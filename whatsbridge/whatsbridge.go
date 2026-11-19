package whatsbridge

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	_ "github.com/mattn/go-sqlite3"
	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/appstate"
	"go.mau.fi/whatsmeow/proto/waCommon"
	"go.mau.fi/whatsmeow/proto/waCompanionReg"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/proto/waMmsRetry"
	"go.mau.fi/whatsmeow/proto/waSyncAction"
	"go.mau.fi/whatsmeow/proto/waWeb"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
	"google.golang.org/protobuf/proto"
)

// NativeBridgeCallback receives ordered JSON events from the Go engine into Kotlin.
type NativeBridgeCallback interface {
	OnJsonEvent(eventType string, payloadJson string)
}

type bridgeEvent struct {
	EventType string
	Payload   string
}

type mediaJob struct {
	MessageID     string
	ChatJID       string
	SenderJID     string
	MediaType     string
	MimeType      string
	DirectPath    string
	MediaKey      []byte
	FileSHA256    []byte
	FileEncSHA256 []byte
	FileLength    uint64
	IsViewOnce    bool
	IsStatus      bool
	IsFromMe      bool
	IsHistorical  bool
	MetaVer       int
	Msg           whatsmeow.DownloadableMessage
}

type versionCache struct {
	Primary   uint32 `json:"primary"`
	Secondary uint32 `json:"secondary"`
	Tertiary  uint32 `json:"tertiary"`
	CheckedAt int64  `json:"checkedAt"`
}

const (
	eventBufferCapacity       = 4096
	mediaQueueCapacity        = 4096
	mediaWorkers              = 3
	avatarQueueCapacity       = 2048
	avatarWorkers             = 3
	avatarMissTTLSec          = 6 * 3600
	avatarUnmappedRetrySec    = 60
	maxDecryptRetries         = 2
	undecryptableGraceDelay   = 12 * time.Second
	autoDownloadVideoMaxBytes = uint64(5 * 1024 * 1024)
	autoDownloadVaultMaxBytes = uint64(16 * 1024 * 1024)
	maxManualMediaBytes       = uint64(512 * 1024 * 1024)
	mediaDownloadTimeout      = 120 * time.Second
)

type cachedPicInfo struct {
	URL string
	ID  string
}

var (
	mu                     sync.Mutex
	client                 *whatsmeow.Client
	container              *sqlstore.Container
	metaDB                 *sql.DB
	baseCtx                context.Context
	baseCancel             context.CancelFunc
	appFilesDir            string
	eventCallback          NativeBridgeCallback
	eventRing              chan bridgeEvent
	mediaQueue             chan mediaJob
	metaWriteQueue         chan mediaJob
	avatarQueue            chan types.JID
	workersOnce            sync.Once
	ghostPresence          atomic.Bool
	contactsSyncRunning    atomic.Bool
	lastContactsSyncUnix   atomic.Int64
	lastKeepAliveResetUnix atomic.Int64
	verifyBulkLastMs       atomic.Int64
	retryCounts            sync.Map
	pendingUndecryptable   sync.Map
	pendingStatusRecovery  sync.Map
	resolvedStatusIDs      sync.Map
	emittedStubIDs         sync.Map
	statusRecoveryMu       sync.Mutex
	statusLastConnectMs    int64
	statusLastFlushMs      int64
	statusLastSentMs       int64
	statusBackoffUntilMs   int64
	statusConsecErrs       int
	statusSentWindowMs     []int64
	seenMediaMap           sync.Map
	mediaJobsById          sync.Map
	mediaInFlight          sync.Map
	historyReqMu           sync.Map
	peerMediaReqMu         sync.Map
	mediaRetrySent         sync.Map
	avatarFetchedMu        sync.Map
	avatarInFlight         sync.Map
	fullAvatarReqMu        sync.Map
	newsletterPicURLs      sync.Map
	deadNewsletters        sync.Map
	disableAutoDlPhoto     atomic.Bool
	disableAutoDlAudio     atomic.Bool
	disableAutoDlVideo     atomic.Bool
	disableAutoDlDoc       atomic.Bool
	rngMu                  sync.Mutex
	rng                    = rand.New(rand.NewSource(time.Now().UnixNano()))
)

type bridgeLogger struct {
	module string
}

func (l *bridgeLogger) emit(level, msg string, args ...any) {
	formatted := fmt.Sprintf(msg, args...)
	fmt.Printf("[%s/%s] %s\n", level, l.module, formatted)
	if level == "ERROR" || level == "WARN" {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  level,
			"module": l.module,
			"msg":    formatted,
		})
	}
}

func (l *bridgeLogger) Errorf(msg string, args ...any) { l.emit("ERROR", msg, args...) }
func (l *bridgeLogger) Warnf(msg string, args ...any)  { l.emit("WARN", msg, args...) }
func (l *bridgeLogger) Infof(msg string, args ...any)  { l.emit("INFO", msg, args...) }
func (l *bridgeLogger) Debugf(_ string, _ ...any)      {}
func (l *bridgeLogger) Sub(mod string) waLog.Logger {
	if l.module == "" {
		return &bridgeLogger{module: mod}
	}
	return &bridgeLogger{module: l.module + "/" + mod}
}

// InitClient initializes wa_protocol.db in WAL mode, refreshes WAVersion (max 1x/24h),
// hardens DeviceProps, starts the 2048-capacity ring buffer and 2-worker media pool.
func InitClient(filesDir string, callback NativeBridgeCallback) error {
	mu.Lock()
	defer mu.Unlock()

	if filesDir == "" {
		return errors.New("filesDir must not be empty")
	}
	if callback == nil {
		return errors.New("callback must not be nil")
	}

	appFilesDir = filesDir
	eventCallback = callback

	if baseCancel != nil {
		baseCancel()
	}
	clearPendingUndecryptable()
	clearPendingStatusRecoveries(true)
	baseCtx, baseCancel = context.WithCancel(context.Background())
	startWorkers()

	for _, sub := range []string{
		filepath.Join(filesDir, "media"),
		filepath.Join(filesDir, "vault", "viewonce"),
		filepath.Join(filesDir, "vault", "status"),
		filepath.Join(filesDir, "vault", "avatars"),
	} {
		if err := os.MkdirAll(sub, 0o755); err != nil {
			return fmt.Errorf("create dir %s: %w", sub, err)
		}
	}

	// Rule 1 & 2: Dynamic WAVersion check (max once every 24h) & Desktop Companion Fingerprint
	refreshWAVersionIfNeeded(filesDir)
	applyDeviceFingerprint()

	// Rule 3: Dual-Database Isolation + SQLite WAL + immediate txlock + 10s busy timeout
	dbPath := filepath.Join(filesDir, "wa_protocol.db")
	dsn := fmt.Sprintf(
		"file:%s?_txlock=immediate&_journal_mode=WAL&_busy_timeout=10000&_synchronous=NORMAL&_foreign_keys=on",
		dbPath,
	)

	rootLog := &bridgeLogger{module: "Main"}

	var err error
	container, err = sqlstore.New(baseCtx, "sqlite3", dsn, rootLog.Sub("DB"))
	if err != nil {
		return fmt.Errorf("open wa_protocol.db: %w", err)
	}

	if err := initMetaDB(dsn); err != nil {
		return fmt.Errorf("init wa_media_meta: %w", err)
	}

	deviceStore, err := container.GetFirstDevice(baseCtx)
	if err != nil {
		return fmt.Errorf("get first device: %w", err)
	}

	cli := whatsmeow.NewClient(deviceStore, rootLog.Sub("Client"))
	// Cap retry receipt parallelism to prevent Bad MAC storms
	cli.SetMaxParallelRetryReceiptHandling(2)
	// Match official WhatsApp Web/Desktop recovery: after 5s with no sender retry,
	// request 1 copy from primary phone via Peer BuildUnavailableMessageRequest
	// (auto-cancelled if sender retry arrives first).
	cli.AutomaticMessageRerequestFromPhone = true
	// Never automatically send active delivery receipts when ghost overrides apply
	cli.SetForceActiveDeliveryReceipts(false)
	cli.EnableAutoReconnect = true
	cli.AutoTrustIdentity = true
	cli.EmitAppStateEventsOnFullSync = true

	client = cli
	cli.AddEventHandler(handleWhatsmeowEvent)

	emitJSON("ENGINE_READY", map[string]any{
		"dbPath":     dbPath,
		"loggedIn":   cli.Store.ID != nil,
		"isLoggedIn": cli.Store.ID != nil,
		"ownJid":     safeJID(cli.Store.ID),
		"ownLid":     cli.Store.LID.String(),
		"waVersion":  store.GetWAVersion().String(),
		"deviceOs":   store.DeviceProps.GetOs(),
		"platform":   store.DeviceProps.GetPlatformType().String(),
		"timestamp":  time.Now().UnixMilli(),
	})

	return nil
}

func startWorkers() {
	workersOnce.Do(func() {
		eventRing = make(chan bridgeEvent, eventBufferCapacity)
		mediaQueue = make(chan mediaJob, mediaQueueCapacity)
		metaWriteQueue = make(chan mediaJob, 8192)
		avatarQueue = make(chan types.JID, avatarQueueCapacity)

		go func() {
			for ev := range eventRing {
				mu.Lock()
				cb := eventCallback
				mu.Unlock()
				if cb != nil {
					cb.OnJsonEvent(ev.EventType, ev.Payload)
				}
			}
		}()

		go metaWriterWorker()

		// Bounded 2-worker media download pool with 150ms-450ms randomized jitter
		for i := 0; i < mediaWorkers; i++ {
			go mediaDownloadWorker()
		}

		// Bounded 2-worker avatar download pool with 200ms jitter
		for i := 0; i < avatarWorkers; i++ {
			go avatarDownloadWorker()
		}
	})
}

func initMetaDB(dsn string) error {
	if metaDB != nil {
		_ = metaDB.Close()
		metaDB = nil
	}
	db, err := sql.Open("sqlite3", dsn)
	if err != nil {
		return err
	}
	db.SetMaxOpenConns(1)
	_, err = db.Exec(`
		CREATE TABLE IF NOT EXISTS wa_media_meta (
			message_id TEXT PRIMARY KEY,
			chat_jid TEXT,
			sender_jid TEXT,
			media_type TEXT,
			mime_type TEXT,
			direct_path TEXT,
			media_key BLOB,
			file_sha256 BLOB,
			file_enc_sha256 BLOB,
			file_length INTEGER,
			is_view_once INTEGER,
			is_status INTEGER,
			is_from_me INTEGER DEFAULT 0,
			is_historical INTEGER DEFAULT 0,
			meta_ver INTEGER DEFAULT 0
		);
	`)
	if err != nil {
		_ = db.Close()
		return err
	}
	_, _ = db.Exec(`ALTER TABLE wa_media_meta ADD COLUMN is_from_me INTEGER DEFAULT 0`)
	_, _ = db.Exec(`ALTER TABLE wa_media_meta ADD COLUMN is_historical INTEGER DEFAULT 0`)
	_, _ = db.Exec(`ALTER TABLE wa_media_meta ADD COLUMN meta_ver INTEGER DEFAULT 0`)
	_, _ = db.Exec(`
		CREATE TABLE IF NOT EXISTS wa_avatar_miss (
			jid TEXT PRIMARY KEY,
			checked_at INTEGER NOT NULL
		);
	`)
	cutoff := time.Now().Unix() - avatarMissTTLSec
	_, _ = db.Exec(`DELETE FROM wa_avatar_miss WHERE checked_at < ?`, cutoff)
	if rows, qErr := db.Query(`SELECT jid, checked_at FROM wa_avatar_miss WHERE checked_at >= ?`, cutoff); qErr == nil {
		for rows.Next() {
			var jidStr string
			var ts int64
			if rows.Scan(&jidStr, &ts) == nil && jidStr != "" {
				avatarFetchedMu.Store(jidStr, ts)
			}
		}
		_ = rows.Close()
	}
	metaDB = db
	return nil
}

func persistAvatarMiss(jids ...string) {
	now := time.Now().Unix()
	for _, j := range jids {
		if j != "" {
			avatarFetchedMu.Store(j, now)
		}
	}
	mu.Lock()
	db := metaDB
	mu.Unlock()
	if db == nil {
		return
	}
	for _, j := range jids {
		if j != "" {
			_, _ = db.Exec(
				`INSERT INTO wa_avatar_miss (jid, checked_at) VALUES (?, ?) ON CONFLICT(jid) DO UPDATE SET checked_at = excluded.checked_at`,
				j, now,
			)
		}
	}
}

func metaWriterWorker() {
	if metaWriteQueue == nil {
		return
	}
	batch := make([]mediaJob, 0, 100)
	flush := func() {
		if len(batch) == 0 {
			return
		}
		mu.Lock()
		db := metaDB
		mu.Unlock()
		if db == nil {
			batch = batch[:0]
			return
		}
		tx, err := db.Begin()
		if err != nil {
			batch = batch[:0]
			return
		}
		stmt, err := tx.Prepare(`
			INSERT INTO wa_media_meta (
				message_id, chat_jid, sender_jid, media_type, mime_type,
				direct_path, media_key, file_sha256, file_enc_sha256, file_length,
				is_view_once, is_status, is_from_me, is_historical, meta_ver
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT(message_id) DO UPDATE SET
				media_type = excluded.media_type,
				mime_type = excluded.mime_type,
				direct_path = excluded.direct_path,
				media_key = excluded.media_key,
				file_sha256 = excluded.file_sha256,
				file_enc_sha256 = excluded.file_enc_sha256,
				file_length = excluded.file_length,
				is_view_once = excluded.is_view_once,
				is_from_me = excluded.is_from_me,
				is_historical = excluded.is_historical,
				meta_ver = excluded.meta_ver
		`)
		if err != nil {
			_ = tx.Rollback()
			batch = batch[:0]
			return
		}
		for _, job := range batch {
			vo := 0
			if job.IsViewOnce {
				vo = 1
			}
			st := 0
			if job.IsStatus {
				st = 1
			}
			fm := 0
			if job.IsFromMe {
				fm = 1
			}
			hist := 0
			if job.IsHistorical {
				hist = 1
			}
			mv := job.MetaVer
			if mv <= 0 {
				mv = 1
			}
			_, _ = stmt.Exec(
				job.MessageID, job.ChatJID, job.SenderJID, job.MediaType, job.MimeType,
				job.DirectPath, job.MediaKey, job.FileSHA256, job.FileEncSHA256, int64(job.FileLength),
				vo, st, fm, hist, mv,
			)
		}
		_ = stmt.Close()
		_ = tx.Commit()
		batch = batch[:0]
	}

	ticker := time.NewTicker(400 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case job := <-metaWriteQueue:
			batch = append(batch, job)
			if len(batch) >= 100 {
				flush()
			}
		case <-ticker.C:
			flush()
		}
	}
}

func saveMediaMeta(job *mediaJob) {
	if job == nil || job.MessageID == "" || job.DirectPath == "" || (len(job.MediaKey) == 0 && !strings.HasSuffix(job.ChatJID, "@newsletter")) {
		return
	}
	mediaJobsById.Store(job.MessageID, *job)
	if metaWriteQueue != nil {
		select {
		case metaWriteQueue <- *job:
		default:
		}
	}
}

func lookupMediaJob(messageId string) (*mediaJob, bool) {
	if messageId == "" {
		return nil, false
	}
	if v, ok := mediaJobsById.Load(messageId); ok {
		j := v.(mediaJob)
		return &j, true
	}
	mu.Lock()
	db := metaDB
	mu.Unlock()
	if db == nil {
		return nil, false
	}
	var j mediaJob
	var fileLen int64
	var vo, st, fm, hist, mv int
	err := db.QueryRow(`
		SELECT message_id, chat_jid, sender_jid, media_type, mime_type,
		       direct_path, media_key, file_sha256, file_enc_sha256, file_length,
		       is_view_once, is_status, COALESCE(is_from_me, 0),
		       COALESCE(is_historical, 0), COALESCE(meta_ver, 0)
		FROM wa_media_meta
		WHERE message_id = ?
	`, messageId).Scan(
		&j.MessageID, &j.ChatJID, &j.SenderJID, &j.MediaType, &j.MimeType,
		&j.DirectPath, &j.MediaKey, &j.FileSHA256, &j.FileEncSHA256, &fileLen,
		&vo, &st, &fm, &hist, &mv,
	)
	if err != nil {
		return nil, false
	}
	j.FileLength = uint64(fileLen)
	j.IsViewOnce = vo != 0
	j.IsStatus = st != 0
	j.IsFromMe = fm != 0
	j.IsHistorical = hist != 0
	j.MetaVer = mv
	mediaJobsById.Store(j.MessageID, j)
	return &j, true
}

func refreshWAVersionIfNeeded(filesDir string) {
	cachePath := filepath.Join(filesDir, "wa_version_cache.json")
	nowSec := time.Now().Unix()
	if raw, err := os.ReadFile(cachePath); err == nil {
		var vc versionCache
		if json.Unmarshal(raw, &vc) == nil && vc.Primary > 0 && nowSec-vc.CheckedAt < 86400 {
			store.SetWAVersion(store.WAVersionContainer{vc.Primary, vc.Secondary, vc.Tertiary})
			return
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	httpCli := &http.Client{Timeout: 5 * time.Second}
	if ver, err := whatsmeow.GetLatestVersion(ctx, httpCli); err == nil && ver != nil && !ver.IsZero() {
		store.SetWAVersion(*ver)
		vc := versionCache{
			Primary:   ver[0],
			Secondary: ver[1],
			Tertiary:  ver[2],
			CheckedAt: nowSec,
		}
		if b, mErr := json.Marshal(vc); mErr == nil {
			_ = os.WriteFile(cachePath, b, 0o644)
		}
	}
}

func applyDeviceFingerprint() {
	store.SetOSInfo("Mac OS", [3]uint32{14, 4, 1})
	store.DeviceProps.Os = proto.String("Mac OS")
	store.DeviceProps.PlatformType = waCompanionReg.DeviceProps_CHROME.Enum()
	store.DeviceProps.RequireFullSync = proto.Bool(true)
	if store.DeviceProps.HistorySyncConfig != nil {
		store.DeviceProps.HistorySyncConfig.SupportCallLogHistory = proto.Bool(true)
	}
}

func ConnectClient() error {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	if cli.IsConnected() {
		return nil
	}
	return cli.Connect()
}

func DisconnectClient() {
	clearPendingUndecryptable()
	clearPendingStatusRecoveries(false)
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli != nil {
		cli.Disconnect()
	}
}

func ResetSocket() {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli != nil {
		cli.ResetConnection()
	}
}

func PairWithPhoneNumber(phone string) (string, error) {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return "", errors.New("client not initialized")
	}
	if !cli.IsConnected() {
		if err := cli.Connect(); err != nil {
			return "", fmt.Errorf("connect before pairing: %w", err)
		}
	}
	code, err := cli.PairPhone(ctx, phone, true, whatsmeow.PairClientChrome, "Chrome (Mac OS)")
	if err != nil {
		return "", err
	}
	emitJSON("PAIR_CODE", map[string]any{
		"code":      code,
		"phone":     phone,
		"timestamp": time.Now().UnixMilli(),
	})
	return code, nil
}

func IsConnected() bool {
	mu.Lock()
	cli := client
	mu.Unlock()
	return cli != nil && cli.IsConnected()
}

func IsLoggedIn() bool {
	mu.Lock()
	cli := client
	mu.Unlock()
	return cli != nil && cli.Store != nil && cli.Store.ID != nil
}

func ensureConnectedAndAuthenticated(cli *whatsmeow.Client, maxWait time.Duration) bool {
	if cli == nil || cli.Store == nil || cli.Store.ID == nil {
		return false
	}
	if cli.IsLoggedIn() {
		return true
	}
	if !cli.IsConnected() {
		_ = cli.Connect()
	}
	deadline := time.Now().Add(maxWait)
	for time.Now().Before(deadline) {
		if cli.IsLoggedIn() {
			return true
		}
		time.Sleep(150 * time.Millisecond)
	}
	return cli.IsLoggedIn()
}

func GetOwnJID() string {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || cli.Store == nil {
		return ""
	}
	return safeJID(cli.Store.ID)
}

func SetGhostPresence(enabled bool) {
	ghostPresence.Store(enabled)
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() || !cli.IsLoggedIn() {
		return
	}
	if enabled {
		_ = cli.SendPresence(ctx, types.PresenceUnavailable)
	} else {
		_ = cli.SendPresence(ctx, types.PresenceAvailable)
	}
}

// unfreezePresenceForActivity unfreezes presence (sends PresenceAvailable) immediately before
// an outbound activity (message, media, reaction, status) when Ghost Mode is active, preventing
// server-side heuristics from detecting outbound traffic from an offline client.
func unfreezePresenceForActivity() {
	if !ghostPresence.Load() {
		return
	}
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli != nil && cli.IsConnected() && cli.IsLoggedIn() {
		_ = cli.SendPresence(ctx, types.PresenceAvailable)
	}
}

// scheduleRefreezePresence automatically re-freezes presence back to PresenceUnavailable
// after a natural 1.5-second settle window following an outbound action.
func scheduleRefreezePresence() {
	if !ghostPresence.Load() {
		return
	}
	go func() {
		time.Sleep(1500 * time.Millisecond)
		if ghostPresence.Load() {
			mu.Lock()
			curCli := client
			curCtx := baseCtx
			mu.Unlock()
			if curCli != nil && curCli.IsConnected() && curCli.IsLoggedIn() {
				_ = curCli.SendPresence(curCtx, types.PresenceUnavailable)
			}
		}
	}()
}

func SendTextMessage(
	chatJid string,
	text string,
	quotedId string,
	quotedSender string,
	quotedBody string,
	mentionsCsv string,
	ghostTyping bool,
) (string, error) {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return "", errors.New("client not initialized")
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", fmt.Errorf("invalid chat JID: %w", err)
	}

	// Rule 5: Outbound message pacing (35ms/char capped at 2500ms, +/- 20% jitter)
	if !ghostTyping && cli.IsConnected() {
		_ = cli.SendChatPresence(ctx, jid, types.ChatPresenceComposing, types.ChatPresenceMediaText)
		baseMs := len([]rune(text)) * 35
		if baseMs > 2500 {
			baseMs = 2500
		}
		if baseMs < 250 {
			baseMs = 250
		}
		jitterFactor := 0.8 + randomFloat()*0.4
		time.Sleep(time.Duration(float64(baseMs)*jitterFactor) * time.Millisecond)
		_ = cli.SendChatPresence(ctx, jid, types.ChatPresencePaused, types.ChatPresenceMediaText)
	}

	var mentions []string
	if strings.TrimSpace(mentionsCsv) != "" {
		for _, part := range strings.Split(mentionsCsv, ",") {
			p := strings.TrimSpace(part)
			if p != "" {
				mentions = append(mentions, p)
			}
		}
	}

	var msg *waE2E.Message
	if quotedId != "" || len(mentions) > 0 {
		ctxInfo := &waE2E.ContextInfo{}
		if quotedId != "" {
			ctxInfo.StanzaID = proto.String(quotedId)
			if quotedSender != "" {
				ctxInfo.Participant = proto.String(quotedSender)
			}
			if quotedBody != "" {
				ctxInfo.QuotedMessage = &waE2E.Message{
					Conversation: proto.String(quotedBody),
				}
			}
		}
		if len(mentions) > 0 {
			ctxInfo.MentionedJID = mentions
		}
		msg = &waE2E.Message{
			ExtendedTextMessage: &waE2E.ExtendedTextMessage{
				Text:        proto.String(text),
				ContextInfo: ctxInfo,
			},
		}
	} else {
		msg = &waE2E.Message{
			Conversation: proto.String(text),
		}
	}

	resp, err := cli.SendMessage(ctx, jid, msg)
	if err != nil {
		return "", err
	}
	return resp.ID, nil
}

func EditMessage(chatJid string, messageId string, newText string) error {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return err
	}
	content := &waE2E.Message{
		Conversation: proto.String(newText),
	}
	editMsg := cli.BuildEdit(jid, messageId, content)
	_, err = cli.SendMessage(ctx, jid, editMsg)
	return err
}

func RevokeMessage(chatJid string, messageId string) error {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return err
	}
	_, err = cli.RevokeMessage(ctx, jid, messageId)
	return err
}

type outboundUploadSpec struct {
	mediaType     whatsmeow.MediaType
	isViewOnce    bool
	isSticker     bool
	isGifPlayback bool
}

func resolveOutboundUploadSpec(mimeType string, qualityMode string, isVoiceNote bool) outboundUploadSpec {
	rawMode := strings.ToLower(strings.TrimSpace(qualityMode))
	isViewOnce := strings.Contains(rawMode, "viewonce") || strings.Contains(rawMode, "view_once")
	modeLower := strings.TrimSpace(strings.TrimSuffix(strings.TrimSuffix(rawMode, ":viewonce"), "_viewonce"))
	mimeLower := strings.ToLower(strings.TrimSpace(mimeType))
	isSticker := modeLower == "sticker" || strings.Contains(rawMode, "sticker")
	isGifPlayback := !isVoiceNote && !isSticker && (modeLower == "gif" || strings.Contains(rawMode, "gif"))
	if isGifPlayback {
		isViewOnce = false
	}

	var waMediaType whatsmeow.MediaType
	switch {
	case isVoiceNote:
		waMediaType = whatsmeow.MediaAudio
	case isSticker:
		waMediaType = whatsmeow.MediaImage
	case isGifPlayback:
		waMediaType = whatsmeow.MediaVideo
	case !isViewOnce && modeLower == "original" && !strings.HasPrefix(mimeLower, "video/"):
		waMediaType = whatsmeow.MediaDocument
	case strings.HasPrefix(mimeLower, "audio/"):
		waMediaType = whatsmeow.MediaAudio
	case strings.HasPrefix(mimeLower, "video/"):
		waMediaType = whatsmeow.MediaVideo
	case strings.HasPrefix(mimeLower, "image/"):
		waMediaType = whatsmeow.MediaImage
	default:
		waMediaType = whatsmeow.MediaDocument
	}

	return outboundUploadSpec{
		mediaType:     waMediaType,
		isViewOnce:    isViewOnce,
		isSticker:     isSticker,
		isGifPlayback: isGifPlayback,
	}
}

func buildOutboundVideoMessage(
	up whatsmeow.UploadResponse,
	mimeType string,
	caption string,
	durationSec int,
	isViewOnce bool,
	isGifPlayback bool,
) *waE2E.Message {
	sec := uint32(maxInt(durationSec, 0))
	videoMime := strings.TrimSpace(mimeType)
	if isGifPlayback && (videoMime == "" || !strings.HasPrefix(strings.ToLower(videoMime), "video/")) {
		videoMime = "video/mp4"
	}
	effectiveViewOnce := isViewOnce && !isGifPlayback
	videoMsg := &waE2E.VideoMessage{
		Caption:       proto.String(caption),
		Mimetype:      proto.String(videoMime),
		Seconds:       &sec,
		URL:           &up.URL,
		DirectPath:    &up.DirectPath,
		MediaKey:      up.MediaKey,
		FileEncSHA256: up.FileEncSHA256,
		FileSHA256:    up.FileSHA256,
		FileLength:    &up.FileLength,
		ViewOnce:      proto.Bool(effectiveViewOnce),
	}
	if isGifPlayback {
		videoMsg.GifPlayback = proto.Bool(true)
		videoMsg.GifAttribution = waE2E.VideoMessage_NONE.Enum()
	}
	msg := &waE2E.Message{
		VideoMessage: videoMsg,
	}
	if effectiveViewOnce {
		msg = &waE2E.Message{
			ViewOnceMessageV2: &waE2E.FutureProofMessage{
				Message: msg,
			},
		}
	}
	return msg
}

func buildOutboundAudioMessage(
	up whatsmeow.UploadResponse,
	mimeType string,
	isVoiceNote bool,
	durationSec int,
	waveformCsv string,
	nowUnixSec int64,
) *waE2E.Message {
	sec := uint32(maxInt(durationSec, 1))
	mimeLower := strings.ToLower(strings.TrimSpace(mimeType))
	audioMime := strings.TrimSpace(mimeType)
	var wf []byte
	if isVoiceNote {
		if strings.Contains(mimeLower, "ogg") || strings.Contains(mimeLower, "opus") || mimeLower == "" {
			audioMime = "audio/ogg; codecs=opus"
		}
		wf = parseWaveformCsv(waveformCsv)
	} else if audioMime == "" || strings.Contains(mimeLower, "m4a") {
		audioMime = "audio/mp4"
	}
	audioMsg := &waE2E.AudioMessage{
		Mimetype:          proto.String(audioMime),
		Seconds:           &sec,
		PTT:               proto.Bool(isVoiceNote),
		URL:               &up.URL,
		DirectPath:        &up.DirectPath,
		MediaKey:          up.MediaKey,
		FileEncSHA256:     up.FileEncSHA256,
		FileSHA256:        up.FileSHA256,
		FileLength:        &up.FileLength,
		MediaKeyTimestamp: proto.Int64(nowUnixSec),
	}
	if len(wf) > 0 {
		audioMsg.Waveform = wf
	}
	return &waE2E.Message{
		AudioMessage: audioMsg,
	}
}

func SendGifMessage(
	chatJid string,
	filePath string,
	caption string,
	durationSec int,
) (string, error) {
	return SendMediaMessage(chatJid, filePath, "video/mp4", caption, "gif", false, durationSec, "")
}

func SendMediaMessage(
	chatJid string,
	filePath string,
	mimeType string,
	caption string,
	qualityMode string,
	isVoiceNote bool,
	durationSec int,
	waveformCsv string,
) (string, error) {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return "", errors.New("client not initialized")
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return "", err
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return "", fmt.Errorf("read media file: %w", err)
	}

	spec := resolveOutboundUploadSpec(mimeType, qualityMode, isVoiceNote)
	waMediaType := spec.mediaType
	isViewOnce := spec.isViewOnce
	isSticker := spec.isSticker
	isGifPlayback := spec.isGifPlayback

	up, err := cli.Upload(ctx, data, waMediaType)
	if err != nil {
		return "", fmt.Errorf("upload media: %w", err)
	}

	var msg *waE2E.Message
	fileName := filepath.Base(filePath)

	if isSticker {
		msg = &waE2E.Message{
			StickerMessage: &waE2E.StickerMessage{
				URL:           &up.URL,
				DirectPath:    &up.DirectPath,
				MediaKey:      up.MediaKey,
				FileEncSHA256: up.FileEncSHA256,
				FileSHA256:    up.FileSHA256,
				FileLength:    &up.FileLength,
				Mimetype:      proto.String("image/webp"),
			},
		}
	} else {
		switch waMediaType {
		case whatsmeow.MediaImage:
			msg = &waE2E.Message{
				ImageMessage: &waE2E.ImageMessage{
					Caption:       proto.String(caption),
					Mimetype:      proto.String(mimeType),
					URL:           &up.URL,
					DirectPath:    &up.DirectPath,
					MediaKey:      up.MediaKey,
					FileEncSHA256: up.FileEncSHA256,
					FileSHA256:    up.FileSHA256,
					FileLength:    &up.FileLength,
					ViewOnce:      proto.Bool(isViewOnce),
				},
			}
			if isViewOnce {
				msg = &waE2E.Message{
					ViewOnceMessageV2: &waE2E.FutureProofMessage{
						Message: msg,
					},
				}
			}
		case whatsmeow.MediaVideo:
			msg = buildOutboundVideoMessage(up, mimeType, caption, durationSec, isViewOnce, isGifPlayback)
		case whatsmeow.MediaAudio:
			msg = buildOutboundAudioMessage(up, mimeType, isVoiceNote, durationSec, waveformCsv, time.Now().Unix())
		default:
			msg = &waE2E.Message{
				DocumentMessage: &waE2E.DocumentMessage{
					FileName:      proto.String(fileName),
					Title:         proto.String(fileName),
					Caption:       proto.String(caption),
					Mimetype:      proto.String(mimeType),
					URL:           &up.URL,
					DirectPath:    &up.DirectPath,
					MediaKey:      up.MediaKey,
					FileEncSHA256: up.FileEncSHA256,
					FileSHA256:    up.FileSHA256,
					FileLength:    &up.FileLength,
				},
			}
		}
	}

	resp, err := cli.SendMessage(ctx, jid, msg)
	if err != nil {
		return "", err
	}
	return resp.ID, nil
}

func resolveReceiptSender(chat types.JID, senderJid string) types.JID {
	if (chat.Server == types.GroupServer || chat.Server == types.BroadcastServer) && senderJid != "" && senderJid != chat.String() {
		if parsed, err := types.ParseJID(senderJid); err == nil {
			return parsed
		}
	}
	return types.EmptyJID
}

func resolveAlternateChatJID(ctx context.Context, cli *whatsmeow.Client, chat types.JID) types.JID {
	if cli == nil || cli.Store == nil || cli.Store.LIDs == nil {
		return types.EmptyJID
	}
	switch chat.Server {
	case types.HiddenUserServer:
		if pn, err := cli.Store.LIDs.GetPNForLID(ctx, chat); err == nil && !pn.IsEmpty() {
			return pn
		}
	case types.DefaultUserServer:
		if lid, err := cli.Store.LIDs.GetLIDForPN(ctx, chat); err == nil && !lid.IsEmpty() {
			return lid
		}
	}
	return types.EmptyJID
}

func learnLIDMappingFromMessageInfo(ctx context.Context, cli *whatsmeow.Client, info *types.MessageInfo) {
	if cli == nil || cli.Store == nil || cli.Store.LIDs == nil || info == nil {
		return
	}
	if ctx == nil {
		ctx = context.Background()
	}
	storePair := func(a, b types.JID) {
		a = a.ToNonAD()
		b = b.ToNonAD()
		if a.IsEmpty() || b.IsEmpty() {
			return
		}
		var lid, pn types.JID
		switch {
		case a.Server == types.HiddenUserServer && b.Server == types.DefaultUserServer:
			lid, pn = a, b
		case b.Server == types.HiddenUserServer && a.Server == types.DefaultUserServer:
			lid, pn = b, a
		default:
			return
		}
		_ = cli.Store.LIDs.PutLIDMapping(ctx, lid, pn)
	}
	storePair(info.Sender, info.SenderAlt)
	if !info.IsGroup {
		storePair(info.Chat, info.RecipientAlt)
		if !info.IsFromMe {
			storePair(info.Chat, info.SenderAlt)
		}
	}
}

func canonicalizeDMChatJID(ctx context.Context, cli *whatsmeow.Client, raw types.JID) (types.JID, types.JID) {
	if raw.IsEmpty() {
		return types.EmptyJID, types.EmptyJID
	}
	if raw == types.StatusBroadcastJID ||
		raw.Server == types.GroupServer ||
		raw.Server == types.NewsletterServer ||
		raw.Server == types.BroadcastServer {
		return raw, types.EmptyJID
	}
	if ctx == nil {
		ctx = context.Background()
	}
	clean := raw.ToNonAD()
	alt := resolveAlternateChatJID(ctx, cli, clean).ToNonAD()
	if clean.Server == types.HiddenUserServer && alt.Server == types.DefaultUserServer && !alt.IsEmpty() {
		return alt, clean
	}
	return clean, alt
}

func formatWebMsgReactions(reactions []*waWeb.Reaction) string {
	if len(reactions) == 0 {
		return ""
	}
	seen := make(map[string]bool, len(reactions))
	ordered := make([]string, 0, len(reactions))
	for _, r := range reactions {
		if r == nil {
			continue
		}
		txt := strings.TrimSpace(r.GetText())
		if txt == "" || seen[txt] {
			continue
		}
		seen[txt] = true
		ordered = append(ordered, txt)
	}
	return strings.Join(ordered, " ")
}

func resolveBestContactName(ctx context.Context, cli *whatsmeow.Client, jid types.JID) (string, bool, types.JID) {
	if cli == nil || cli.Store == nil || cli.Store.Contacts == nil || jid.IsEmpty() {
		return "", false, types.EmptyJID
	}
	alt := resolveAlternateChatJID(ctx, cli, jid)
	var c1, c2 types.ContactInfo
	c1, _ = cli.Store.Contacts.GetContact(ctx, jid)
	if !alt.IsEmpty() {
		c2, _ = cli.Store.Contacts.GetContact(ctx, alt)
	}
	for _, s := range []string{c1.FullName, c2.FullName, c1.FirstName, c2.FirstName} {
		if strings.TrimSpace(s) != "" {
			return strings.TrimSpace(s), true, alt
		}
	}
	for _, s := range []string{c1.BusinessName, c2.BusinessName, c1.PushName, c2.PushName} {
		if strings.TrimSpace(s) != "" {
			return strings.TrimSpace(s), false, alt
		}
	}
	if jid.Server == types.HiddenUserServer && alt.Server == types.DefaultUserServer && alt.User != "" {
		return "+" + alt.User, false, alt
	}
	if jid.Server == types.DefaultUserServer && jid.User != "" {
		return "+" + jid.User, false, alt
	}
	return "", false, alt
}

type participantMeta struct {
	JID        string `json:"jid"`
	AltJID     string `json:"altJid"`
	SavedName  string `json:"savedName"`
	PushName   string `json:"pushName"`
	Phone      string `json:"phone"`
	AvatarPath string `json:"avatarPath"`
	IsSelf     bool   `json:"isSelf"`
}

func resolveLocalParticipantMeta(ctx context.Context, cli *whatsmeow.Client, filesDir string, jid types.JID) participantMeta {
	meta := participantMeta{JID: jid.String()}
	if cli == nil || cli.Store == nil || jid.IsEmpty() {
		return meta
	}
	nonAd := jid.ToNonAD()
	alt := resolveAlternateChatJID(ctx, cli, nonAd)
	if alt.IsEmpty() {
		if a, err := cli.Store.GetAltJID(ctx, nonAd); err == nil && !a.IsEmpty() {
			alt = a.ToNonAD()
		}
	}
	meta.AltJID = alt.ToNonAD().String()

	if cli.Store.ID != nil {
		ownNonAd := cli.Store.ID.ToNonAD()
		if (nonAd.User == ownNonAd.User && nonAd.Server == ownNonAd.Server) ||
			(!alt.IsEmpty() && alt.User == ownNonAd.User && alt.Server == ownNonAd.Server) {
			meta.IsSelf = true
		}
	}
	if !meta.IsSelf && !cli.Store.LID.IsEmpty() {
		ownLid := cli.Store.LID.ToNonAD()
		if (nonAd.User == ownLid.User && nonAd.Server == ownLid.Server) ||
			(!alt.IsEmpty() && alt.User == ownLid.User && alt.Server == ownLid.Server) {
			meta.IsSelf = true
		}
	}

	if nonAd.Server == types.DefaultUserServer && nonAd.User != "" {
		meta.Phone = "+" + nonAd.User
	} else if alt.Server == types.DefaultUserServer && alt.User != "" {
		meta.Phone = "+" + alt.User
	}

	if cli.Store.Contacts != nil {
		c1, _ := cli.Store.Contacts.GetContact(ctx, nonAd)
		var c2 types.ContactInfo
		if !alt.IsEmpty() {
			c2, _ = cli.Store.Contacts.GetContact(ctx, alt)
		}
		for _, s := range []string{c1.FullName, c2.FullName, c1.FirstName, c2.FirstName} {
			if t := strings.TrimSpace(s); t != "" {
				meta.SavedName = t
				break
			}
		}
		for _, s := range []string{c1.BusinessName, c2.BusinessName, c1.PushName, c2.PushName} {
			if t := strings.TrimSpace(s); t != "" {
				meta.PushName = t
				break
			}
		}
	}

	if filesDir != "" {
		if p := findCachedAvatar(filesDir, nonAd.User); p != "" {
			meta.AvatarPath = p
		} else if !alt.IsEmpty() {
			if p := findCachedAvatar(filesDir, alt.User); p != "" {
				meta.AvatarPath = p
			}
		}
	}
	return meta
}

// ResolveParticipantNamesJson resolves JID display metadata (@lid -> PN, saved/push names, local cached avatars)
// strictly from the local SQLite store and disk cache with zero network requests.
func ResolveParticipantNamesJson(jidsCsv string) string {
	mu.Lock()
	cli := client
	ctx := baseCtx
	filesDir := appFilesDir
	mu.Unlock()
	if ctx == nil {
		ctx = context.Background()
	}
	out := make(map[string]participantMeta)
	if cli == nil || cli.Store == nil {
		b, _ := json.Marshal(out)
		return string(b)
	}
	for _, raw := range strings.Split(jidsCsv, ",") {
		key := strings.TrimSpace(raw)
		if key == "" {
			continue
		}
		if _, exists := out[key]; exists {
			continue
		}
		parsed, err := types.ParseJID(key)
		if err != nil || parsed.IsEmpty() {
			continue
		}
		out[key] = resolveLocalParticipantMeta(ctx, cli, filesDir, parsed)
	}
	b, err := json.Marshal(out)
	if err != nil {
		return "{}"
	}
	return string(b)
}

func enqueueAvatarFetch(jid types.JID) {
	if jid.IsEmpty() || jid == types.StatusBroadcastJID || jid.Server == types.BroadcastServer {
		return
	}
	now := time.Now().Unix()
	if prev, ok := avatarFetchedMu.Load(jid.String()); ok && now-prev.(int64) < avatarMissTTLSec {
		return
	}
	if _, loaded := avatarInFlight.LoadOrStore(jid.String(), true); loaded {
		return
	}
	if avatarQueue == nil {
		avatarInFlight.Delete(jid.String())
		return
	}
	select {
	case avatarQueue <- jid:
	default:
		avatarInFlight.Delete(jid.String())
	}
}

func findCachedAvatar(filesDir, user string) string {
	if filesDir == "" || user == "" {
		return ""
	}
	dir := filepath.Join(filesDir, "vault", "avatars")
	prefix := sanitizeFileName(user + "_")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return ""
	}
	fallback := ""
	for _, e := range entries {
		if !e.IsDir() && strings.HasPrefix(e.Name(), prefix) {
			info, infoErr := e.Info()
			if infoErr == nil && info.Size() > 0 {
				fullPath := filepath.Join(dir, e.Name())
				if strings.HasSuffix(e.Name(), "_hd.jpg") {
					return fullPath
				}
				if fallback == "" {
					fallback = fullPath
				}
			}
		}
	}
	return fallback
}

func newsletterPictureInfoURL(meta *types.NewsletterMetadata) (string, string) {
	if meta == nil {
		return "", ""
	}
	var pic *types.ProfilePictureInfo
	if meta.ThreadMeta.Picture != nil && (meta.ThreadMeta.Picture.URL != "" || meta.ThreadMeta.Picture.DirectPath != "") {
		pic = meta.ThreadMeta.Picture
	} else if meta.ThreadMeta.Preview.URL != "" || meta.ThreadMeta.Preview.DirectPath != "" {
		p := meta.ThreadMeta.Preview
		pic = &p
	}
	if pic == nil {
		return "", ""
	}
	if pic.URL != "" {
		return pic.URL, pic.ID
	}
	dp := strings.TrimSpace(pic.DirectPath)
	if dp == "" {
		return "", ""
	}
	if !strings.HasPrefix(dp, "/") {
		dp = "/" + dp
	}
	return "https://pps.whatsapp.net" + dp, pic.ID
}

func isAvatarNotFoundErr(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, whatsmeow.ErrProfilePictureNotSet) || errors.Is(err, whatsmeow.ErrProfilePictureUnauthorized) {
		return true
	}
	lower := strings.ToLower(err.Error())
	return strings.Contains(lower, "item-not-found") || strings.Contains(lower, "not-authorized")
}

func resolveAvatarURL(ctx context.Context, cli *whatsmeow.Client, jid, alt types.JID) (picURL, picID string, notFound bool) {
	if jid.Server == types.NewsletterServer {
		if cached, ok := newsletterPicURLs.Load(jid.String()); ok {
			if info, ok2 := cached.(cachedPicInfo); ok2 && info.URL != "" {
				return info.URL, info.ID, false
			}
		}
		if meta, nErr := cli.GetNewsletterInfo(ctx, jid); nErr == nil && meta != nil {
			if u, id := newsletterPictureInfoURL(meta); u != "" {
				newsletterPicURLs.Store(jid.String(), cachedPicInfo{URL: u, ID: id})
				return u, id, false
			}
		}
		return "", "", false
	}

	queryTarget := func(target types.JID, isCommunity bool) (string, string, bool) {
		infoFull, errFull := cli.GetProfilePictureInfo(ctx, target, &whatsmeow.GetProfilePictureParams{
			Preview:     false,
			IsCommunity: isCommunity,
		})
		if errFull == nil && infoFull != nil && infoFull.URL != "" {
			return infoFull.URL, infoFull.ID, false
		}
		if errors.Is(errFull, whatsmeow.ErrProfilePictureUnauthorized) || errors.Is(errFull, whatsmeow.ErrProfilePictureNotSet) {
			return "", "", true
		}
		info, err := cli.GetProfilePictureInfo(ctx, target, &whatsmeow.GetProfilePictureParams{
			Preview:     true,
			IsCommunity: isCommunity,
		})
		if err == nil && info != nil && info.URL != "" {
			return info.URL, info.ID, false
		}
		return "", "", isAvatarNotFoundErr(err)
	}

	if u, id, nf := queryTarget(jid, false); u != "" {
		return u, id, false
	} else {
		notFound = nf
	}

	if jid.Server == types.GroupServer {
		if u, id, nf := queryTarget(jid, true); u != "" {
			return u, id, false
		} else if nf {
			notFound = true
		}
	}

	if !alt.IsEmpty() && (alt.User != jid.User || alt.Server != jid.Server) {
		if u, id, nf := queryTarget(alt, false); u != "" {
			return u, id, false
		} else if nf {
			notFound = true
		} else {
			notFound = false
		}
	}

	return "", "", notFound
}

func setWhatsAppCDNHeaders(req *http.Request) {
	req.Header.Set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
	req.Header.Set("Origin", "https://web.whatsapp.com")
	req.Header.Set("Referer", "https://web.whatsapp.com/")
}

func processAvatarJob(jid types.JID, httpCli *http.Client) {
	defer avatarInFlight.Delete(jid.String())

	mu.Lock()
	cli := client
	ctx := baseCtx
	filesDir := appFilesDir
	mu.Unlock()
	if cli == nil || filesDir == "" {
		return
	}

	alt := resolveAlternateChatJID(ctx, cli, jid)
	if cached := findCachedAvatar(filesDir, jid.User); cached != "" {
		avatarFetchedMu.Store(jid.String(), time.Now().Unix())
		if !alt.IsEmpty() {
			avatarFetchedMu.Store(alt.String(), time.Now().Unix())
		}
		emitJSON("PROFILE_UPDATE", map[string]any{
			"jid":        jid.String(),
			"altJid":     alt.String(),
			"avatarPath": cached,
			"removed":    false,
			"timestamp":  time.Now().UnixMilli(),
		})
		return
	}
	if !alt.IsEmpty() {
		if cachedAlt := findCachedAvatar(filesDir, alt.User); cachedAlt != "" {
			avatarFetchedMu.Store(jid.String(), time.Now().Unix())
			avatarFetchedMu.Store(alt.String(), time.Now().Unix())
			emitJSON("PROFILE_UPDATE", map[string]any{
				"jid":        jid.String(),
				"altJid":     alt.String(),
				"avatarPath": cachedAlt,
				"removed":    false,
				"timestamp":  time.Now().UnixMilli(),
			})
			return
		}
	}
	if !ensureConnectedAndAuthenticated(cli, 6*time.Second) {
		return
	}

	picCtx, cancel := context.WithTimeout(ctx, 12*time.Second)
	picURL, picID, notFound := resolveAvatarURL(picCtx, cli, jid, alt)
	cancel()
	if picURL == "" {
		if notFound {
			// If this is an @lid without a known PN yet, only pause briefly in memory so PN mapping can resolve later.
			if jid.Server == types.HiddenUserServer && alt.IsEmpty() {
				avatarFetchedMu.Store(jid.String(), time.Now().Unix()-avatarMissTTLSec+avatarUnmappedRetrySec)
			} else {
				persistAvatarMiss(jid.String(), alt.String())
			}
		}
		time.Sleep(time.Duration(70+int(randomFloat()*80)) * time.Millisecond)
		return
	}
	if picID == "" {
		picID = "pic"
	}

	b, statusCode, readErr := downloadAvatarBytes(ctx, httpCli, picURL)
	if (statusCode == http.StatusNotFound || statusCode == http.StatusGone) && len(b) == 0 {
		if jid.Server == types.HiddenUserServer && alt.IsEmpty() {
			avatarFetchedMu.Store(jid.String(), time.Now().Unix()-avatarMissTTLSec+avatarUnmappedRetrySec)
		} else {
			persistAvatarMiss(jid.String(), alt.String())
		}
	} else if statusCode == http.StatusOK && readErr == nil && len(b) > 0 {
		saveAvatarAndEmit(filesDir, jid, alt, picID, b)
	}
	time.Sleep(time.Duration(75+int(randomFloat()*85)) * time.Millisecond)
}

func downloadAvatarBytes(ctx context.Context, httpCli *http.Client, picURL string) ([]byte, int, error) {
	req, reqErr := http.NewRequestWithContext(ctx, http.MethodGet, picURL, nil)
	if reqErr != nil {
		return nil, 0, reqErr
	}
	setWhatsAppCDNHeaders(req)
	resp, httpErr := httpCli.Do(req)
	if httpErr != nil {
		return nil, 0, httpErr
	}
	defer resp.Body.Close()
	b, readErr := io.ReadAll(resp.Body)
	return b, resp.StatusCode, readErr
}

func saveAvatarAndEmit(filesDir string, jid, alt types.JID, picID string, b []byte) string {
	if filesDir == "" || len(b) == 0 {
		return ""
	}
	if picID == "" {
		picID = "pic"
	}
	fname := sanitizeFileName(jid.User + "_" + picID + "_hd.jpg")
	target := filepath.Join(filesDir, "vault", "avatars", fname)
	if err := os.WriteFile(target, b, 0o644); err != nil {
		return ""
	}
	now := time.Now().Unix()
	avatarFetchedMu.Store(jid.String(), now)
	if !alt.IsEmpty() {
		avatarFetchedMu.Store(alt.String(), now)
	}
	emitJSON("PROFILE_UPDATE", map[string]any{
		"jid":        jid.String(),
		"altJid":     alt.String(),
		"pictureId":  picID,
		"avatarPath": target,
		"removed":    false,
		"timestamp":  time.Now().UnixMilli(),
	})
	return target
}

func FetchFullProfilePicture(jidStr string) {
	raw := strings.TrimSpace(jidStr)
	if raw == "" {
		return
	}
	jid, err := types.ParseJID(raw)
	if err != nil || jid.User == "" {
		return
	}
	now := time.Now().Unix()
	if prev, ok := fullAvatarReqMu.Load(jid.String()); ok {
		if ts, ok2 := prev.(int64); ok2 && now-ts < 60 {
			return
		}
	}
	fullAvatarReqMu.Store(jid.String(), now)

	go func() {
		mu.Lock()
		cli := client
		ctx := baseCtx
		filesDir := appFilesDir
		mu.Unlock()
		if cli == nil || filesDir == "" {
			return
		}
		alt := resolveAlternateChatJID(ctx, cli, jid)
		if cached := findCachedAvatar(filesDir, jid.User); strings.HasSuffix(cached, "_hd.jpg") {
			emitJSON("PROFILE_UPDATE", map[string]any{
				"jid":        jid.String(),
				"altJid":     alt.String(),
				"avatarPath": cached,
				"removed":    false,
				"timestamp":  time.Now().UnixMilli(),
			})
			return
		}
		if !alt.IsEmpty() {
			if cachedAlt := findCachedAvatar(filesDir, alt.User); strings.HasSuffix(cachedAlt, "_hd.jpg") {
				emitJSON("PROFILE_UPDATE", map[string]any{
					"jid":        jid.String(),
					"altJid":     alt.String(),
					"avatarPath": cachedAlt,
					"removed":    false,
					"timestamp":  time.Now().UnixMilli(),
				})
				return
			}
		}
		if !ensureConnectedAndAuthenticated(cli, 6*time.Second) {
			return
		}
		picCtx, cancel := context.WithTimeout(ctx, 12*time.Second)
		picURL, picID, _ := resolveAvatarURL(picCtx, cli, jid, alt)
		cancel()
		if picURL == "" {
			return
		}
		httpCli := &http.Client{Timeout: 12 * time.Second}
		b, status, readErr := downloadAvatarBytes(ctx, httpCli, picURL)
		if status == http.StatusOK && readErr == nil && len(b) > 0 {
			saveAvatarAndEmit(filesDir, jid, alt, picID, b)
		}
	}()
}

func avatarDownloadWorker() {
	if avatarQueue == nil {
		return
	}
	httpCli := &http.Client{Timeout: 12 * time.Second}
	for jid := range avatarQueue {
		processAvatarJob(jid, httpCli)
	}
}

func emitStoredChatSettingsIfPresent(ctx context.Context, cli *whatsmeow.Client, rawJID types.JID) {
	if cli == nil || cli.Store == nil || cli.Store.ChatSettings == nil || rawJID.IsEmpty() {
		return
	}
	canon, alt := canonicalizeDMChatJID(ctx, cli, rawJID)
	if canon.IsEmpty() {
		canon = rawJID
	}
	settings, err := cli.Store.ChatSettings.GetChatSettings(ctx, rawJID)
	if (err != nil || !settings.Found) && !alt.IsEmpty() {
		settings, err = cli.Store.ChatSettings.GetChatSettings(ctx, alt)
	}
	if (err != nil || !settings.Found) && canon != rawJID {
		settings, err = cli.Store.ChatSettings.GetChatSettings(ctx, canon)
	}
	if err != nil || !settings.Found {
		return
	}
	now := time.Now()
	muted := settings.MutedUntil.After(now)
	var muteEndMs int64
	if muted {
		muteEndMs = settings.MutedUntil.UnixMilli()
		if muteEndMs <= 0 {
			muteEndMs = -1
		}
	}
	emitJSON("CHAT_META_UPDATE", map[string]any{
		"kind":    "MUTE",
		"jid":     canon.String(),
		"altJid":  alt.String(),
		"muted":   muted,
		"muteEnd": muteEndMs,
	})
	if settings.Pinned {
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "PIN",
			"jid":       canon.String(),
			"altJid":    alt.String(),
			"pinned":    true,
			"timestamp": now.UnixMilli(),
		})
	}
	if settings.Archived {
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":     "ARCHIVE",
			"jid":      canon.String(),
			"altJid":   alt.String(),
			"archived": true,
		})
	}
}

func SyncContactsAndAvatars(jidsCsv string) {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !ensureConnectedAndAuthenticated(cli, 8*time.Second) {
		return
	}

	// Targeted lookup for specific chat/status JIDs: never trigger global Group/Newsletter/Contacts sweeps.
	if strings.TrimSpace(jidsCsv) != "" {
		resolved := make([]map[string]any, 0, 32)
		for _, raw := range strings.Split(jidsCsv, ",") {
			raw = strings.TrimSpace(raw)
			if raw == "" {
				continue
			}
			parsed, err := types.ParseJID(raw)
			if err != nil || parsed.IsEmpty() {
				continue
			}
			enqueueAvatarFetch(parsed)
			emitStoredChatSettingsIfPresent(ctx, cli, parsed)
			if parsed.Server != types.GroupServer && parsed.Server != types.NewsletterServer {
				name, isCustom, alt := resolveBestContactName(ctx, cli, parsed)
				if name != "" || !alt.IsEmpty() {
					resolved = append(resolved, map[string]any{
						"jid":      parsed.String(),
						"altJid":   alt.String(),
						"name":     name,
						"isCustom": isCustom,
					})
				}
			}
		}
		if len(resolved) > 0 {
			emitJSON("CONTACTS_SYNC_BATCH", map[string]any{
				"contacts": resolved,
			})
		}
		return
	}

	nowSec := time.Now().Unix()
	prev := lastContactsSyncUnix.Load()
	if prev > 0 && nowSec-prev < 90 {
		return
	}
	if !contactsSyncRunning.CompareAndSwap(false, true) {
		return
	}
	defer contactsSyncRunning.Store(false)
	lastContactsSyncUnix.Store(nowSec)

	// 1. Fetch all subscribed channels (newsletters) & cache their picture URLs immediately
	nCtx, nCancel := context.WithTimeout(ctx, 20*time.Second)
	newsletters, nErr := cli.GetSubscribedNewsletters(nCtx)
	nCancel()
	if nErr == nil && len(newsletters) > 0 {
		nlBatch := make([]map[string]any, 0, len(newsletters))
		for _, meta := range newsletters {
			if meta == nil || meta.ID.IsEmpty() {
				continue
			}
			if u, id := newsletterPictureInfoURL(meta); u != "" {
				newsletterPicURLs.Store(meta.ID.String(), cachedPicInfo{URL: u, ID: id})
			}
			isMuted := meta.ViewerMeta != nil && meta.ViewerMeta.Mute == types.NewsletterMuteOn
			nlBatch = append(nlBatch, map[string]any{
				"jid":         meta.ID.String(),
				"name":        strings.TrimSpace(meta.ThreadMeta.Name.Text),
				"description": strings.TrimSpace(meta.ThreadMeta.Description.Text),
				"isMuted":     isMuted,
			})
			enqueueAvatarFetch(meta.ID)
		}
		if len(nlBatch) > 0 {
			emitJSON("NEWSLETTERS_SYNC_BATCH", map[string]any{
				"channels": nlBatch,
			})
			nlCopy := append([]*types.NewsletterMetadata(nil), newsletters...)
			go func() {
				for _, meta := range nlCopy {
					if meta == nil || meta.ID.IsEmpty() {
						continue
					}
					if _, isDead := deadNewsletters.Load(meta.ID.String()); isDead {
						continue
					}
					emitNewsletterMessages(cli, meta.ID, 20)
					time.Sleep(time.Duration(200+int(randomFloat()*140)) * time.Millisecond)
				}
			}()
		}
	} else if nErr != nil {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "NewsletterSync",
			"msg":    fmt.Sprintf("GetSubscribedNewsletters error: %v", nErr),
		})
	}

	// 2. Fetch all joined groups & communities from server
	gCtx, gCancel := context.WithTimeout(ctx, 15*time.Second)
	joined, gErr := cli.GetJoinedGroups(gCtx)
	gCancel()
	if gErr == nil && len(joined) > 0 {
		groups := make([]map[string]any, 0, len(joined))
		for _, g := range joined {
			if g == nil {
				continue
			}
			parentStr := ""
			if !g.LinkedParentJID.IsEmpty() {
				parentStr = g.LinkedParentJID.String()
			}
			groups = append(groups, map[string]any{
				"jid":               g.JID.String(),
				"name":              g.Name,
				"topic":             g.Topic,
				"isCommunityParent": g.IsParent,
				"parentGroupJid":    parentStr,
				"isDefaultSubgroup": g.IsDefaultSubGroup,
				"onlyAdminsPost":    g.IsAnnounce && !isOwnGroupAdmin(cli, g),
			})
			enqueueAvatarFetch(g.JID)
			emitStoredChatSettingsIfPresent(ctx, cli, g.JID)
		}
		if len(groups) > 0 {
			emitJSON("GROUPS_SYNC_BATCH", map[string]any{
				"groups": groups,
			})
		}
	} else if gErr != nil {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "GroupSync",
			"msg":    fmt.Sprintf("GetJoinedGroups error: %v", gErr),
		})
	}

	// 3. Emit saved contacts / pushnames from sqlstore.Contacts and queue background avatars
	if cli.Store != nil && cli.Store.Contacts != nil {
		if allContacts, err := cli.Store.Contacts.GetAllContacts(ctx); err == nil {
			batch := make([]map[string]any, 0, 200)
			flushContacts := func() {
				if len(batch) == 0 {
					return
				}
				emitJSON("CONTACTS_SYNC_BATCH", map[string]any{
					"contacts": batch,
				})
				batch = batch[:0]
			}
			for cJid, info := range allContacts {
				alt := resolveAlternateChatJID(ctx, cli, cJid)
				name := strings.TrimSpace(info.FullName)
				isCustom := name != ""
				if name == "" {
					name = strings.TrimSpace(info.FirstName)
					isCustom = name != ""
				}
				if name == "" {
					name = strings.TrimSpace(info.BusinessName)
				}
				if name == "" {
					name = strings.TrimSpace(info.PushName)
				}
				if name == "" {
					continue
				}
				enqueueAvatarFetch(cJid)
				emitStoredChatSettingsIfPresent(ctx, cli, cJid)
				batch = append(batch, map[string]any{
					"jid":      cJid.String(),
					"altJid":   alt.String(),
					"name":     name,
					"isCustom": isCustom,
				})
				if len(batch) >= 200 {
					flushContacts()
				}
			}
			flushContacts()
		}
	}
}

// MarkReadManual marks messages as read on WhatsApp (grouped by sender in groups, empty sender in DMs)
// and syncs chat-read state across companion/primary devices.
func MarkReadManual(chatJid string, senderJid string, messageIdsCsv string) error {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	if cli.Store == nil || cli.Store.ID == nil {
		return errors.New("not paired")
	}
	chat, err := types.ParseJID(chatJid)
	if err != nil {
		return err
	}
	sender := resolveReceiptSender(chat, senderJid)
	var ids []types.MessageID
	for _, raw := range strings.Split(messageIdsCsv, ",") {
		trimmed := strings.TrimSpace(raw)
		if trimmed != "" {
			ids = append(ids, trimmed)
			if len(ids) >= 25 {
				break
			}
		}
	}
	if len(ids) == 0 {
		return nil
	}
	go func(c, s types.JID, msgIDs []types.MessageID) {
		if !ensureConnectedAndAuthenticated(cli, 6*time.Second) {
			emitJSON("BRIDGE_LOG", map[string]any{
				"level":  "WARN",
				"module": "MarkRead",
				"msg":    fmt.Sprintf("aborted chat=%s: not authenticated after wait", c.String()),
			})
			return
		}
		altChat := resolveAlternateChatJID(ctx, cli, c)
		markErr := cli.MarkRead(ctx, msgIDs, time.Now(), c, s)
		var altMarkErr error
		if !altChat.IsEmpty() && (c.Server == types.HiddenUserServer || c.Server == types.DefaultUserServer) {
			altMarkErr = cli.MarkRead(ctx, msgIDs, time.Now(), altChat, types.EmptyJID)
		}
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "INFO",
			"module": "MarkRead",
			"msg": fmt.Sprintf("chat=%s alt=%s sender=%s count=%d err=%v altErr=%v",
				c.String(), altChat.String(), s.String(), len(msgIDs), markErr, altMarkErr),
		})
		if c.Server == types.DefaultUserServer || c.Server == types.HiddenUserServer || c.Server == types.GroupServer {
			appErr := cli.SendAppState(ctx, appstate.BuildMarkChatAsRead(c, true, time.Now(), nil))
			var altAppErr error
			if !altChat.IsEmpty() {
				altAppErr = cli.SendAppState(ctx, appstate.BuildMarkChatAsRead(altChat, true, time.Now(), nil))
			}
			emitJSON("BRIDGE_LOG", map[string]any{
				"level":  "INFO",
				"module": "MarkChatAsRead",
				"msg":    fmt.Sprintf("chat=%s err=%v alt=%s altErr=%v", c.String(), appErr, altChat.String(), altAppErr),
			})
		}
	}(chat, sender, ids)
	return nil
}

func SendPlayedReceiptManual(chatJid string, senderJid string, messageId string) error {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	if cli.Store == nil || cli.Store.ID == nil {
		return errors.New("not paired")
	}
	chat, err := types.ParseJID(chatJid)
	if err != nil {
		return err
	}
	sender := resolveReceiptSender(chat, senderJid)
	go func() {
		if !ensureConnectedAndAuthenticated(cli, 6*time.Second) {
			return
		}
		err := cli.MarkRead(ctx, []types.MessageID{messageId}, time.Now(), chat, sender, types.ReceiptTypePlayed)
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "INFO",
			"module": "PlayedReceipt",
			"msg":    fmt.Sprintf("chat=%s id=%s err=%v", chat.String(), messageId, err),
		})
	}()
	return nil
}

// RequestMediaDownload downloads media from cached/persisted keys or asks the primary phone to resend it.
func HasCachedMediaMeta(messageId string) bool {
	_, ok := lookupMediaJob(messageId)
	return ok
}

func emitMediaStatus(messageId, chatJid, status, reason string) {
	if messageId == "" {
		return
	}
	emitJSON("MEDIA_DOWNLOAD_STATUS", map[string]any{
		"messageId": messageId,
		"chatJid":   chatJid,
		"status":    status,
		"reason":    reason,
	})
}

func RequestMediaDownload(chatJid string, senderJid string, messageId string, timestampMs int64) error {
	mu.Lock()
	cli := client
	ctx := baseCtx
	filesDir := appFilesDir
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	if messageId == "" {
		return nil
	}

	if job, ok := lookupMediaJob(messageId); ok {
		if job.IsViewOnce {
			emitViewOnceExpired(job, "Open in original WhatsApp")
			return nil
		}
		if _, busy := mediaInFlight.Load(messageId); busy {
			emitMediaStatus(messageId, job.ChatJID, "DOWNLOADING", "")
			return nil
		}
		emitMediaStatus(messageId, job.ChatJID, "DOWNLOADING", "")
		go func(j *mediaJob) {
			dlCtx, cancel := context.WithTimeout(ctx, mediaDownloadTimeout)
			defer cancel()
			okDl := downloadAndEmitMedia(cli, dlCtx, filesDir, j)
			emitJSON("BRIDGE_LOG", map[string]any{
				"level":  "INFO",
				"module": "MediaReq",
				"msg":    fmt.Sprintf("cached download id=%s ok=%v", messageId, okDl),
			})
		}(job)
		return nil
	}

	if cli.Store == nil || cli.Store.ID == nil {
		emitMediaStatus(messageId, chatJid, "FAILED", "not paired")
		return errors.New("not paired")
	}

	now := time.Now().UnixMilli()
	if prev, ok := peerMediaReqMu.Load(messageId); ok && now-prev.(int64) < 10_000 {
		return nil
	}
	peerMediaReqMu.Store(messageId, now)

	chat, err := types.ParseJID(chatJid)
	if err != nil {
		emitMediaStatus(messageId, chatJid, "FAILED", err.Error())
		return err
	}
	var sender types.JID
	if senderJid != "" {
		sender, _ = types.ParseJID(senderJid)
	}
	altChat := resolveAlternateChatJID(ctx, cli, chat)
	histKey := chatJid + ":" + messageId
	shouldSendHistory := false
	if prevChatReq, ok := historyReqMu.Load(histKey); !ok || now-prevChatReq.(int64) >= 15_000 {
		historyReqMu.Store(histKey, now)
		shouldSendHistory = true
	}

	emitMediaStatus(messageId, chatJid, "DOWNLOADING", "")
	go func() {
		if !ensureConnectedAndAuthenticated(cli, 6*time.Second) {
			emitMediaStatus(messageId, chatJid, "FAILED", "not authenticated")
			emitJSON("BRIDGE_LOG", map[string]any{
				"level":  "WARN",
				"module": "MediaReq",
				"msg":    fmt.Sprintf("aborted id=%s: not authenticated after wait", messageId),
			})
			return
		}
		isFromMe := !sender.IsEmpty() && cli.Store.ID != nil &&
			(sender.User == cli.Store.ID.User || sender.User == cli.Store.LID.User)
		targets := []types.JID{chat}
		if !altChat.IsEmpty() {
			targets = append(targets, altChat)
		}
		ts := time.UnixMilli(timestampMs)
		if timestampMs <= 0 {
			ts = time.Now()
		}

		for _, tChat := range targets {
			var tSender types.JID
			switch {
			case tChat.Server == types.GroupServer || tChat.Server == types.BroadcastServer:
				tSender = sender
			case !isFromMe:
				tSender = tChat.ToNonAD()
			}
			uResp, uErr := cli.SendMessage(ctx, cli.Store.ID.ToNonAD(), cli.BuildUnavailableMessageRequest(tChat, tSender, messageId), whatsmeow.SendRequestExtra{Peer: true})
			var hResp whatsmeow.SendResponse
			var hErr error
			if shouldSendHistory && tChat.Server != types.NewsletterServer {
				info := &types.MessageInfo{
					MessageSource: types.MessageSource{
						Chat:     tChat,
						Sender:   tSender,
						IsFromMe: isFromMe,
						IsGroup:  tChat.Server == types.GroupServer,
					},
					ID:        messageId,
					Timestamp: ts,
				}
				hResp, hErr = cli.SendMessage(ctx, cli.Store.ID.ToNonAD(), cli.BuildHistorySyncRequest(info, 50), whatsmeow.SendRequestExtra{Peer: true})
			}
			emitJSON("BRIDGE_LOG", map[string]any{
				"level":  "INFO",
				"module": "MediaReq",
				"msg": fmt.Sprintf("peer req chat=%s sender=%s id=%s unavailId=%s unavailErr=%v histId=%s histErr=%v",
					tChat.String(), tSender.String(), messageId, uResp.ID, uErr, hResp.ID, hErr),
			})
			if uErr == nil && (!shouldSendHistory || tChat.Server == types.NewsletterServer || hErr == nil) {
				break
			}
		}
	}()
	return nil
}

func SendChatPresenceManual(chatJid string, state string, media string, blockedByGhost bool) error {
	if blockedByGhost {
		return nil
	}
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return nil
	}
	jid, err := types.ParseJID(chatJid)
	if err != nil {
		return err
	}
	cpState := types.ChatPresencePaused
	if state == "composing" {
		cpState = types.ChatPresenceComposing
	}
	cpMedia := types.ChatPresenceMediaText
	if media == "audio" {
		cpMedia = types.ChatPresenceMediaAudio
	}
	return cli.SendChatPresence(ctx, jid, cpState, cpMedia)
}

func SubscribeContactPresence(jidStr string) error {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	jid, err := types.ParseJID(jidStr)
	if err != nil {
		return err
	}
	return cli.SubscribePresence(ctx, jid)
}

func resolveGroupParticipantJID(ctx context.Context, cli *whatsmeow.Client, p types.GroupParticipant) types.JID {
	primary := p.JID.ToNonAD()
	pn := p.PhoneNumber.ToNonAD()
	lid := p.LID.ToNonAD()
	if pn.IsEmpty() && primary.Server == types.DefaultUserServer {
		pn = primary
	}
	if lid.IsEmpty() && primary.Server == types.HiddenUserServer {
		lid = primary
	}
	if cli != nil && cli.Store != nil && cli.Store.LIDs != nil && !lid.IsEmpty() && !pn.IsEmpty() {
		if ctx == nil {
			ctx = context.Background()
		}
		_ = cli.Store.LIDs.PutLIDMapping(ctx, lid, pn)
	}
	if !pn.IsEmpty() {
		return pn
	}
	if !primary.IsEmpty() && cli != nil {
		canon, _ := canonicalizeDMChatJID(ctx, cli, primary)
		if !canon.IsEmpty() {
			return canon
		}
	}
	return primary
}

func GetGroupParticipantsJson(groupJid string) (string, error) {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "[]", errors.New("client not connected")
	}
	jid, err := types.ParseJID(groupJid)
	if err != nil {
		return "[]", err
	}
	info, err := cli.GetGroupInfo(ctx, jid)
	if err != nil {
		return "[]", err
	}
	jids := make([]string, 0, len(info.Participants))
	seen := make(map[string]bool, len(info.Participants))
	for _, p := range info.Participants {
		resolved := resolveGroupParticipantJID(ctx, cli, p)
		if resolved.IsEmpty() {
			continue
		}
		s := resolved.String()
		if !seen[s] {
			seen[s] = true
			jids = append(jids, s)
		}
	}
	b, _ := json.Marshal(jids)
	return string(b), nil
}

func GetGroupInfoJson(groupJid string) (string, error) {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "{}", errors.New("client not connected")
	}
	jid, err := types.ParseJID(groupJid)
	if err != nil {
		return "{}", err
	}
	info, err := cli.GetGroupInfo(ctx, jid)
	if err != nil {
		return "{}", err
	}
	type participantDto struct {
		JID          string `json:"jid"`
		IsAdmin      bool   `json:"isAdmin"`
		IsSuperAdmin bool   `json:"isSuperAdmin"`
		DisplayName  string `json:"displayName"`
	}
	type groupInfoDto struct {
		JID          string           `json:"jid"`
		Name         string           `json:"name"`
		Topic        string           `json:"topic"`
		OwnerJID     string           `json:"ownerJid"`
		IsLocked     bool             `json:"isLocked"`
		IsAnnounce   bool             `json:"isAnnounce"`
		Participants []participantDto `json:"participants"`
	}
	dto := groupInfoDto{
		JID:        info.JID.String(),
		Name:       info.GroupName.Name,
		Topic:      info.Topic,
		OwnerJID:   info.OwnerJID.String(),
		IsLocked:   info.IsLocked,
		IsAnnounce: info.IsAnnounce,
	}
	for _, p := range info.Participants {
		resolved := resolveGroupParticipantJID(ctx, cli, p)
		jStr := resolved.String()
		if jStr == "" {
			jStr = p.JID.ToNonAD().String()
		}
		dto.Participants = append(dto.Participants, participantDto{
			JID:          jStr,
			IsAdmin:      p.IsAdmin,
			IsSuperAdmin: p.IsSuperAdmin,
			DisplayName:  p.DisplayName,
		})
	}
	b, _ := json.Marshal(dto)
	return string(b), nil
}

func VerifyNumbersOnWhatsApp(phonesCsv string) (string, error) {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "[]", errors.New("client not connected")
	}
	nowMs := time.Now().UnixMilli()
	prevMs := verifyBulkLastMs.Load()
	if prevMs > 0 && nowMs-prevMs < 2000 {
		return "[]", errors.New("please wait 2 seconds between number verification batches")
	}
	var phones []string
	for _, p := range strings.Split(phonesCsv, ",") {
		p = strings.TrimSpace(p)
		if p != "" {
			phones = append(phones, p)
			if len(phones) >= 25 {
				break
			}
		}
	}
	if len(phones) == 0 {
		return "[]", nil
	}
	verifyBulkLastMs.Store(nowMs)
	res, err := cli.IsOnWhatsApp(ctx, phones)
	if err != nil {
		return "[]", err
	}
	type outRow struct {
		Query string `json:"query"`
		JID   string `json:"jid"`
		IsIn  bool   `json:"isIn"`
	}
	rows := make([]outRow, 0, len(res))
	for _, r := range res {
		rows = append(rows, outRow{
			Query: r.Query,
			JID:   r.JID.String(),
			IsIn:  r.IsIn,
		})
	}
	b, _ := json.Marshal(rows)
	return string(b), nil
}

// Recursive unwrapper handling Ephemeral, ViewOnceV1/V2/Ext, DeviceSent, DocumentWithCaption, FutureProof, and Edited containers.
func hasInnerViewOnceFlag(m *waE2E.Message) bool {
	if m == nil {
		return false
	}
	if im := m.GetImageMessage(); im != nil && im.GetViewOnce() {
		return true
	}
	if vm := m.GetVideoMessage(); vm != nil && vm.GetViewOnce() {
		return true
	}
	if am := m.GetAudioMessage(); am != nil && am.GetViewOnce() {
		return true
	}
	return false
}

func isNonZeroBytes(b []byte) bool {
	for _, v := range b {
		if v != 0 {
			return true
		}
	}
	return false
}

func isMediaNotFoundOrExpired(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, whatsmeow.ErrMediaDownloadFailedWith404) ||
		errors.Is(err, whatsmeow.ErrMediaDownloadFailedWith410) {
		return true
	}
	lower := strings.ToLower(err.Error())
	return strings.Contains(lower, "404") ||
		strings.Contains(lower, "410") ||
		strings.Contains(lower, "not found") ||
		strings.Contains(lower, "gone")
}

func emitViewOnceExpired(job *mediaJob, reason string) {
	if job == nil || job.MessageID == "" {
		return
	}
	emitJSON("VIEW_ONCE_EXPIRED", map[string]any{
		"messageId": job.MessageID,
		"chatJid":   job.ChatJID,
		"mediaType": job.MediaType,
		"isFromMe":  job.IsFromMe,
		"reason":    reason,
	})
}

func requestPeerPlaceholderResendForJob(cli *whatsmeow.Client, ctx context.Context, job *mediaJob) {
	if cli == nil || cli.Store == nil || cli.Store.ID == nil || job == nil || job.MessageID == "" || job.ChatJID == "" {
		return
	}
	now := time.Now().UnixMilli()
	if prev, ok := peerMediaReqMu.Load(job.MessageID); ok && now-prev.(int64) < 15_000 {
		return
	}
	peerMediaReqMu.Store(job.MessageID, now)

	chat, err := types.ParseJID(job.ChatJID)
	if err != nil || chat.IsEmpty() || chat.Server == types.NewsletterServer {
		return
	}
	var sender types.JID
	if job.SenderJID != "" {
		sender, _ = types.ParseJID(job.SenderJID)
	}
	isFromMe := job.IsFromMe
	if !isFromMe && !sender.IsEmpty() &&
		(sender.User == cli.Store.ID.User || (!cli.Store.LID.IsEmpty() && sender.User == cli.Store.LID.User)) {
		isFromMe = true
	}
	var tSender types.JID
	switch {
	case chat.Server == types.GroupServer || chat.Server == types.BroadcastServer:
		tSender = sender
	case !isFromMe:
		tSender = chat.ToNonAD()
	}
	reqCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	_, uErr := cli.SendMessage(reqCtx, cli.Store.ID.ToNonAD(), cli.BuildUnavailableMessageRequest(chat, tSender, job.MessageID), whatsmeow.SendRequestExtra{Peer: true})
	emitJSON("BRIDGE_LOG", map[string]any{
		"level":  "INFO",
		"module": "MediaReq",
		"msg":    fmt.Sprintf("legacy placeholder resend id=%s chat=%s fromMe=%v err=%v", job.MessageID, chat.String(), isFromMe, uErr),
	})
}

func unwrapMessageContainer(m *waE2E.Message) (
	inner *waE2E.Message,
	isViewOnce bool,
	isEphemeral bool,
	ephemeralSec uint32,
	isEdit bool,
) {
	curr := m
	for curr != nil {
		switch {
		case curr.GetEphemeralMessage() != nil:
			isEphemeral = true
			if ctx := curr.GetEphemeralMessage().GetMessage(); ctx != nil {
				curr = ctx
			} else {
				if hasInnerViewOnceFlag(curr) {
					isViewOnce = true
				}
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetViewOnceMessage() != nil:
			isViewOnce = true
			curr = curr.GetViewOnceMessage().GetMessage()
		case curr.GetViewOnceMessageV2() != nil:
			isViewOnce = true
			curr = curr.GetViewOnceMessageV2().GetMessage()
		case curr.GetViewOnceMessageV2Extension() != nil:
			isViewOnce = true
			curr = curr.GetViewOnceMessageV2Extension().GetMessage()
		case curr.GetDeviceSentMessage() != nil:
			curr = curr.GetDeviceSentMessage().GetMessage()
		case curr.GetDocumentWithCaptionMessage() != nil:
			curr = curr.GetDocumentWithCaptionMessage().GetMessage()
		case curr.GetEditedMessage() != nil:
			isEdit = true
			curr = curr.GetEditedMessage().GetMessage()
		case curr.GetAssociatedChildMessage() != nil:
			curr = curr.GetAssociatedChildMessage().GetMessage()
		case curr.GetGroupMentionedMessage() != nil:
			curr = curr.GetGroupMentionedMessage().GetMessage()
		case curr.GetBotInvokeMessage() != nil:
			curr = curr.GetBotInvokeMessage().GetMessage()
		case curr.GetLottieStickerMessage() != nil:
			if innerMsg := curr.GetLottieStickerMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetSpoilerMessage() != nil:
			if innerMsg := curr.GetSpoilerMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetLimitSharingMessage() != nil:
			if innerMsg := curr.GetLimitSharingMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetBotForwardedMessage() != nil:
			if innerMsg := curr.GetBotForwardedMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetStatusMentionMessage() != nil:
			if innerMsg := curr.GetStatusMentionMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetGroupStatusMentionMessage() != nil:
			if innerMsg := curr.GetGroupStatusMentionMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetStatusAddYours() != nil:
			if innerMsg := curr.GetStatusAddYours().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetGroupStatusMessage() != nil:
			if innerMsg := curr.GetGroupStatusMessage().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		case curr.GetGroupStatusMessageV2() != nil:
			if innerMsg := curr.GetGroupStatusMessageV2().GetMessage(); innerMsg != nil {
				curr = innerMsg
			} else {
				return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
			}
		default:
			ephemeralSec = extractEphemeralDuration(curr)
			if ephemeralSec > 0 {
				isEphemeral = true
			}
			if hasInnerViewOnceFlag(curr) {
				isViewOnce = true
			}
			return curr, isViewOnce, isEphemeral, ephemeralSec, isEdit
		}
	}
	return nil, isViewOnce, isEphemeral, ephemeralSec, isEdit
}

func extractEphemeralDuration(m *waE2E.Message) uint32 {
	if m == nil {
		return 0
	}
	switch {
	case m.GetExtendedTextMessage().GetContextInfo() != nil:
		return m.GetExtendedTextMessage().GetContextInfo().GetExpiration()
	case m.GetImageMessage().GetContextInfo() != nil:
		return m.GetImageMessage().GetContextInfo().GetExpiration()
	case m.GetVideoMessage().GetContextInfo() != nil:
		return m.GetVideoMessage().GetContextInfo().GetExpiration()
	case m.GetAudioMessage().GetContextInfo() != nil:
		return m.GetAudioMessage().GetContextInfo().GetExpiration()
	case m.GetDocumentMessage().GetContextInfo() != nil:
		return m.GetDocumentMessage().GetContextInfo().GetExpiration()
	case m.GetStickerMessage().GetContextInfo() != nil:
		return m.GetStickerMessage().GetContextInfo().GetExpiration()
	}
	return 0
}

func handleWhatsmeowEvent(rawEvt any) {
	switch evt := rawEvt.(type) {
	case *events.QR:
		if len(evt.Codes) > 0 {
			emitJSON("QR", map[string]any{
				"code":      evt.Codes[0],
				"timestamp": time.Now().UnixMilli(),
			})
		}

	case *events.PairSuccess:
		emitJSON("CONNECTION_STATE", map[string]any{
			"state":     "PAIRED",
			"ownJid":    evt.ID.String(),
			"ownLid":    evt.LID.String(),
			"platform":  evt.Platform,
			"timestamp": time.Now().UnixMilli(),
		})

	case *events.Connected:
		statusRecoveryMu.Lock()
		statusLastConnectMs = time.Now().UnixMilli()
		statusRecoveryMu.Unlock()
		mu.Lock()
		cli := client
		ctx := baseCtx
		mu.Unlock()
		own := ""
		if cli != nil {
			own = safeJID(cli.Store.ID)
			if ghostPresence.Load() {
				_ = cli.SendPresence(ctx, types.PresenceUnavailable)
			}
		}
		emitJSON("CONNECTION_STATE", map[string]any{
			"state":     "CONNECTED",
			"ownJid":    own,
			"timestamp": time.Now().UnixMilli(),
		})
		go func() {
			time.Sleep(1200 * time.Millisecond)
			SyncContactsAndAvatars("")
			time.Sleep(statusReconnectSettleDelay)
			flushPendingStatusRecoveries()
		}()

	case *events.Disconnected:
		emitJSON("CONNECTION_STATE", map[string]any{
			"state":     "DISCONNECTED",
			"timestamp": time.Now().UnixMilli(),
		})

	case *events.KeepAliveTimeout:
		emitJSON("CONNECTION_STATE", map[string]any{
			"state":     "RECONNECTING",
			"timestamp": time.Now().UnixMilli(),
		})
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "KeepAlive",
			"msg":    fmt.Sprintf("KeepAliveTimeout errorCount=%d", evt.ErrorCount),
		})
		if evt.ErrorCount >= 2 {
			nowSec := time.Now().Unix()
			prevReset := lastKeepAliveResetUnix.Load()
			if nowSec-prevReset >= 60 && lastKeepAliveResetUnix.CompareAndSwap(prevReset, nowSec) {
				mu.Lock()
				kaCli := client
				mu.Unlock()
				if kaCli != nil {
					go kaCli.ResetConnection()
				}
			}
		}

	case *events.LoggedOut:
		emitJSON("CONNECTION_STATE", map[string]any{
			"state":     "LOGGED_OUT",
			"reason":    evt.Reason.String(),
			"timestamp": time.Now().UnixMilli(),
		})

	case *events.HistorySync:
		processHistorySync(evt)

	case *events.Message:
		processIncomingMessage(evt)

	case *events.UndecryptableMessage:
		processUndecryptable(evt)

	case *events.Receipt:
		if evt.Type == types.ReceiptTypeRetry || evt.Type == types.ReceiptTypeServerError || evt.Type == types.ReceiptTypeInactive || evt.Type == types.ReceiptTypePeerMsg || evt.Type == types.ReceiptTypeHistorySync {
			return
		}
		rt := "DELIVERED"
		switch evt.Type {
		case types.ReceiptTypeRead, types.ReceiptTypeReadSelf:
			rt = "READ"
		case types.ReceiptTypePlayed, types.ReceiptTypePlayedSelf:
			rt = "PLAYED"
		}
		mu.Lock()
		rCli := client
		rCtx := baseCtx
		mu.Unlock()
		canonChat, altChat := canonicalizeDMChatJID(rCtx, rCli, evt.Chat)
		chatStr := canonChat.String()
		if chatStr == "" {
			chatStr = normalizeUserJID(rCli, evt.Chat.String())
		}
		if chatStr == "" {
			chatStr = evt.Chat.String()
		}
		altChatStr := altChat.String()
		canonSender, _ := canonicalizeDMChatJID(rCtx, rCli, evt.Sender)
		senderStr := canonSender.String()
		if senderStr == "" {
			senderStr = normalizeUserJID(rCli, evt.Sender.String())
		}
		if senderStr == "" {
			senderStr = evt.Sender.String()
		}
		ids := make([]string, 0, len(evt.MessageIDs))
		for _, id := range evt.MessageIDs {
			ids = append(ids, string(id))
		}
		emitJSON("RECEIPT", map[string]any{
			"chatJid":         chatStr,
			"altChatJid":      altChatStr,
			"senderJid":       senderStr,
			"messageIds":      ids,
			"receiptType":     rt,
			"isFromMe":        evt.IsFromMe,
			"isPrimaryActive": evt.Type == types.ReceiptTypeReadSelf || evt.Type == types.ReceiptTypePlayedSelf || evt.IsFromMe,
			"timestamp":       evt.Timestamp.UnixMilli(),
		})

	case *events.GroupInfo:
		if evt != nil && !evt.JID.IsEmpty() {
			mu.Lock()
			gCli := client
			mu.Unlock()
			if evt.Announce != nil && gCli != nil {
				go func(gJID types.JID) {
					ctx, cancel := context.WithTimeout(baseCtx, 10*time.Second)
					info, err := gCli.GetGroupInfo(ctx, gJID)
					cancel()
					if err == nil && info != nil {
						emitJSON("CHAT_META_UPDATE", map[string]any{
							"kind":   "GROUP_ANNOUNCE",
							"jid":    gJID.String(),
							"locked": info.IsAnnounce && !isOwnGroupAdmin(gCli, info),
						})
					}
				}(evt.JID)
			}
			tsMs := evt.Timestamp.UnixMilli()
			if tsMs <= 0 {
				tsMs = time.Now().UnixMilli()
			}
			var sysText string
			switch {
			case evt.Announce != nil && evt.Announce.IsAnnounce:
				sysText = "Changed group settings to allow only admins to send messages"
			case evt.Announce != nil && !evt.Announce.IsAnnounce:
				sysText = "Changed group settings to allow all members to send messages"
			case evt.Name != nil && evt.Name.Name != "":
				sysText = "Changed the group name to \"" + evt.Name.Name + "\""
			case evt.Topic != nil:
				sysText = "Updated the group description"
			}
			if sysText != "" {
				senderStr := ""
				if evt.SenderPN != nil && !evt.SenderPN.IsEmpty() {
					senderStr = evt.SenderPN.ToNonAD().String()
				} else if evt.Sender != nil && !evt.Sender.IsEmpty() {
					senderStr = evt.Sender.ToNonAD().String()
				}
				emitJSON("MESSAGE_UPSERT", map[string]any{
					"id":                fmt.Sprintf("sys_%s_%d", evt.JID.User, tsMs),
					"chatJid":           evt.JID.String(),
					"senderJid":         senderStr,
					"senderName":        "",
					"isFromMe":          false,
					"isGroup":           true,
					"timestamp":         tsMs,
					"body":              sysText,
					"msgType":           "SYSTEM",
					"mimeType":          "",
					"durationSec":       0,
					"fileSize":          0,
					"deliveryStatus":    "READ",
					"isViewOnce":        false,
					"viewOnceState":     "NONE",
					"isEphemeral":       false,
					"ephemeralDuration": 0,
					"quotedId":          "",
					"quotedSender":      "",
					"quotedBody":        "",
					"mentionsCsv":       "",
					"reactionSummary":   "",
				})
			}
		}

	case *events.Presence:
		mu.Lock()
		prCli := client
		prCtx := baseCtx
		mu.Unlock()
		canonFrom, altFrom := canonicalizeDMChatJID(prCtx, prCli, evt.From)
		jidStr := canonFrom.String()
		if jidStr == "" {
			jidStr = evt.From.String()
		}
		emitJSON("PRESENCE", map[string]any{
			"jid":       jidStr,
			"altJid":    altFrom.String(),
			"isOnline":  !evt.Unavailable,
			"lastSeen":  evt.LastSeen.UnixMilli(),
			"timestamp": time.Now().UnixMilli(),
		})

	case *events.ChatPresence:
		mu.Lock()
		cpCli := client
		cpCtx := baseCtx
		mu.Unlock()
		canonCPChat, altCPChat := canonicalizeDMChatJID(cpCtx, cpCli, evt.Chat)
		cpChatStr := canonCPChat.String()
		if cpChatStr == "" {
			cpChatStr = evt.Chat.String()
		}
		canonCPSender, _ := canonicalizeDMChatJID(cpCtx, cpCli, evt.Sender)
		cpSenderStr := canonCPSender.String()
		if cpSenderStr == "" {
			cpSenderStr = evt.Sender.String()
		}
		emitJSON("CHAT_PRESENCE", map[string]any{
			"chatJid":    cpChatStr,
			"altChatJid": altCPChat.String(),
			"senderJid":  cpSenderStr,
			"state":      string(evt.State),
			"media":      string(evt.Media),
			"timestamp":  time.Now().UnixMilli(),
		})

	case *events.Contact:
		name := ""
		if evt.Action != nil {
			name = evt.Action.GetFullName()
			if name == "" {
				name = evt.Action.GetFirstName()
			}
		}
		mu.Lock()
		cCli := client
		cCtx := baseCtx
		mu.Unlock()
		alt := resolveAlternateChatJID(cCtx, cCli, evt.JID)
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "CONTACT",
			"jid":       evt.JID.String(),
			"altJid":    alt.String(),
			"name":      name,
			"timestamp": evt.Timestamp.UnixMilli(),
		})

	case *events.PushName:
		mu.Lock()
		pCli := client
		pCtx := baseCtx
		mu.Unlock()
		alt := resolveAlternateChatJID(pCtx, pCli, evt.JID)
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "PUSHNAME",
			"jid":       evt.JID.String(),
			"altJid":    alt.String(),
			"name":      evt.NewPushName,
			"timestamp": time.Now().UnixMilli(),
		})

	case *events.Mute:
		muted := false
		muteEnd := int64(0)
		if evt.Action != nil {
			muted = evt.Action.GetMuted()
			muteEnd = evt.Action.GetMuteEndTimestamp()
		}
		mu.Lock()
		mCli := client
		mCtx := baseCtx
		mu.Unlock()
		canon, alt := canonicalizeDMChatJID(mCtx, mCli, evt.JID)
		if canon.IsEmpty() {
			canon = evt.JID
		}
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "MUTE",
			"jid":       canon.String(),
			"altJid":    alt.String(),
			"muted":     muted,
			"muteEnd":   muteEnd,
			"timestamp": evt.Timestamp.UnixMilli(),
		})

	case *events.Pin:
		pinned := false
		if evt.Action != nil {
			pinned = evt.Action.GetPinned()
		}
		mu.Lock()
		pCli := client
		pCtx := baseCtx
		mu.Unlock()
		canon, alt := canonicalizeDMChatJID(pCtx, pCli, evt.JID)
		if canon.IsEmpty() {
			canon = evt.JID
		}
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "PIN",
			"jid":       canon.String(),
			"altJid":    alt.String(),
			"pinned":    pinned,
			"timestamp": evt.Timestamp.UnixMilli(),
		})

	case *events.Archive:
		archived := false
		if evt.Action != nil {
			archived = evt.Action.GetArchived()
		}
		mu.Lock()
		aCli := client
		aCtx := baseCtx
		mu.Unlock()
		canon, alt := canonicalizeDMChatJID(aCtx, aCli, evt.JID)
		if canon.IsEmpty() {
			canon = evt.JID
		}
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "ARCHIVE",
			"jid":       canon.String(),
			"altJid":    alt.String(),
			"archived":  archived,
			"timestamp": evt.Timestamp.UnixMilli(),
		})

	case *events.MarkChatAsRead:
		read := false
		if evt.Action != nil {
			read = evt.Action.GetRead()
		}
		mu.Lock()
		rCli := client
		rCtx := baseCtx
		mu.Unlock()
		canon, alt := canonicalizeDMChatJID(rCtx, rCli, evt.JID)
		if canon.IsEmpty() {
			canon = evt.JID
		}
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":      "MARK_READ",
			"jid":       canon.String(),
			"altJid":    alt.String(),
			"read":      read,
			"timestamp": evt.Timestamp.UnixMilli(),
		})

	case *events.DeleteChat:
		emitChatRangeMetaUpdate("DELETE_CHAT", evt.JID, evt.Action.GetMessageRange(), evt.DeleteMedia, evt.FromFullSync, evt.Timestamp)

	case *events.ClearChat:
		emitChatRangeMetaUpdate("CLEAR_CHAT", evt.JID, evt.Action.GetMessageRange(), evt.DeleteMedia, evt.FromFullSync, evt.Timestamp)

	case *events.Picture:
		go handlePictureUpdate(evt)

	case *events.UserAbout:
		emitJSON("PROFILE_UPDATE", map[string]any{
			"jid":       evt.JID.String(),
			"aboutText": evt.Status,
			"timestamp": evt.Timestamp.UnixMilli(),
		})

	case *events.MediaRetry:
		go processMediaRetry(evt)

	case *events.AppState:
		if evt != nil && evt.SyncActionValue != nil {
			if sa := evt.GetStickerAction(); sa != nil {
				isFav := sa.GetIsFavorite()
				shaHex := hex.EncodeToString(sa.GetFileEncSHA256())
				emitJSON("STICKER_ACTION", map[string]any{
					"fileEncSha256": shaHex,
					"url":           sa.GetURL(),
					"directPath":    sa.GetDirectPath(),
					"isFavorite":    isFav,
				})
			}
		}

	case *events.NewsletterLiveUpdate:
		if evt != nil && !evt.JID.IsEmpty() {
			mu.Lock()
			nCli := client
			mu.Unlock()
			for _, nm := range evt.Messages {
				if nm == nil {
					continue
				}
				summary := formatNewsletterReactionSummary(nm.ReactionCounts, nm.ForwardsCount)
				msgID := string(nm.MessageID)
				if msgID == "" && nm.MessageServerID > 0 {
					msgID = fmt.Sprintf("nl_%s_%d", evt.JID.User, nm.MessageServerID)
				}
				if nm.Message != nil && nCli != nil {
					inner, isVO, isEph, ephSec, _ := unwrapMessageContainer(nm.Message)
					if inner != nil {
						ts := nm.Timestamp
						if ts.IsZero() {
							ts = evt.Time
						}
						info := &types.MessageInfo{
							MessageSource: types.MessageSource{
								Chat:     evt.JID,
								Sender:   evt.JID,
								IsFromMe: false,
								IsGroup:  false,
							},
							ID:        types.MessageID(msgID),
							Timestamp: ts,
						}
						payload, job := buildNormalizedMessage(info, inner, isVO, isEph, ephSec)
						payload["reactionSummary"] = summary
						emitJSON("MESSAGE_UPSERT", payload)
						if job != nil {
							enqueueMedia(job)
						}
						continue
					}
				}
				if msgID != "" && summary != "" {
					emitJSON("MESSAGE_REACTION", map[string]any{
						"chatJid":         evt.JID.String(),
						"targetMessageId": msgID,
						"senderJid":       evt.JID.String(),
						"emoji":           "",
						"reactionSummary": summary,
						"timestamp":       evt.Time.UnixMilli(),
					})
				}
			}
		}

	case *events.CallOffer:
		mu.Lock()
		cCli := client
		mu.Unlock()
		chatJID := evt.GroupJID
		if chatJID.IsEmpty() {
			chatJID = evt.From
		}
		if chatJID.IsEmpty() {
			chatJID = evt.CallCreator
		}
		if !chatJID.IsEmpty() {
			isVideo := false
			if evt.Data != nil {
				_, isVideo = evt.Data.GetOptionalChildByTag("video")
			}
			title := "Voice call"
			if isVideo {
				title = "Video call"
			}
			tsMs := evt.Timestamp.UnixMilli()
			if tsMs <= 0 {
				tsMs = time.Now().UnixMilli()
			}
			callID := evt.CallID
			if callID == "" {
				callID = fmt.Sprintf("call_%d", tsMs)
			} else {
				callID = "call_" + callID
			}
			normChat := normalizeUserJID(cCli, chatJID.String())
			normSender := normalizeUserJID(cCli, evt.CallCreator.String())
			if normSender == "" {
				normSender = normChat
			}
			emitJSON("MESSAGE_UPSERT", map[string]any{
				"id":                callID,
				"chatJid":           normChat,
				"senderJid":         normSender,
				"senderName":        "",
				"isFromMe":          false,
				"isGroup":           chatJID.Server == types.GroupServer,
				"timestamp":         tsMs,
				"body":              title + "|Incoming call",
				"msgType":           "CALL",
				"mimeType":          "",
				"durationSec":       0,
				"fileSize":          0,
				"isViewOnce":        false,
				"viewOnceState":     "NONE",
				"isEphemeral":       false,
				"ephemeralDuration": 0,
				"quotedId":          "",
				"quotedSender":      "",
				"quotedBody":        "",
				"mentionsCsv":       "",
			})
		}
	}
}

func handlePictureUpdate(evt *events.Picture) {
	mu.Lock()
	cli := client
	ctx := baseCtx
	filesDir := appFilesDir
	mu.Unlock()

	avatarPath := ""
	if cli != nil && !evt.Remove {
		info, err := cli.GetProfilePictureInfo(ctx, evt.JID, &whatsmeow.GetProfilePictureParams{Preview: false})
		if err == nil && info != nil && info.URL != "" {
			req, reqErr := http.NewRequestWithContext(ctx, http.MethodGet, info.URL, nil)
			if reqErr == nil {
				setWhatsAppCDNHeaders(req)
				resp, httpErr := (&http.Client{Timeout: 10 * time.Second}).Do(req)
				if httpErr == nil {
					defer resp.Body.Close()
					if b, readErr := io.ReadAll(resp.Body); readErr == nil && len(b) > 0 {
						fname := sanitizeFileName(evt.JID.User + "_" + info.ID + "_hd.jpg")
						target := filepath.Join(filesDir, "vault", "avatars", fname)
						if os.WriteFile(target, b, 0o644) == nil {
							avatarPath = target
						}
					}
				}
			}
		}
	}

	emitJSON("PROFILE_UPDATE", map[string]any{
		"jid":        evt.JID.String(),
		"pictureId":  evt.PictureID,
		"avatarPath": avatarPath,
		"removed":    evt.Remove,
		"timestamp":  evt.Timestamp.UnixMilli(),
	})
}

const (
	maxStatusRecoveryPending     = 64
	statusRecoveryDelay          = 25 * time.Second
	statusReconnectSettleDelay   = 15 * time.Second
	statusReconnectFlushCooldown = 10 * time.Minute
	statusRecoveryWindow         = 10 * time.Minute
	statusRecoveryWindowMax      = 6
	statusRecoveryMinGap         = 3 * time.Second
	statusRecoveryErrBackoff     = 10 * time.Minute
	statusRecoveryMaxConsecErr   = 2
	statusRecoveryMaxPerSync     = 3
)

type statusRecoveryEntry struct {
	msgID     string
	chat      types.JID
	sender    types.JID
	senderAlt types.JID
	tsMs      int64
	timerMu   sync.Mutex
	timer     *time.Timer
	requested atomic.Bool
}

func clearPendingUndecryptable() {
	pendingUndecryptable.Range(func(key, value any) bool {
		if t, ok := value.(*time.Timer); ok && t != nil {
			t.Stop()
		}
		pendingUndecryptable.Delete(key)
		return true
	})
}

func clearPendingStatusRecoveries(deleteEntries bool) {
	pendingStatusRecovery.Range(func(key, value any) bool {
		if entry, ok := value.(*statusRecoveryEntry); ok && entry != nil {
			entry.timerMu.Lock()
			if entry.timer != nil {
				entry.timer.Stop()
				entry.timer = nil
			}
			entry.timerMu.Unlock()
		}
		if deleteEntries {
			pendingStatusRecovery.Delete(key)
		}
		return true
	})
	if deleteEntries {
		resolvedStatusIDs.Range(func(key, _ any) bool {
			resolvedStatusIDs.Delete(key)
			return true
		})
		statusRecoveryMu.Lock()
		statusLastConnectMs = 0
		statusLastFlushMs = 0
		statusLastSentMs = 0
		statusBackoffUntilMs = 0
		statusConsecErrs = 0
		statusSentWindowMs = nil
		statusRecoveryMu.Unlock()
	}
}

func cancelPendingStatusRecovery(msgID string) {
	if msgID == "" {
		return
	}
	if val, ok := pendingStatusRecovery.LoadAndDelete(msgID); ok {
		if entry, ok := val.(*statusRecoveryEntry); ok && entry != nil {
			entry.timerMu.Lock()
			if entry.timer != nil {
				entry.timer.Stop()
				entry.timer = nil
			}
			entry.timerMu.Unlock()
		}
	}
}

func prunePendingStatusRecoveries(nowMs int64) {
	cutoffMs := nowMs - int64((24*time.Hour)/time.Millisecond)
	var oldestKey string
	oldestTs := int64(0)
	count := 0
	pendingStatusRecovery.Range(func(key, value any) bool {
		entry, ok := value.(*statusRecoveryEntry)
		if !ok || entry == nil || entry.tsMs < cutoffMs {
			cancelPendingStatusRecovery(fmt.Sprint(key))
			return true
		}
		count++
		if oldestTs == 0 || entry.tsMs < oldestTs {
			oldestTs = entry.tsMs
			oldestKey = entry.msgID
		}
		return true
	})
	if count >= maxStatusRecoveryPending && oldestKey != "" {
		cancelPendingStatusRecovery(oldestKey)
	}
	resolvedStatusIDs.Range(func(key, value any) bool {
		if ts, ok := value.(int64); !ok || ts < cutoffMs {
			resolvedStatusIDs.Delete(key)
		}
		return true
	})
}

func reserveStatusRecoveryBudget(nowMs int64) (allowed bool, waitMs int64, reason string) {
	statusRecoveryMu.Lock()
	defer statusRecoveryMu.Unlock()

	if statusBackoffUntilMs > nowMs {
		return false, 0, "error_backoff"
	}
	settleMs := int64(statusReconnectSettleDelay / time.Millisecond)
	if statusLastConnectMs > 0 && nowMs-statusLastConnectMs < settleMs {
		return false, 0, "reconnect_settling"
	}
	winCutoff := nowMs - int64(statusRecoveryWindow/time.Millisecond)
	kept := statusSentWindowMs[:0]
	for _, ts := range statusSentWindowMs {
		if ts >= winCutoff {
			kept = append(kept, ts)
		}
	}
	statusSentWindowMs = kept
	if len(statusSentWindowMs) >= statusRecoveryWindowMax {
		return false, 0, "window_budget_exhausted"
	}
	gapMs := int64(statusRecoveryMinGap / time.Millisecond)
	if statusLastSentMs > 0 && nowMs-statusLastSentMs < gapMs {
		waitMs = gapMs - (nowMs - statusLastSentMs)
	}
	sendTs := nowMs + waitMs
	statusLastSentMs = sendTs
	statusSentWindowMs = append(statusSentWindowMs, sendTs)
	return true, waitMs, ""
}

func recordStatusRecoveryOutcome(nowMs int64, sendErr error) {
	statusRecoveryMu.Lock()
	defer statusRecoveryMu.Unlock()
	if sendErr == nil {
		statusConsecErrs = 0
		statusBackoffUntilMs = 0
		return
	}
	statusConsecErrs++
	if statusConsecErrs >= statusRecoveryMaxConsecErr {
		statusBackoffUntilMs = nowMs + int64(statusRecoveryErrBackoff/time.Millisecond)
	}
}

func shouldFlushStatusOnReconnect(nowMs int64) bool {
	statusRecoveryMu.Lock()
	defer statusRecoveryMu.Unlock()
	if statusBackoffUntilMs > nowMs {
		return false
	}
	cooldownMs := int64(statusReconnectFlushCooldown / time.Millisecond)
	if statusLastFlushMs > 0 && nowMs-statusLastFlushMs < cooldownMs {
		return false
	}
	winCutoff := nowMs - int64(statusRecoveryWindow/time.Millisecond)
	recent := 0
	for _, ts := range statusSentWindowMs {
		if ts >= winCutoff {
			recent++
		}
	}
	if recent >= statusRecoveryWindowMax {
		return false
	}
	statusLastFlushMs = nowMs
	return true
}

func pickStatusRecoverySender(ctx context.Context, cli *whatsmeow.Client, sender, senderAlt types.JID) types.JID {
	canon, _ := canonicalizeDMChatJID(ctx, cli, sender)
	if !canon.IsEmpty() {
		return canon
	}
	if !sender.IsEmpty() {
		return sender.ToNonAD()
	}
	if !senderAlt.IsEmpty() {
		return senderAlt.ToNonAD()
	}
	return types.EmptyJID
}

func attemptStatusPeerRecovery(entry *statusRecoveryEntry) {
	if entry == nil || entry.msgID == "" {
		return
	}
	if _, alreadyResolved := resolvedStatusIDs.Load(entry.msgID); alreadyResolved {
		cancelPendingStatusRecovery(entry.msgID)
		return
	}
	nowMs := time.Now().UnixMilli()
	if nowMs-entry.tsMs > int64((24*time.Hour)/time.Millisecond) {
		cancelPendingStatusRecovery(entry.msgID)
		return
	}
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || cli.Store == nil || cli.Store.ID == nil || !cli.IsConnected() || !cli.IsLoggedIn() {
		// Keep unresolved entry queued so flushPendingStatusRecoveries can recover once reconnected.
		return
	}
	if entry.requested.Load() {
		return
	}
	allowed, waitMs, skipReason := reserveStatusRecoveryBudget(nowMs)
	if !allowed {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "INFO",
			"module": "StatusRecovery",
			"msg":    fmt.Sprintf("skipped peer resend id=%s reason=%s", entry.msgID, skipReason),
		})
		return
	}
	if !entry.requested.CompareAndSwap(false, true) {
		return
	}
	if waitMs > 0 {
		time.Sleep(time.Duration(waitMs) * time.Millisecond)
	}
	if _, stillPending := pendingStatusRecovery.Load(entry.msgID); !stillPending {
		return
	}
	if ctx == nil {
		ctx = context.Background()
	}
	reqCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
	defer cancel()
	targetSender := pickStatusRecoverySender(reqCtx, cli, entry.sender, entry.senderAlt)
	chat := entry.chat
	if chat.IsEmpty() {
		chat = types.StatusBroadcastJID
	}
	_, err := cli.SendMessage(
		reqCtx,
		cli.Store.ID.ToNonAD(),
		cli.BuildUnavailableMessageRequest(chat, targetSender, entry.msgID),
		whatsmeow.SendRequestExtra{Peer: true},
	)
	recordStatusRecoveryOutcome(time.Now().UnixMilli(), err)
	emitJSON("BRIDGE_LOG", map[string]any{
		"level":  "INFO",
		"module": "StatusRecovery",
		"msg":    fmt.Sprintf("peer status resend id=%s sender=%s err=%v", entry.msgID, targetSender.String(), err),
	})
}

func scheduleStatusRecovery(evt *events.UndecryptableMessage) {
	if evt == nil || evt.Info.ID == "" || evt.Info.Chat != types.StatusBroadcastJID {
		return
	}
	msgID := string(evt.Info.ID)
	if _, alreadyResolved := resolvedStatusIDs.Load(msgID); alreadyResolved {
		return
	}
	if _, exists := pendingStatusRecovery.Load(msgID); exists {
		return
	}
	tsMs := evt.Info.Timestamp.UnixMilli()
	if tsMs <= 0 {
		tsMs = time.Now().UnixMilli()
	}
	nowMs := time.Now().UnixMilli()
	if nowMs-tsMs > int64((24*time.Hour)/time.Millisecond) {
		return
	}
	prunePendingStatusRecoveries(nowMs)
	entry := &statusRecoveryEntry{
		msgID:     msgID,
		chat:      evt.Info.Chat,
		sender:    evt.Info.Sender,
		senderAlt: evt.Info.SenderAlt,
		tsMs:      tsMs,
	}
	if _, loaded := pendingStatusRecovery.LoadOrStore(msgID, entry); loaded {
		return
	}
	timer := time.AfterFunc(statusRecoveryDelay, func() {
		attemptStatusPeerRecovery(entry)
	})
	entry.timerMu.Lock()
	if _, stillPending := pendingStatusRecovery.Load(msgID); stillPending {
		entry.timer = timer
	} else {
		timer.Stop()
	}
	entry.timerMu.Unlock()
}

func flushPendingStatusRecoveries() {
	nowMs := time.Now().UnixMilli()
	prunePendingStatusRecoveries(nowMs)
	if !shouldFlushStatusOnReconnect(nowMs) {
		return
	}
	var todo []*statusRecoveryEntry
	pendingStatusRecovery.Range(func(_, value any) bool {
		if entry, ok := value.(*statusRecoveryEntry); ok && entry != nil && !entry.requested.Load() {
			if nowMs-entry.tsMs >= int64(statusRecoveryDelay/time.Millisecond) {
				todo = append(todo, entry)
			}
		}
		return true
	})
	if len(todo) == 0 {
		return
	}
	sort.Slice(todo, func(i, j int) bool { return todo[i].tsMs > todo[j].tsMs })
	if len(todo) > statusRecoveryMaxPerSync {
		todo = todo[:statusRecoveryMaxPerSync]
	}
	for _, entry := range todo {
		attemptStatusPeerRecovery(entry)
	}
}

func isStatusMessageInfo(info *types.MessageInfo) bool {
	return info != nil && (info.Chat == types.StatusBroadcastJID || info.IsNewsletterStatus)
}

func shouldIgnoreUndecryptable(evt *events.UndecryptableMessage) bool {
	if evt == nil {
		return true
	}
	if evt.DecryptFailMode == events.DecryptFailHide {
		return true
	}
	if evt.Info.Category == "peer" ||
		evt.Info.Chat == types.StatusBroadcastJID ||
		evt.Info.Chat.Server == types.BroadcastServer ||
		evt.Info.Chat.Server == types.NewsletterServer ||
		evt.Info.Edit != "" {
		return true
	}
	switch evt.Info.Type {
	case "reaction", "poll", "medianotify":
		return true
	}
	return false
}

func cancelPendingUndecryptable(msgID string) {
	if msgID == "" {
		return
	}
	if val, ok := pendingUndecryptable.LoadAndDelete(msgID); ok {
		if t, ok := val.(*time.Timer); ok && t != nil {
			t.Stop()
		}
	}
	retryCounts.Delete(msgID)
	cancelPendingStatusRecovery(msgID)
}

func resolveEmittedStubIfNonVisible(msgID string, chatJid string) {
	if msgID == "" {
		return
	}
	if _, hadStub := emittedStubIDs.LoadAndDelete(msgID); hadStub {
		emitJSON("UNDECRYPTABLE_RESOLVED", map[string]any{
			"id":      msgID,
			"chatJid": chatJid,
		})
	}
}

func processUndecryptable(evt *events.UndecryptableMessage) {
	if evt == nil {
		return
	}
	msgID := string(evt.Info.ID)
	if msgID == "" {
		return
	}

	mu.Lock()
	uCli := client
	uCtx := baseCtx
	mu.Unlock()
	learnLIDMappingFromMessageInfo(uCtx, uCli, &evt.Info)
	canonChat, altChat := canonicalizeDMChatJID(uCtx, uCli, evt.Info.Chat)
	chatStr := canonChat.String()
	if chatStr == "" {
		chatStr = evt.Info.Chat.String()
	}
	canonSender, _ := canonicalizeDMChatJID(uCtx, uCli, evt.Info.Sender)
	senderStr := canonSender.String()
	if senderStr == "" {
		senderStr = evt.Info.Sender.String()
	}

	if evt.IsUnavailable && evt.UnavailableType == events.UnavailableTypeViewOnce {
		emitJSON("VIEW_ONCE_STRIPPED", map[string]any{
			"id":         msgID,
			"chatJid":    chatStr,
			"altChatJid": altChat.String(),
			"senderJid":  senderStr,
			"pushName":   evt.Info.PushName,
			"isFromMe":   evt.Info.IsFromMe,
			"timestamp":  evt.Info.Timestamp.UnixMilli(),
			"reason":     "Key=0B stripped by Meta server on companion session",
		})
		return
	}

	if evt.Info.Chat == types.StatusBroadcastJID {
		scheduleStatusRecovery(evt)
	}

	if shouldIgnoreUndecryptable(evt) {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "INFO",
			"module": "Decrypt",
			"msg": fmt.Sprintf("ignored non-visible undecryptable id=%s chat=%s type=%q cat=%q failMode=%q unavail=%v",
				msgID, chatStr, evt.Info.Type, evt.Info.Category, evt.DecryptFailMode, evt.IsUnavailable),
		})
		return
	}

	val, _ := retryCounts.LoadOrStore(msgID, 0)
	cnt := val.(int) + 1
	retryCounts.Store(msgID, cnt)

	emitJSON("BRIDGE_LOG", map[string]any{
		"level":  "INFO",
		"module": "Decrypt",
		"msg": fmt.Sprintf("pending recovery id=%s chat=%s attempt=%d unavail=%v",
			msgID, chatStr, cnt, evt.IsUnavailable),
	})

	if _, alreadyPending := pendingUndecryptable.Load(msgID); alreadyPending {
		return
	}

	snapChat := chatStr
	snapAlt := altChat.String()
	snapSender := senderStr
	snapPush := evt.Info.PushName
	snapFromMe := evt.Info.IsFromMe
	snapTs := evt.Info.Timestamp.UnixMilli()
	snapCtx := uCtx

	timer := time.AfterFunc(undecryptableGraceDelay, func() {
		pendingUndecryptable.Delete(msgID)
		retryCounts.Delete(msgID)
		if snapCtx != nil && snapCtx.Err() != nil {
			return
		}
		emittedStubIDs.Store(msgID, struct{}{})
		emitJSON("UNDECRYPTABLE_STUB", map[string]any{
			"id":         msgID,
			"chatJid":    snapChat,
			"altChatJid": snapAlt,
			"senderJid":  snapSender,
			"pushName":   snapPush,
			"isFromMe":   snapFromMe,
			"timestamp":  snapTs,
		})
	})
	if _, loaded := pendingUndecryptable.LoadOrStore(msgID, timer); loaded {
		timer.Stop()
	}
}

func processIncomingMessage(evt *events.Message) {
	if evt == nil {
		return
	}
	evtMsgID := string(evt.Info.ID)
	cancelPendingUndecryptable(evtMsgID)

	raw := evt.Message
	if raw == nil {
		raw = evt.RawMessage
	}
	inner, isVO, isEph, ephSec, isEdit := unwrapMessageContainer(raw)
	if isVO || evt.IsViewOnce || evt.IsViewOnceV2 || evt.IsViewOnceV2Extension {
		isVO = true
	}
	if evt.IsEphemeral {
		isEph = true
	}
	if evt.IsEdit {
		isEdit = true
	}
	if inner == nil {
		resolveEmittedStubIfNonVisible(evtMsgID, evt.Info.Chat.String())
		return
	}

	// Intercept ProtocolMessage REVOKE / MESSAGE_EDIT / PEER_DATA_OPERATION_REQUEST_RESPONSE_MESSAGE
	mu.Lock()
	msgCli := client
	msgCtx := baseCtx
	mu.Unlock()
	if msgCtx == nil {
		msgCtx = context.Background()
	}
	learnLIDMappingFromMessageInfo(msgCtx, msgCli, &evt.Info)
	canonEvtChat, altEvtChat := canonicalizeDMChatJID(msgCtx, msgCli, evt.Info.Chat)
	evtChatStr := canonEvtChat.String()
	if evtChatStr == "" {
		evtChatStr = evt.Info.Chat.String()
	}
	canonEvtSender, _ := canonicalizeDMChatJID(msgCtx, msgCli, evt.Info.Sender)
	evtSenderStr := canonEvtSender.String()
	if evtSenderStr == "" {
		evtSenderStr = evt.Info.Sender.String()
	}

	if pm := inner.GetProtocolMessage(); pm != nil {
		switch pm.GetType() {
		case waE2E.ProtocolMessage_REVOKE:
			targetID := pm.GetKey().GetID()
			emitJSON("MESSAGE_REVOKE", map[string]any{
				"chatJid":         evtChatStr,
				"altChatJid":      altEvtChat.String(),
				"targetMessageId": targetID,
				"senderJid":       evtSenderStr,
				"revokedAt":       evt.Info.Timestamp.UnixMilli(),
				"isStatus":        evt.Info.Chat == types.StatusBroadcastJID,
			})
			resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
			return

		case waE2E.ProtocolMessage_MESSAGE_EDIT:
			targetID := pm.GetKey().GetID()
			editedContent := pm.GetEditedMessage()
			newBody, _, _, _, _, _ := extractMessageContent(editedContent)
			editedAt := pm.GetTimestampMS()
			if editedAt == 0 {
				editedAt = evt.Info.Timestamp.UnixMilli()
			}
			emitJSON("MESSAGE_EDIT", map[string]any{
				"chatJid":         evtChatStr,
				"altChatJid":      altEvtChat.String(),
				"targetMessageId": targetID,
				"newBody":         newBody,
				"editedAt":        editedAt,
			})
			resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
			return

		case waE2E.ProtocolMessage_PEER_DATA_OPERATION_REQUEST_RESPONSE_MESSAGE:
			if resp := pm.GetPeerDataOperationRequestResponseMessage(); resp != nil {
				emitJSON("BRIDGE_LOG", map[string]any{
					"level":  "INFO",
					"module": "PeerResend",
					"msg":    fmt.Sprintf("received stanza=%s count=%d", resp.GetStanzaID(), len(resp.GetPeerDataOperationResult())),
				})
				mu.Lock()
				cli := client
				mu.Unlock()
				if cli != nil {
					for _, res := range resp.GetPeerDataOperationResult() {
						pr := res.GetPlaceholderMessageResendResponse()
						if pr == nil || len(pr.GetWebMessageInfoBytes()) == 0 {
							continue
						}
						var webMsg waWeb.WebMessageInfo
						if uErr := proto.Unmarshal(pr.GetWebMessageInfoBytes(), &webMsg); uErr != nil {
							continue
						}
						parsedID := webMsg.GetKey().GetID()
						chatHint := types.EmptyJID
						if webMsg.GetKey().GetRemoteJID() == "" {
							if _, isStatusPending := pendingStatusRecovery.Load(parsedID); isStatusPending {
								chatHint = types.StatusBroadcastJID
							}
						}
						parsed, pErr := parseStatusWebMessage(cli, chatHint, &webMsg)
						if pErr != nil || parsed == nil {
							emitJSON("BRIDGE_LOG", map[string]any{
								"level":  "WARN",
								"module": "PeerResend",
								"msg":    fmt.Sprintf("parse failed id=%s remote=%q err=%v", parsedID, webMsg.GetKey().GetRemoteJID(), pErr),
							})
							continue
						}
						resolvedID := string(parsed.Info.ID)
						cancelPendingUndecryptable(resolvedID)
						rInner, rVO, rEph, rEphSec, _ := unwrapMessageContainer(parsed.Message)
						if parsed.IsViewOnce {
							rVO = true
						}
						if parsed.IsEphemeral {
							rEph = true
						}
						if rInner == nil {
							resolveEmittedStubIfNonVisible(resolvedID, parsed.Info.Chat.String())
							continue
						}
						emittedStubIDs.Delete(resolvedID)
						if isStatusMessageInfo(&parsed.Info) {
							_ = emitStatusFromMessage(&parsed.Info, rInner, rVO, rEph, rEphSec)
						} else {
							rPayload, rJob := buildNormalizedMessage(&parsed.Info, rInner, rVO, rEph, rEphSec)
							emitJSON("MESSAGE_UPSERT", rPayload)
							if shouldAutoDownloadMedia(rJob) {
								enqueueMedia(rJob)
							}
						}
					}
				}
			}
			resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
			return
		}
		resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
		return
	}

	// Reaction message
	if rm := inner.GetReactionMessage(); rm != nil {
		emitJSON("MESSAGE_REACTION", map[string]any{
			"chatJid":         evtChatStr,
			"altChatJid":      altEvtChat.String(),
			"targetMessageId": rm.GetKey().GetID(),
			"senderJid":       evtSenderStr,
			"senderName":      evt.Info.PushName,
			"isFromMe":        evt.Info.IsFromMe,
			"emoji":           rm.GetText(),
			"timestamp":       evt.Info.Timestamp.UnixMilli(),
		})
		resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
		return
	}

	// Pin in chat message
	if pinMsg := inner.GetPinInChatMessage(); pinMsg != nil {
		isUnpin := pinMsg.GetType() == waE2E.PinInChatMessage_UNPIN_FOR_ALL
		var durSec uint32
		if ctxInfo := raw.GetMessageContextInfo(); ctxInfo != nil {
			durSec = ctxInfo.GetMessageAddOnDurationInSecs()
		}
		if durSec == 0 {
			if ctxInfo := inner.GetMessageContextInfo(); ctxInfo != nil {
				durSec = ctxInfo.GetMessageAddOnDurationInSecs()
			}
		}
		ts := pinMsg.GetSenderTimestampMS()
		if ts == 0 {
			ts = evt.Info.Timestamp.UnixMilli()
		}
		emitJSON("MESSAGE_PIN_IN_CHAT", map[string]any{
			"chatJid":         evtChatStr,
			"altChatJid":      altEvtChat.String(),
			"targetMessageId": pinMsg.GetKey().GetID(),
			"senderJid":       evtSenderStr,
			"isFromMe":        evt.Info.IsFromMe,
			"isUnpin":         isUnpin,
			"durationSec":     durSec,
			"timestamp":       ts,
		})
		resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
		return
	}

	// Handle direct Edit wrapper if ProtocolMessage was nested inside EditedMessage
	if isEdit && inner.GetProtocolMessage() == nil {
		body, _, _, _, _, _ := extractMessageContent(inner)
		emitJSON("MESSAGE_EDIT", map[string]any{
			"chatJid":         evtChatStr,
			"altChatJid":      altEvtChat.String(),
			"targetMessageId": evtMsgID,
			"newBody":         body,
			"editedAt":        evt.Info.Timestamp.UnixMilli(),
		})
		resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
		return
	}

	if isStatusMessageInfo(&evt.Info) {
		_ = emitStatusFromMessage(&evt.Info, inner, isVO, isEph, ephSec)
		return
	}

	payload, job := buildNormalizedMessage(&evt.Info, inner, isVO, isEph, ephSec)
	if v, ok := payload["isViewOnce"].(bool); ok && v {
		isVO = true
	}
	msgTypeStr, _ := payload["msgType"].(string)
	bodyStr, _ := payload["body"].(string)
	if !isVO && job == nil && msgTypeStr == "UNKNOWN" && strings.TrimSpace(bodyStr) == "" {
		resolveEmittedStubIfNonVisible(evtMsgID, evtChatStr)
		return
	}
	emittedStubIDs.Delete(evtMsgID)
	if !canonEvtChat.IsEmpty() {
		enqueueAvatarFetch(canonEvtChat)
	} else {
		enqueueAvatarFetch(evt.Info.Chat)
	}
	emitJSON("MESSAGE_UPSERT", payload)

	if shouldAutoDownloadMedia(job) {
		enqueueMedia(job)
	}
}

func processHistorySync(evt *events.HistorySync) {
	if evt == nil || evt.Data == nil {
		return
	}
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil {
		return
	}

	if cli.Store != nil && cli.Store.LIDs != nil {
		for _, mapping := range evt.Data.GetPhoneNumberToLidMappings() {
			if mapping.GetPnJID() != "" && mapping.GetLidJID() != "" {
				if pnJID, err1 := types.ParseJID(mapping.GetPnJID()); err1 == nil {
					if lidJID, err2 := types.ParseJID(mapping.GetLidJID()); err2 == nil {
						_ = cli.Store.LIDs.PutLIDMapping(baseCtx, lidJID, pnJID)
						avatarFetchedMu.Delete(lidJID.String())
						avatarFetchedMu.Delete(pnJID.String())
					}
				}
			}
		}
	}

	pushnames := make([]map[string]any, 0, len(evt.Data.GetPushnames()))
	for _, pn := range evt.Data.GetPushnames() {
		if pn.GetID() != "" && pn.GetPushname() != "" {
			altStr := ""
			if pJID, pErr := types.ParseJID(pn.GetID()); pErr == nil {
				altStr = resolveAlternateChatJID(baseCtx, cli, pJID).String()
			}
			pushnames = append(pushnames, map[string]any{
				"jid":    pn.GetID(),
				"altJid": altStr,
				"name":   pn.GetPushname(),
			})
		}
	}

	type chatMeta struct {
		JID               string `json:"jid"`
		AltJID            string `json:"altJid"`
		Name              string `json:"name"`
		IsGroup           bool   `json:"isGroup"`
		IsCommunityParent bool   `json:"isCommunityParent"`
		ParentGroupJID    string `json:"parentGroupJid"`
		IsDefaultSubgroup bool   `json:"isDefaultSubgroup"`
		UnreadCount       uint32 `json:"unreadCount"`
		Archived          bool   `json:"archived"`
		Locked            bool   `json:"locked"`
		Pinned            uint32 `json:"pinned"`
		MuteEndTime       uint64 `json:"muteEndTime"`
		LastTimestampMs   int64  `json:"lastTimestampMs"`
		EphemeralSec      uint32 `json:"ephemeralSec"`
	}

	const batchLimit = 250
	var chatBatch []chatMeta
	chatBatchIdx := make(map[string]int)
	var msgBatch []map[string]any
	var rxBatch []map[string]any
	var pinBatch []map[string]any
	var priorityMediaJobs []*mediaJob
	var standardMediaJobs []*mediaJob
	convJIDs := make(map[string]bool)

	flush := func(done bool) {
		if len(chatBatch) == 0 && len(msgBatch) == 0 && len(rxBatch) == 0 && len(pinBatch) == 0 && !done {
			return
		}
		emitJSON("HISTORY_SYNC_BATCH", map[string]any{
			"syncType":  evt.Data.GetSyncType().String(),
			"progress":  evt.Data.GetProgress(),
			"chats":     chatBatch,
			"messages":  msgBatch,
			"reactions": rxBatch,
			"pins":      pinBatch,
			"pushnames": pushnames,
			"isFinal":   done,
		})
		pushnames = nil
		chatBatch = nil
		chatBatchIdx = make(map[string]int)
		msgBatch = nil
		rxBatch = nil
		pinBatch = nil
	}

	type histMsgEntry struct {
		tsMs       int64
		msgOrderID uint64
		origIdx    int
		payload    map[string]any
	}

	for _, conv := range evt.Data.GetConversations() {
		rawChatJID, err := types.ParseJID(conv.GetID())
		if err != nil || rawChatJID.IsEmpty() {
			continue
		}
		if rawChatJID == types.StatusBroadcastJID {
			for _, hMsg := range conv.GetMessages() {
				if webMsg := hMsg.GetMessage(); webMsg != nil {
					processHistoryStatus(cli, webMsg)
				}
			}
			continue
		}
		if rawChatJID.Server == types.BroadcastServer {
			continue
		}
		if cli.Store != nil && cli.Store.LIDs != nil && conv.GetPnJID() != "" && conv.GetLidJID() != "" {
			if pnJID, err1 := types.ParseJID(conv.GetPnJID()); err1 == nil {
				if lidJID, err2 := types.ParseJID(conv.GetLidJID()); err2 == nil {
					_ = cli.Store.LIDs.PutLIDMapping(baseCtx, lidJID, pnJID)
				}
			}
		}
		chatJID, altJID := canonicalizeDMChatJID(baseCtx, cli, rawChatJID)
		if chatJID.IsEmpty() {
			chatJID = rawChatJID
		}
		if chatJID == types.StatusBroadcastJID || chatJID.Server == types.BroadcastServer {
			continue
		}
		if altJID.IsEmpty() {
			altJID = resolveAlternateChatJID(baseCtx, cli, chatJID)
		}
		chatName := conv.GetName()
		if chatName == "" {
			chatName = conv.GetDisplayName()
		}
		if chatName == "" || chatName == chatJID.User {
			var resolved string
			var resolvedAlt types.JID
			resolved, _, resolvedAlt = resolveBestContactName(baseCtx, cli, chatJID)
			if resolved != "" {
				chatName = resolved
			}
			if altJID.IsEmpty() && !resolvedAlt.IsEmpty() {
				altJID = resolvedAlt
			}
		}
		cm := chatMeta{
			JID:               chatJID.String(),
			AltJID:            altJID.String(),
			Name:              chatName,
			IsGroup:           chatJID.Server == types.GroupServer,
			IsCommunityParent: conv.GetIsParentGroup(),
			ParentGroupJID:    conv.GetParentGroupID(),
			IsDefaultSubgroup: conv.GetIsDefaultSubgroup(),
			UnreadCount:       conv.GetUnreadCount(),
			Archived:          conv.GetArchived(),
			Locked:            conv.GetLocked(),
			Pinned:            conv.GetPinned(),
			MuteEndTime:       conv.GetMuteEndTime(),
			LastTimestampMs:   int64(conv.GetConversationTimestamp()) * 1000,
			EphemeralSec:      conv.GetEphemeralExpiration(),
		}

		convMsgs := make([]histMsgEntry, 0, len(conv.GetMessages()))
		for origIdx, hMsg := range conv.GetMessages() {
			webMsg := hMsg.GetMessage()
			if webMsg == nil {
				continue
			}
			if stubTitle, stubSub, okCallStub := callStubDetails(webMsg.GetMessageStubType()); okCallStub {
				callID := webMsg.GetKey().GetID()
				tsMs := normalizeTimestampMs(webMsg.GetMessageTimestamp())
				if callID == "" {
					callID = fmt.Sprintf("callstub_%s_%d", chatJID.User, tsMs)
				}
				fromMe := webMsg.GetKey().GetFromMe()
				senderStr := chatJID.String()
				if fromMe && cli.Store != nil && cli.Store.ID != nil {
					senderStr = cli.Store.ID.String()
				} else if webMsg.GetParticipant() != "" {
					senderStr = webMsg.GetParticipant()
				}
				durSec := parseCallStubDuration(webMsg.GetMessageStubParameters())
				if durSec > 0 {
					stubSub = formatCallDuration(int64(durSec))
				}
				convMsgs = append(convMsgs, histMsgEntry{
					tsMs:       tsMs,
					msgOrderID: hMsg.GetMsgOrderID(),
					origIdx:    origIdx,
					payload: map[string]any{
						"id":                callID,
						"chatJid":           chatJID.String(),
						"altChatJid":        altJID.String(),
						"senderJid":         senderStr,
						"senderName":        webMsg.GetPushName(),
						"isFromMe":          fromMe,
						"isGroup":           chatJID.Server == types.GroupServer,
						"timestamp":         tsMs,
						"body":              stubTitle + "|" + stubSub,
						"msgType":           "CALL",
						"mimeType":          "",
						"durationSec":       durSec,
						"fileSize":          0,
						"isViewOnce":        false,
						"viewOnceState":     "NONE",
						"isEphemeral":       false,
						"ephemeralDuration": 0,
						"quotedId":          "",
						"quotedSender":      "",
						"quotedBody":        "",
						"mentionsCsv":       "",
						"reactionSummary":   "",
					},
				})
				continue
			}
			if stubText, okGroupStub := groupStubDetails(webMsg); okGroupStub {
				stubID := webMsg.GetKey().GetID()
				tsMs := normalizeTimestampMs(webMsg.GetMessageTimestamp())
				if stubID == "" {
					stubID = fmt.Sprintf("grpstub_%s_%d", chatJID.User, tsMs)
				}
				senderStr := strings.TrimSpace(webMsg.GetParticipant())
				if senderStr == "" {
					senderStr = chatJID.String()
				}
				convMsgs = append(convMsgs, histMsgEntry{
					tsMs:       tsMs,
					msgOrderID: hMsg.GetMsgOrderID(),
					origIdx:    origIdx,
					payload: map[string]any{
						"id":                stubID,
						"chatJid":           chatJID.String(),
						"altChatJid":        altJID.String(),
						"senderJid":         senderStr,
						"senderName":        webMsg.GetPushName(),
						"isFromMe":          webMsg.GetKey().GetFromMe(),
						"isGroup":           chatJID.Server == types.GroupServer,
						"timestamp":         tsMs,
						"body":              stubText,
						"msgType":           "SYSTEM",
						"mimeType":          "",
						"durationSec":       0,
						"fileSize":          0,
						"deliveryStatus":    "READ",
						"isViewOnce":        false,
						"viewOnceState":     "NONE",
						"isEphemeral":       false,
						"ephemeralDuration": 0,
						"quotedId":          "",
						"quotedSender":      "",
						"quotedBody":        "",
						"mentionsCsv":       "",
						"reactionSummary":   "",
					},
				})
				continue
			}
			parsed, pErr := cli.ParseWebMessage(chatJID, webMsg)
			if pErr != nil || parsed == nil {
				continue
			}
			inner, isVO, isEph, ephSec, _ := unwrapMessageContainer(parsed.Message)
			if parsed.IsViewOnce {
				isVO = true
			}
			if parsed.IsEphemeral {
				isEph = true
			}
			if inner == nil {
				continue
			}
			if rm := inner.GetReactionMessage(); rm != nil {
				if tid := rm.GetKey().GetID(); tid != "" {
					canonRxSender, _ := canonicalizeDMChatJID(baseCtx, cli, parsed.Info.Sender)
					rxSender := canonRxSender.String()
					if rxSender == "" {
						rxSender = parsed.Info.Sender.String()
					}
					rxBatch = append(rxBatch, map[string]any{
						"chatJid":         chatJID.String(),
						"altChatJid":      altJID.String(),
						"targetMessageId": tid,
						"senderJid":       rxSender,
						"senderName":      parsed.Info.PushName,
						"isFromMe":        parsed.Info.IsFromMe,
						"emoji":           rm.GetText(),
						"timestamp":       parsed.Info.Timestamp.UnixMilli(),
					})
				}
				continue
			}
			if pinMsg := inner.GetPinInChatMessage(); pinMsg != nil {
				if tid := pinMsg.GetKey().GetID(); tid != "" {
					isUnpin := pinMsg.GetType() == waE2E.PinInChatMessage_UNPIN_FOR_ALL
					var durSec uint32
					if ctxInfo := parsed.Message.GetMessageContextInfo(); ctxInfo != nil {
						durSec = ctxInfo.GetMessageAddOnDurationInSecs()
					}
					if durSec == 0 {
						if ctxInfo := inner.GetMessageContextInfo(); ctxInfo != nil {
							durSec = ctxInfo.GetMessageAddOnDurationInSecs()
						}
					}
					ts := pinMsg.GetSenderTimestampMS()
					if ts == 0 {
						ts = parsed.Info.Timestamp.UnixMilli()
					}
					canonPinSender, _ := canonicalizeDMChatJID(baseCtx, cli, parsed.Info.Sender)
					pinSender := canonPinSender.String()
					if pinSender == "" {
						pinSender = parsed.Info.Sender.String()
					}
					pinBatch = append(pinBatch, map[string]any{
						"chatJid":         chatJID.String(),
						"altChatJid":      altJID.String(),
						"targetMessageId": tid,
						"senderJid":       pinSender,
						"isFromMe":        parsed.Info.IsFromMe,
						"isUnpin":         isUnpin,
						"durationSec":     durSec,
						"timestamp":       ts,
					})
				}
				continue
			}
			if pm := inner.GetProtocolMessage(); pm != nil {
				continue
			}
			mPayload, job := buildNormalizedMessage(&parsed.Info, inner, isVO, isEph, ephSec)
			if v, ok := mPayload["isViewOnce"].(bool); ok && v {
				isVO = true
			}
			if rxSummary := formatWebMsgReactions(webMsg.GetReactions()); rxSummary != "" {
				mPayload["reactionSummary"] = rxSummary
			}
			mPayload["deliveryStatus"] = deliveryStatusFromWebMsg(webMsg, parsed.Info.IsFromMe)
			mt, _ := mPayload["msgType"].(string)
			bd, _ := mPayload["body"].(string)
			if !isVO && job == nil && mt == "UNKNOWN" && strings.TrimSpace(bd) == "" {
				continue
			}
			tsMs, _ := mPayload["timestamp"].(int64)
			convMsgs = append(convMsgs, histMsgEntry{
				tsMs:       tsMs,
				msgOrderID: hMsg.GetMsgOrderID(),
				origIdx:    origIdx,
				payload:    mPayload,
			})
			if job != nil {
				job.IsHistorical = parsed.Info.IsFromMe || time.Since(parsed.Info.Timestamp) > 24*time.Hour
				saveMediaMeta(job)
				switch {
				case isVO && job.IsHistorical:
					// Do not fire CDN 404/410 requests in bulk for old or previously sent View-Once media in HistorySync
				case isVO || job.MediaType == "PTT" || job.MediaType == "AUDIO":
					priorityMediaJobs = append(priorityMediaJobs, job)
				case job.MediaType == "IMAGE" || job.MediaType == "STICKER" || job.MediaType == "VIDEO":
					standardMediaJobs = append(standardMediaJobs, job)
				}
			}
		}

		isGroupOrChannel := chatJID.Server == types.GroupServer || chatJID.Server == types.NewsletterServer
		if !isGroupOrChannel && len(convMsgs) == 0 {
			// Skip empty DM conversation stubs (unchatted @lid co-members, cleared/metadata stubs)
			// so they never flood the home chat list or trigger stranger avatar requests.
			continue
		}

		enqueueAvatarFetch(chatJID)
		convJIDs[chatJID.String()] = true
		if idx, exists := chatBatchIdx[cm.JID]; exists {
			prev := chatBatch[idx]
			if prev.AltJID == "" {
				prev.AltJID = cm.AltJID
			}
			if prev.Name == "" || strings.HasPrefix(prev.Name, "+") || prev.Name == chatJID.User {
				if cm.Name != "" {
					prev.Name = cm.Name
				}
			}
			if cm.UnreadCount > prev.UnreadCount {
				prev.UnreadCount = cm.UnreadCount
			}
			if cm.LastTimestampMs > prev.LastTimestampMs {
				prev.LastTimestampMs = cm.LastTimestampMs
			}
			prev.Archived = prev.Archived || cm.Archived
			prev.Locked = prev.Locked || cm.Locked
			if cm.Pinned > prev.Pinned {
				prev.Pinned = cm.Pinned
			}
			if cm.MuteEndTime > prev.MuteEndTime {
				prev.MuteEndTime = cm.MuteEndTime
			}
			if cm.EphemeralSec > prev.EphemeralSec {
				prev.EphemeralSec = cm.EphemeralSec
			}
			chatBatch[idx] = prev
		} else {
			chatBatchIdx[cm.JID] = len(chatBatch)
			chatBatch = append(chatBatch, cm)
		}

		// Sort messages within this conversation chronologically and assign +1ms tie-breakers for same-second messages
		sort.SliceStable(convMsgs, func(i, j int) bool {
			if convMsgs[i].tsMs != convMsgs[j].tsMs {
				return convMsgs[i].tsMs < convMsgs[j].tsMs
			}
			if convMsgs[i].msgOrderID != convMsgs[j].msgOrderID {
				return convMsgs[i].msgOrderID < convMsgs[j].msgOrderID
			}
			return convMsgs[i].origIdx < convMsgs[j].origIdx
		})
		var lastTsMs int64
		for i := range convMsgs {
			if lastTsMs > 0 && convMsgs[i].tsMs <= lastTsMs && convMsgs[i].tsMs/1000 == lastTsMs/1000 {
				convMsgs[i].tsMs = lastTsMs + 1
				convMsgs[i].payload["timestamp"] = convMsgs[i].tsMs
			}
			lastTsMs = convMsgs[i].tsMs
			msgBatch = append(msgBatch, convMsgs[i].payload)
			if len(msgBatch) >= batchLimit {
				flush(false)
			}
		}
	}

	for _, rec := range evt.Data.GetCallLogRecords() {
		if rec == nil {
			continue
		}
		targetRaw := pickCallRecordTargetJID(cli, rec)
		targetChat := normalizeUserJID(cli, targetRaw)
		if targetChat == "" {
			continue
		}
		parsedChat, _ := types.ParseJID(targetChat)
		altChat := resolveAlternateChatJID(baseCtx, cli, parsedChat).ToNonAD().String()
		if !convJIDs[targetChat] && altChat != "" && convJIDs[altChat] {
			targetChat = altChat
			parsedChat, _ = types.ParseJID(targetChat)
		}
		title := "Voice call"
		if rec.GetIsVideo() {
			title = "Video call"
		}
		dur := rec.GetDuration()
		subtitle := callRecordSubtitle(rec.GetCallResult(), dur)
		var tsMs int64
		if rec.GetStartTime() > 0 {
			tsMs = normalizeTimestampMs(uint64(rec.GetStartTime()))
		} else {
			tsMs = time.Now().UnixMilli()
		}
		callID := rec.GetCallID()
		if callID == "" {
			callID = fmt.Sprintf("%s_%d", parsedChat.User, tsMs)
		}
		fromMe := !rec.GetIsIncoming()
		senderJID := normalizeUserJID(cli, rec.GetCallCreatorJID())
		if senderJID == "" {
			if fromMe && cli.Store != nil && cli.Store.ID != nil {
				senderJID = cli.Store.ID.ToNonAD().String()
			} else {
				senderJID = targetChat
			}
		}
		msgBatch = append(msgBatch, map[string]any{
			"id":                "call_" + callID,
			"chatJid":           targetChat,
			"senderJid":         senderJID,
			"senderName":        "",
			"isFromMe":          fromMe,
			"isGroup":           parsedChat.Server == types.GroupServer || rec.GetGroupJID() != "",
			"timestamp":         tsMs,
			"body":              title + "|" + subtitle,
			"msgType":           "CALL",
			"mimeType":          "",
			"durationSec":       int(dur),
			"fileSize":          0,
			"isViewOnce":        false,
			"viewOnceState":     "NONE",
			"isEphemeral":       false,
			"ephemeralDuration": 0,
			"quotedId":          "",
			"quotedSender":      "",
			"quotedBody":        "",
			"mentionsCsv":       "",
		})
		if len(msgBatch) >= batchLimit {
			flush(false)
		}
	}

	statusV3List := evt.Data.GetStatusV3Messages()
	emittedStatusCount := 0
	for _, stMsg := range statusV3List {
		if processHistoryStatus(cli, stMsg) {
			emittedStatusCount++
		}
	}
	if len(statusV3List) > 0 || evt.Data.GetSyncType().String() == "INITIAL_STATUS_V3" {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "INFO",
			"module": "HistorySync",
			"msg": fmt.Sprintf("syncType=%s statusV3Total=%d statusEmitted=%d",
				evt.Data.GetSyncType().String(), len(statusV3List), emittedStatusCount),
		})
	}

	flush(true)

	// Enqueue voice notes / View Once first so audio isn't starved behind large image backlogs.
	// Large videos (> 5 MB) are never auto-downloaded during history sync.
	for _, job := range priorityMediaJobs {
		if shouldAutoDownloadMedia(job) {
			enqueueMedia(job)
		}
	}
	for _, job := range standardMediaJobs {
		if shouldAutoDownloadMedia(job) {
			enqueueMedia(job)
		}
	}
}

func resolveStatusFallbackParticipant(webMsg *waWeb.WebMessageInfo) string {
	if webMsg == nil {
		return ""
	}
	for _, cand := range []string{
		webMsg.GetKey().GetParticipant(),
		webMsg.GetParticipant(),
		webMsg.GetOriginalSelfAuthorUserJIDString(),
	} {
		if trimmed := strings.TrimSpace(cand); trimmed != "" {
			return trimmed
		}
	}
	if remote := strings.TrimSpace(webMsg.GetKey().GetRemoteJID()); remote != "" {
		if rJID, err := types.ParseJID(remote); err == nil &&
			(rJID.Server == types.DefaultUserServer || rJID.Server == types.HiddenUserServer) {
			return remote
		}
	}
	return ""
}

func parseStatusWebMessage(cli *whatsmeow.Client, chatHint types.JID, webMsg *waWeb.WebMessageInfo) (*events.Message, error) {
	if cli == nil || webMsg == nil {
		return nil, errors.New("nil client or webMsg")
	}
	targetHint := chatHint
	if targetHint.IsEmpty() && strings.TrimSpace(webMsg.GetKey().GetRemoteJID()) == "" {
		targetHint = types.EmptyJID
	}
	msgToParse := webMsg
	if !webMsg.GetKey().GetFromMe() &&
		strings.TrimSpace(webMsg.GetParticipant()) == "" &&
		strings.TrimSpace(webMsg.GetKey().GetParticipant()) == "" {
		if fallbackPart := resolveStatusFallbackParticipant(webMsg); fallbackPart != "" {
			if cloned, ok := proto.Clone(webMsg).(*waWeb.WebMessageInfo); ok && cloned != nil {
				if cloned.Key == nil {
					cloned.Key = &waCommon.MessageKey{}
				}
				cloned.Key.Participant = proto.String(fallbackPart)
				msgToParse = cloned
			}
		}
	}
	parsed, err := cli.ParseWebMessage(targetHint, msgToParse)
	if err != nil || parsed == nil {
		return nil, err
	}
	if parsed.Info.Chat.IsEmpty() && !chatHint.IsEmpty() {
		parsed.Info.Chat = chatHint
	}
	if parsed.Info.Sender.IsEmpty() {
		if rawPart := resolveStatusFallbackParticipant(webMsg); rawPart != "" {
			if pJID, pErr := types.ParseJID(rawPart); pErr == nil && !pJID.IsEmpty() {
				parsed.Info.Sender = pJID
			}
		}
	}
	return parsed, nil
}

func emitStatusFromMessage(info *types.MessageInfo, inner *waE2E.Message, isVO bool, isEph bool, ephSec uint32) bool {
	if info == nil || inner == nil {
		return false
	}
	payload, job := buildNormalizedMessage(info, inner, isVO, isEph, ephSec)
	mt, _ := payload["msgType"].(string)
	bd, _ := payload["body"].(string)
	if !isVO && job == nil && mt == "UNKNOWN" && strings.TrimSpace(bd) == "" {
		resolveEmittedStubIfNonVisible(string(info.ID), info.Chat.String())
		return false
	}
	msgID := string(info.ID)
	resolvedStatusIDs.Store(msgID, time.Now().UnixMilli())
	cancelPendingStatusRecovery(msgID)
	emittedStubIDs.Delete(msgID)
	statusSender := info.Sender
	if statusSender.IsEmpty() && info.IsNewsletterStatus {
		statusSender = info.Chat
	}
	if !statusSender.IsEmpty() {
		enrichStatusPayload(statusSender, payload)
	}
	emitJSON("STATUS_UPSERT", payload)
	if shouldAutoDownloadMedia(job) {
		enqueueMedia(job)
	}
	return true
}

func processHistoryStatus(cli *whatsmeow.Client, webMsg *waWeb.WebMessageInfo) bool {
	if webMsg == nil {
		return false
	}
	parsed, err := parseStatusWebMessage(cli, types.StatusBroadcastJID, webMsg)
	if err != nil || parsed == nil {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "HistorySync",
			"msg":    fmt.Sprintf("skipped status id=%s err=%v", webMsg.GetKey().GetID(), err),
		})
		return false
	}
	inner, isVO, isEph, ephSec, _ := unwrapMessageContainer(parsed.Message)
	if inner == nil {
		return false
	}
	return emitStatusFromMessage(&parsed.Info, inner, isVO, isEph, ephSec)
}

func isNumericOrPhoneDisplayName(name string) bool {
	trimmed := strings.TrimSpace(name)
	if trimmed == "" {
		return false
	}
	digits := 0
	for _, r := range trimmed {
		switch {
		case r >= '0' && r <= '9':
			digits++
		case r == '+' || r == ' ' || r == '-' || r == '(' || r == ')':
		default:
			return false
		}
	}
	return digits > 0
}

// enrichStatusPayload canonicalizes status sender JIDs, preserves alternate JIDs,
// and resolves the strongest saved-contact or push name before emitting STATUS_UPSERT.
func enrichStatusPayload(sender types.JID, payload map[string]any) {
	if sender.IsEmpty() || payload == nil {
		return
	}
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if ctx == nil {
		ctx = context.Background()
	}

	canonSender, altSender := canonicalizeDMChatJID(ctx, cli, sender)
	if canonSender.IsEmpty() {
		canonSender = sender.ToNonAD()
	}
	if s := canonSender.String(); s != "" {
		payload["senderJid"] = s
	} else if norm := normalizeUserJID(cli, sender.String()); norm != "" {
		payload["senderJid"] = norm
	}
	if !altSender.IsEmpty() {
		payload["altSenderJid"] = altSender.String()
	}

	existingName, _ := payload["senderName"].(string)
	existingName = strings.TrimSpace(existingName)
	bestName, isSaved, _ := resolveBestContactName(ctx, cli, canonSender)
	if bestName == "" && canonSender != sender.ToNonAD() {
		bestName, isSaved, _ = resolveBestContactName(ctx, cli, sender.ToNonAD())
	}
	bestName = strings.TrimSpace(bestName)
	if bestName != "" {
		if isSaved || existingName == "" || isNumericOrPhoneDisplayName(existingName) || !isNumericOrPhoneDisplayName(bestName) {
			payload["senderName"] = bestName
		}
	}
	enqueueAvatarFetch(canonSender)
}

func buildNormalizedMessage(
	info *types.MessageInfo,
	inner *waE2E.Message,
	isVO bool,
	isEph bool,
	ephSec uint32,
) (map[string]any, *mediaJob) {
	if !isVO && hasInnerViewOnceFlag(inner) {
		isVO = true
	}
	body, msgType, mimeType, durationSec, fileSize, dlMsg := extractMessageContent(inner)
	quotedId, quotedSender, quotedBody, mentionsCsv := extractContextInfo(inner)

	hasKey := dlMsg != nil && isNonZeroBytes(dlMsg.GetMediaKey())
	hasDirectPath := dlMsg != nil && strings.TrimSpace(dlMsg.GetDirectPath()) != ""
	isChannel := info.Chat.Server == types.NewsletterServer
	voState := "NONE"
	if isVO {
		if hasKey && hasDirectPath {
			voState = "AVAILABLE"
		} else {
			voState = "STRIPPED"
		}
	}

	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if ctx == nil {
		ctx = context.Background()
	}

	learnLIDMappingFromMessageInfo(ctx, cli, info)
	canonChat, altChat := canonicalizeDMChatJID(ctx, cli, info.Chat)
	chatStr := canonChat.String()
	if chatStr == "" {
		chatStr = info.Chat.String()
	}
	canonSender, altSender := canonicalizeDMChatJID(ctx, cli, info.Sender)
	senderStr := canonSender.String()
	if senderStr == "" {
		senderStr = info.Sender.String()
	}
	altSenderStr := altSender.String()

	resolvedSenderName := strings.TrimSpace(info.PushName)
	if cli != nil && !info.Sender.IsEmpty() {
		sMeta := resolveLocalParticipantMeta(ctx, cli, "", info.Sender)
		switch {
		case sMeta.SavedName != "":
			resolvedSenderName = sMeta.SavedName
		case resolvedSenderName == "" && sMeta.PushName != "":
			resolvedSenderName = sMeta.PushName
		case resolvedSenderName == "" && sMeta.Phone != "":
			resolvedSenderName = sMeta.Phone
		}
	}
	if cli != nil && quotedSender != "" {
		if qJID, qErr := types.ParseJID(quotedSender); qErr == nil && qJID.Server == types.HiddenUserServer {
			qMeta := resolveLocalParticipantMeta(ctx, cli, "", qJID)
			if qMeta.AltJID != "" && !strings.HasSuffix(qMeta.AltJID, "@lid") {
				quotedSender = qMeta.AltJID
			}
		}
	}

	msgTsMs := info.Timestamp.UnixMilli()
	if info.Timestamp.IsZero() || msgTsMs <= 0 {
		msgTsMs = time.Now().UnixMilli()
	}

	payload := map[string]any{
		"id":                string(info.ID),
		"chatJid":           chatStr,
		"altChatJid":        altChat.String(),
		"senderJid":         senderStr,
		"altSenderJid":      altSenderStr,
		"senderName":        resolvedSenderName,
		"isFromMe":          info.IsFromMe,
		"isGroup":           info.IsGroup,
		"timestamp":         msgTsMs,
		"body":              body,
		"msgType":           msgType,
		"mimeType":          mimeType,
		"durationSec":       durationSec,
		"fileSize":          fileSize,
		"isViewOnce":        isVO,
		"viewOnceState":     voState,
		"isEphemeral":       isEph,
		"ephemeralDuration": ephSec,
		"quotedId":          quotedId,
		"quotedSender":      quotedSender,
		"quotedBody":        quotedBody,
		"mentionsCsv":       mentionsCsv,
		"reactionSummary":   "",
	}

	var job *mediaJob
	if !isVO && dlMsg != nil && hasDirectPath && (hasKey || isChannel) {
		isHist := !info.Timestamp.IsZero() && time.Since(info.Timestamp) > 24*time.Hour
		job = &mediaJob{
			MessageID:     string(info.ID),
			ChatJID:       chatStr,
			SenderJID:     senderStr,
			MediaType:     msgType,
			MimeType:      mimeType,
			DirectPath:    dlMsg.GetDirectPath(),
			MediaKey:      append([]byte(nil), dlMsg.GetMediaKey()...),
			FileSHA256:    append([]byte(nil), dlMsg.GetFileSHA256()...),
			FileEncSHA256: append([]byte(nil), dlMsg.GetFileEncSHA256()...),
			FileLength:    fileSize,
			IsViewOnce:    false,
			IsStatus:      isStatusMessageInfo(info),
			IsFromMe:      info.IsFromMe,
			IsHistorical:  isHist,
			MetaVer:       1,
			Msg:           dlMsg,
		}
		saveMediaMeta(job)
	}
	return payload, job
}

func joinBusinessParts(parts ...string) string {
	var cleaned []string
	for _, p := range parts {
		t := strings.TrimSpace(p)
		if t == "" {
			continue
		}
		if len(cleaned) > 0 && cleaned[len(cleaned)-1] == t {
			continue
		}
		cleaned = append(cleaned, t)
	}
	return strings.Join(cleaned, "\n")
}

func formatDocumentBody(fileName, caption, extraText string) string {
	fn := strings.TrimSpace(fileName)
	capTxt := strings.TrimSpace(caption)
	extra := strings.TrimSpace(extraText)
	captionPart := joinBusinessParts(capTxt, extra)
	switch {
	case fn != "" && captionPart != "" && captionPart != fn:
		return fn + "\n" + captionPart
	case fn != "":
		return fn
	default:
		return captionPart
	}
}

func extractBusinessMediaOrText(
	im *waE2E.ImageMessage,
	vm *waE2E.VideoMessage,
	dm *waE2E.DocumentMessage,
	text string,
) (
	body string,
	msgType string,
	mimeType string,
	durationSec int,
	fileSize uint64,
	dl whatsmeow.DownloadableMessage,
) {
	switch {
	case im != nil:
		capTxt := joinBusinessParts(text, im.GetCaption())
		return capTxt, "IMAGE", im.GetMimetype(), 0, im.GetFileLength(), im
	case vm != nil:
		capTxt := joinBusinessParts(text, vm.GetCaption())
		return capTxt, "VIDEO", vm.GetMimetype(), int(vm.GetSeconds()), vm.GetFileLength(), vm
	case dm != nil:
		docLabel := strings.TrimSpace(dm.GetFileName())
		if docLabel == "" {
			docLabel = strings.TrimSpace(dm.GetTitle())
		}
		capTxt := formatDocumentBody(docLabel, dm.GetCaption(), text)
		return capTxt, "DOCUMENT", dm.GetMimetype(), 0, dm.GetFileLength(), dm
	case text != "":
		return text, "TEXT", "text/plain", 0, 0, nil
	default:
		return "", "UNKNOWN", "", 0, 0, nil
	}
}

func extractInteractiveMessage(im *waE2E.InteractiveMessage) (
	body string,
	msgType string,
	mimeType string,
	durationSec int,
	fileSize uint64,
	dl whatsmeow.DownloadableMessage,
) {
	if im == nil {
		return "", "UNKNOWN", "", 0, 0, nil
	}
	var hdrTitle, hdrSub, bodyTxt, footerTxt string
	var hdrImg *waE2E.ImageMessage
	var hdrVid *waE2E.VideoMessage
	var hdrDoc *waE2E.DocumentMessage
	if hdr := im.GetHeader(); hdr != nil {
		hdrTitle = hdr.GetTitle()
		hdrSub = hdr.GetSubtitle()
		hdrImg = hdr.GetImageMessage()
		hdrVid = hdr.GetVideoMessage()
		hdrDoc = hdr.GetDocumentMessage()
	}
	if b := im.GetBody(); b != nil {
		bodyTxt = b.GetText()
	}
	if f := im.GetFooter(); f != nil {
		footerTxt = f.GetText()
	}
	text := joinBusinessParts(hdrTitle, hdrSub, bodyTxt, footerTxt)
	return extractBusinessMediaOrText(hdrImg, hdrVid, hdrDoc, text)
}

func extractTemplateMessage(tm *waE2E.TemplateMessage) (
	body string,
	msgType string,
	mimeType string,
	durationSec int,
	fileSize uint64,
	dl whatsmeow.DownloadableMessage,
) {
	if tm == nil {
		return "", "UNKNOWN", "", 0, 0, nil
	}
	ht := tm.GetHydratedTemplate()
	if ht == nil {
		ht = tm.GetHydratedFourRowTemplate()
	}
	if ht != nil {
		text := joinBusinessParts(
			ht.GetHydratedTitleText(),
			ht.GetHydratedContentText(),
			ht.GetHydratedFooterText(),
		)
		return extractBusinessMediaOrText(
			ht.GetImageMessage(),
			ht.GetVideoMessage(),
			ht.GetDocumentMessage(),
			text,
		)
	}
	if im := tm.GetInteractiveMessageTemplate(); im != nil {
		return extractInteractiveMessage(im)
	}
	return "", "UNKNOWN", "", 0, 0, nil
}

func extractMessageContent(m *waE2E.Message) (
	body string,
	msgType string,
	mimeType string,
	durationSec int,
	fileSize uint64,
	dl whatsmeow.DownloadableMessage,
) {
	if m == nil {
		return "", "TEXT", "", 0, 0, nil
	}
	switch {
	case m.Conversation != nil:
		return m.GetConversation(), "TEXT", "text/plain", 0, 0, nil
	case m.GetExtendedTextMessage() != nil:
		return m.GetExtendedTextMessage().GetText(), "TEXT", "text/plain", 0, 0, nil
	case m.GetImageMessage() != nil:
		im := m.GetImageMessage()
		return im.GetCaption(), "IMAGE", im.GetMimetype(), 0, im.GetFileLength(), im
	case m.GetVideoMessage() != nil:
		vm := m.GetVideoMessage()
		return vm.GetCaption(), "VIDEO", vm.GetMimetype(), int(vm.GetSeconds()), vm.GetFileLength(), vm
	case m.GetAudioMessage() != nil:
		am := m.GetAudioMessage()
		mt := "AUDIO"
		if am.GetPTT() {
			mt = "PTT"
		}
		return "", mt, am.GetMimetype(), int(am.GetSeconds()), am.GetFileLength(), am
	case m.GetDocumentMessage() != nil:
		dm := m.GetDocumentMessage()
		fileName := strings.TrimSpace(dm.GetFileName())
		if fileName == "" {
			fileName = strings.TrimSpace(dm.GetTitle())
		}
		capTxt := formatDocumentBody(fileName, dm.GetCaption(), "")
		return capTxt, "DOCUMENT", dm.GetMimetype(), 0, dm.GetFileLength(), dm
	case m.GetStickerMessage() != nil:
		sm := m.GetStickerMessage()
		return "", "STICKER", sm.GetMimetype(), 0, sm.GetFileLength(), sm
	case m.GetCallLogMesssage() != nil:
		cl := m.GetCallLogMesssage()
		title := "Voice call"
		if cl.GetIsVideo() {
			title = "Video call"
		}
		dur := cl.GetDurationSecs()
		var sub string
		switch {
		case dur > 0:
			sub = formatCallDuration(dur)
		case cl.GetCallOutcome() == waE2E.CallLogMessage_CONNECTED:
			sub = "Answered"
		case cl.GetCallOutcome() == waE2E.CallLogMessage_REJECTED:
			sub = "Declined"
		default:
			sub = "No answer"
		}
		return title + "|" + sub, "CALL", "", int(dur), 0, nil
	case m.GetContactMessage() != nil:
		cm := m.GetContactMessage()
		name := strings.TrimSpace(cm.GetDisplayName())
		if name == "" {
			name = "Contact"
		}
		return "👤 " + name, "TEXT", "text/x-vcard", 0, 0, nil
	case m.GetContactsArrayMessage() != nil:
		cam := m.GetContactsArrayMessage()
		name := strings.TrimSpace(cam.GetDisplayName())
		if name == "" {
			name = "Contacts"
		}
		return "👤 " + name, "TEXT", "text/x-vcard", 0, 0, nil
	case m.GetLocationMessage() != nil:
		lm := m.GetLocationMessage()
		label := strings.TrimSpace(lm.GetName())
		if label == "" {
			label = strings.TrimSpace(lm.GetAddress())
		}
		if label == "" {
			label = "Location"
		}
		return "📍 " + label, "TEXT", "", 0, 0, nil
	case m.GetLiveLocationMessage() != nil:
		llm := m.GetLiveLocationMessage()
		capTxt := strings.TrimSpace(llm.GetCaption())
		if capTxt == "" {
			capTxt = "Live location"
		}
		return "📍 " + capTxt, "TEXT", "", 0, 0, nil
	case m.GetTemplateMessage() != nil:
		return extractTemplateMessage(m.GetTemplateMessage())
	case m.GetInteractiveMessage() != nil:
		return extractInteractiveMessage(m.GetInteractiveMessage())
	case m.GetButtonsMessage() != nil:
		bm := m.GetButtonsMessage()
		text := joinBusinessParts(bm.GetText(), bm.GetContentText(), bm.GetFooterText())
		return extractBusinessMediaOrText(bm.GetImageMessage(), bm.GetVideoMessage(), bm.GetDocumentMessage(), text)
	case m.GetListMessage() != nil:
		lm := m.GetListMessage()
		text := joinBusinessParts(lm.GetTitle(), lm.GetDescription(), lm.GetFooterText())
		if text != "" {
			return text, "TEXT", "text/plain", 0, 0, nil
		}
		return "", "UNKNOWN", "", 0, 0, nil
	case m.GetStatusQuotedMessage() != nil:
		sq := m.GetStatusQuotedMessage()
		if txt := strings.TrimSpace(sq.GetText()); txt != "" {
			return txt, "TEXT", "text/plain", 0, 0, nil
		}
		return "", "UNKNOWN", "", 0, 0, nil
	default:
		return "", "UNKNOWN", "", 0, 0, nil
	}
}

func extractContextInfo(m *waE2E.Message) (qId, qSender, qBody, mentionsCsv string) {
	if m == nil {
		return
	}
	var ctx *waE2E.ContextInfo
	switch {
	case m.GetExtendedTextMessage() != nil:
		ctx = m.GetExtendedTextMessage().GetContextInfo()
	case m.GetImageMessage() != nil:
		ctx = m.GetImageMessage().GetContextInfo()
	case m.GetVideoMessage() != nil:
		ctx = m.GetVideoMessage().GetContextInfo()
	case m.GetAudioMessage() != nil:
		ctx = m.GetAudioMessage().GetContextInfo()
	case m.GetDocumentMessage() != nil:
		ctx = m.GetDocumentMessage().GetContextInfo()
	case m.GetStickerMessage() != nil:
		ctx = m.GetStickerMessage().GetContextInfo()
	case m.GetTemplateMessage() != nil:
		ctx = m.GetTemplateMessage().GetContextInfo()
	case m.GetInteractiveMessage() != nil:
		ctx = m.GetInteractiveMessage().GetContextInfo()
	case m.GetButtonsMessage() != nil:
		ctx = m.GetButtonsMessage().GetContextInfo()
	case m.GetListMessage() != nil:
		ctx = m.GetListMessage().GetContextInfo()
	}
	if ctx == nil {
		return
	}
	qId = ctx.GetStanzaID()
	qSender = ctx.GetParticipant()
	if qm := ctx.GetQuotedMessage(); qm != nil {
		qBody, _, _, _, _, _ = extractMessageContent(qm)
	}
	if len(ctx.GetMentionedJID()) > 0 {
		mentionsCsv = strings.Join(ctx.GetMentionedJID(), ",")
	}
	return
}

func SetAutoDownloadPolicy(allowPhoto, allowAudio, allowVideo, allowDoc bool) {
	disableAutoDlPhoto.Store(!allowPhoto)
	disableAutoDlAudio.Store(!allowAudio)
	disableAutoDlVideo.Store(!allowVideo)
	disableAutoDlDoc.Store(!allowDoc)
}

func shouldAutoDownloadMedia(job *mediaJob) bool {
	if job == nil || job.MessageID == "" || job.IsViewOnce {
		return false
	}
	switch job.MediaType {
	case "IMAGE", "STICKER":
		if disableAutoDlPhoto.Load() {
			return false
		}
		return job.FileLength <= maxManualMediaBytes
	case "PTT", "AUDIO":
		if disableAutoDlAudio.Load() {
			return false
		}
		return job.FileLength <= maxManualMediaBytes
	case "VIDEO":
		if disableAutoDlVideo.Load() {
			return false
		}
		limit := autoDownloadVideoMaxBytes
		if job.IsStatus {
			limit = autoDownloadVaultMaxBytes
		}
		return job.FileLength > 0 && job.FileLength <= limit
	case "DOCUMENT":
		if disableAutoDlDoc.Load() {
			return false
		}
		return job.FileLength > 0 && job.FileLength <= autoDownloadVaultMaxBytes
	default:
		if disableAutoDlPhoto.Load() {
			return false
		}
		return job.FileLength <= maxManualMediaBytes
	}
}

func enqueueMedia(job *mediaJob) {
	if job == nil || mediaQueue == nil {
		return
	}
	select {
	case mediaQueue <- *job:
	default:
		// Drop only if queue saturated to keep event loop lock-free
	}
}

func waMediaTypeFromString(mediaType string) whatsmeow.MediaType {
	switch mediaType {
	case "PTT", "AUDIO":
		return whatsmeow.MediaAudio
	case "VIDEO":
		return whatsmeow.MediaVideo
	case "DOCUMENT":
		return whatsmeow.MediaDocument
	default:
		return whatsmeow.MediaImage
	}
}

func triggerMediaRetry(cli *whatsmeow.Client, job *mediaJob) {
	if cli == nil || job == nil || job.MessageID == "" || len(job.MediaKey) == 0 || job.IsViewOnce {
		return
	}
	now := time.Now()
	if prev, ok := mediaRetrySent.Load(job.MessageID); ok {
		if t, ok2 := prev.(time.Time); ok2 && now.Sub(t) < 30*time.Second {
			return
		}
	}
	mediaRetrySent.Store(job.MessageID, now)

	chat, err := types.ParseJID(job.ChatJID)
	if err != nil || chat.IsEmpty() {
		return
	}

	isFromMe := job.IsFromMe
	var sender types.JID
	if job.SenderJID != "" {
		if parsedSender, sErr := types.ParseJID(job.SenderJID); sErr == nil {
			sender = parsedSender
			if !isFromMe && cli.Store != nil {
				if (cli.Store.ID != nil && parsedSender.User == cli.Store.ID.User) ||
					(!cli.Store.LID.IsEmpty() && parsedSender.User == cli.Store.LID.User) {
					isFromMe = true
				}
			}
		}
	}

	ensureConnectedAndAuthenticated(cli, 6*time.Second)

	ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
	defer cancel()

	msgInfo := &types.MessageInfo{
		MessageSource: types.MessageSource{
			Chat:     chat,
			Sender:   sender,
			IsFromMe: isFromMe,
			IsGroup:  chat.Server == types.GroupServer,
		},
		ID: types.MessageID(job.MessageID),
	}
	err = cli.SendMediaRetryReceipt(ctx, msgInfo, job.MediaKey)

	var altChat types.JID
	var altErr error
	if err != nil {
		altChat = resolveAlternateChatJID(ctx, cli, chat)
		if !altChat.IsEmpty() && altChat.String() != chat.String() {
			altInfo := &types.MessageInfo{
				MessageSource: types.MessageSource{
					Chat:     altChat,
					Sender:   sender,
					IsFromMe: isFromMe,
					IsGroup:  altChat.Server == types.GroupServer,
				},
				ID: types.MessageID(job.MessageID),
			}
			altErr = cli.SendMediaRetryReceipt(ctx, altInfo, job.MediaKey)
		}
	}

	emitJSON("BRIDGE_LOG", map[string]any{
		"level":  "INFO",
		"module": "MediaRetry",
		"msg":    fmt.Sprintf("sent receipt id=%s chat=%s alt=%s fromMe=%v err=%v altErr=%v", job.MessageID, chat.String(), altChat.String(), isFromMe, err, altErr),
	})
}

func processMediaRetry(evt *events.MediaRetry) {
	if evt == nil || evt.MessageID == "" {
		return
	}
	msgID := string(evt.MessageID)
	job, ok := lookupMediaJob(msgID)
	if !ok || job == nil || len(job.MediaKey) == 0 {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "MediaRetry",
			"msg":    fmt.Sprintf("response ignored id=%s (jobFound=%v)", msgID, ok),
		})
		return
	}

	retryData, err := whatsmeow.DecryptMediaRetryNotification(evt, job.MediaKey)
	if err != nil {
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "MediaRetry",
			"msg":    fmt.Sprintf("decrypt failed id=%s err=%v evtErr=%v", msgID, err, evt.Error),
		})
		return
	}

	res := retryData.GetResult()
	newPath := retryData.GetDirectPath()
	emitJSON("BRIDGE_LOG", map[string]any{
		"level":  "INFO",
		"module": "MediaRetry",
		"msg":    fmt.Sprintf("response id=%s result=%v hasNewPath=%v", msgID, res, newPath != ""),
	})

	if res != waMmsRetry.MediaRetryNotification_SUCCESS || newPath == "" {
		if job.IsViewOnce {
			emitViewOnceExpired(job, "Opened or expired")
		} else {
			emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", "Media unavailable on primary device")
		}
		return
	}

	job.DirectPath = newPath
	job.Msg = nil
	saveMediaMeta(job)

	mu.Lock()
	cli := client
	filesDir := appFilesDir
	mu.Unlock()
	if cli == nil || filesDir == "" {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), mediaDownloadTimeout)
	defer cancel()
	downloadAndEmitMedia(cli, ctx, filesDir, job)
}

func hashFileSHA256(path string) (string, int64, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", 0, err
	}
	defer f.Close()
	h := sha256.New()
	n, err := io.Copy(h, f)
	if err != nil {
		return "", n, err
	}
	return hex.EncodeToString(h.Sum(nil)), n, nil
}

func moveOrCopyTempFile(tmpPath, targetPath string) error {
	if err := os.Rename(tmpPath, targetPath); err == nil {
		return nil
	}
	src, err := os.Open(tmpPath)
	if err != nil {
		return err
	}
	defer src.Close()
	dst, err := os.OpenFile(targetPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(dst, src)
	closeErr := dst.Close()
	if copyErr != nil {
		_ = os.Remove(targetPath)
		return copyErr
	}
	if closeErr != nil {
		_ = os.Remove(targetPath)
		return closeErr
	}
	_ = os.Remove(tmpPath)
	return nil
}

func downloadAndEmitMedia(cli *whatsmeow.Client, ctx context.Context, filesDir string, job *mediaJob) bool {
	if cli == nil || job == nil || job.MessageID == "" || filesDir == "" {
		return false
	}
	if job.IsViewOnce {
		emitViewOnceExpired(job, "Open in original WhatsApp")
		return false
	}
	if _, busy := mediaInFlight.LoadOrStore(job.MessageID, true); busy {
		return false
	}
	defer mediaInFlight.Delete(job.MessageID)

	if job.FileLength > maxManualMediaBytes {
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", "file exceeds 128 MB safety limit")
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "MediaDownload",
			"msg":    fmt.Sprintf("blocked oversized id=%s type=%s bytes=%d", job.MessageID, job.MediaType, job.FileLength),
		})
		return false
	}

	tmpDir := filepath.Join(filesDir, "media", "tmp")
	if err := os.MkdirAll(tmpDir, 0o755); err != nil {
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", err.Error())
		return false
	}
	tmpFile, err := os.CreateTemp(tmpDir, "wupp_dl_*.part")
	if err != nil {
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", err.Error())
		return false
	}
	tmpPath := tmpFile.Name()
	keepTmp := false
	defer func() {
		_ = tmpFile.Close()
		if !keepTmp {
			_ = os.Remove(tmpPath)
		}
	}()

	resetTmp := func() {
		_, _ = tmpFile.Seek(0, 0)
		_ = tmpFile.Truncate(0)
	}

	if strings.HasSuffix(job.ChatJID, "@newsletter") && len(job.MediaKey) == 0 && job.DirectPath != "" {
		err = cli.DownloadMediaWithOnlyPathToFile(ctx, job.DirectPath, tmpFile)
		if err != nil && job.Msg != nil {
			resetTmp()
			err = cli.DownloadToFile(ctx, job.Msg, tmpFile)
		}
	} else if job.Msg != nil {
		err = cli.DownloadToFile(ctx, job.Msg, tmpFile)
		if err != nil && len(job.MediaKey) == 0 && job.DirectPath != "" {
			resetTmp()
			err = cli.DownloadMediaWithOnlyPathToFile(ctx, job.DirectPath, tmpFile)
		}
	} else if job.DirectPath != "" && len(job.MediaKey) > 0 {
		err = cli.DownloadMediaWithPathToFile(
			ctx,
			job.DirectPath,
			job.FileEncSHA256,
			job.FileSHA256,
			job.MediaKey,
			waMediaTypeFromString(job.MediaType),
			"",
			len(job.FileSHA256) == 0,
			tmpFile,
		)
	} else if job.DirectPath != "" && strings.HasSuffix(job.ChatJID, "@newsletter") {
		err = cli.DownloadMediaWithOnlyPathToFile(ctx, job.DirectPath, tmpFile)
	} else {
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", "missing media path")
		return false
	}

	_ = tmpFile.Close()
	stat, statErr := os.Stat(tmpPath)
	fileBytes := int64(0)
	if statErr == nil {
		fileBytes = stat.Size()
	}
	if err != nil || fileBytes <= 0 {
		errReason := "empty download"
		if err != nil {
			errReason = err.Error()
		}
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "MediaDownload",
			"msg":    fmt.Sprintf("failed id=%s type=%s vo=%v err=%v bytes=%d", job.MessageID, job.MediaType, job.IsViewOnce, err, fileBytes),
		})
		isNotFound := isMediaNotFoundOrExpired(err) || (err == nil && fileBytes <= 0)
		if job.IsViewOnce && isNotFound {
			if job.IsFromMe || job.IsHistorical {
				emitViewOnceExpired(job, "Already opened or expired")
				return false
			}
			if _, retried := mediaRetrySent.Load(job.MessageID); retried {
				emitViewOnceExpired(job, "Already opened or expired")
				return false
			}
			emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", errReason)
			triggerMediaRetry(cli, job)
			return false
		}
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", errReason)
		if err != nil && len(job.MediaKey) > 0 && isMediaNotFoundOrExpired(err) {
			if job.MetaVer == 0 {
				go requestPeerPlaceholderResendForJob(cli, ctx, job)
			}
			triggerMediaRetry(cli, job)
		}
		return false
	}
	if uint64(fileBytes) > maxManualMediaBytes {
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", "file exceeds 128 MB safety limit")
		return false
	}

	hashHex, hashedLen, hashErr := hashFileSHA256(tmpPath)
	if hashErr != nil || hashedLen <= 0 {
		emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", "hash verification failed")
		return false
	}
	ext := extensionForMime(job.MimeType, job.MediaType)

	var targetPath string
	switch {
	case job.IsViewOnce:
		targetPath = filepath.Join(filesDir, "vault", "viewonce", sanitizeFileName(job.MessageID)+ext)
	case job.IsStatus:
		targetPath = filepath.Join(filesDir, "vault", "status", sanitizeFileName(job.MessageID)+ext)
	default:
		if cached, ok := seenMediaMap.Load(hashHex); ok {
			targetPath = cached.(string)
		} else {
			subDir := filepath.Join(filesDir, "media", strings.ToLower(job.MediaType))
			_ = os.MkdirAll(subDir, 0o755)
			targetPath = filepath.Join(subDir, hashHex[:16]+ext)
		}
	}

	if stTarget, stErr := os.Stat(targetPath); stErr != nil || stTarget.Size() <= 0 {
		if mErr := moveOrCopyTempFile(tmpPath, targetPath); mErr != nil {
			emitMediaStatus(job.MessageID, job.ChatJID, "FAILED", mErr.Error())
			return false
		}
		keepTmp = true
	}
	if !job.IsViewOnce && !job.IsStatus {
		seenMediaMap.Store(hashHex, targetPath)
	}

	waveform := ""
	if (job.MediaType == "PTT" || job.MediaType == "AUDIO") && hashedLen <= int64(autoDownloadVaultMaxBytes) {
		if audioBytes, aErr := os.ReadFile(targetPath); aErr == nil {
			waveform = compute40BarWaveform(audioBytes)
		}
	}

	emitJSON("MEDIA_DOWNLOADED", map[string]any{
		"messageId":    job.MessageID,
		"chatJid":      job.ChatJID,
		"localPath":    targetPath,
		"sha256":       hashHex,
		"fileSize":     hashedLen,
		"isViewOnce":   job.IsViewOnce,
		"isStatus":     job.IsStatus,
		"isHistorical": job.IsHistorical,
		"isFromMe":     job.IsFromMe,
		"mediaType":    job.MediaType,
		"mimeType":     job.MimeType,
		"waveformCsv":  waveform,
	})
	return true
}

func mediaDownloadWorker() {
	for job := range mediaQueue {
		jitterMs := 70 + int(randomFloat()*110)
		time.Sleep(time.Duration(jitterMs) * time.Millisecond)

		mu.Lock()
		cli := client
		ctx := baseCtx
		filesDir := appFilesDir
		mu.Unlock()
		if cli == nil || ctx == nil {
			continue
		}

		dlCtx, cancel := context.WithTimeout(ctx, mediaDownloadTimeout)
		_ = downloadAndEmitMedia(cli, dlCtx, filesDir, &job)
		cancel()
	}
}

func emitJSON(eventType string, payload map[string]any) {
	if eventRing == nil {
		return
	}
	if eventType == "BRIDGE_LOG" && len(eventRing) >= (eventBufferCapacity*3)/4 {
		return
	}
	b, err := json.Marshal(payload)
	if err != nil {
		return
	}
	ev := bridgeEvent{
		EventType: eventType,
		Payload:   string(b),
	}
	select {
	case eventRing <- ev:
	default:
		if eventType == "BRIDGE_LOG" {
			return
		}
		// Ring full: drain one item and push newest so writer never blocks socket loop
		select {
		case <-eventRing:
		default:
		}
		select {
		case eventRing <- ev:
		default:
		}
	}
}

func compute40BarWaveform(data []byte) string {
	const bars = 40
	if len(data) < bars {
		return "25,40,65,50,80,60,45,70,85,55,40,75,90,65,50,35,60,80,70,45,35,55,75,65,50,85,70,45,60,75,55,40,65,80,60,45,70,50,35,25"
	}
	chunk := len(data) / bars
	vals := make([]string, bars)
	for i := 0; i < bars; i++ {
		start := i * chunk
		end := start + chunk
		if end > len(data) {
			end = len(data)
		}
		var energy int
		for j := start; j < end; j++ {
			v := int(data[j]) - 128
			if v < 0 {
				v = -v
			}
			energy += v
		}
		avg := energy / maxInt(end-start, 1)
		pct := 15 + (avg*85)/128
		if pct > 100 {
			pct = 100
		}
		vals[i] = strconv.Itoa(pct)
	}
	return strings.Join(vals, ",")
}

func parseWaveformCsv(csv string) []byte {
	const targetBars = 64
	clean := strings.TrimSpace(csv)
	if clean != "" {
		parts := strings.Split(clean, ",")
		raw := make([]byte, 0, len(parts))
		for _, p := range parts {
			if n, err := strconv.Atoi(strings.TrimSpace(p)); err == nil {
				if n < 0 {
					n = 0
				}
				if n > 100 {
					n = 100
				}
				raw = append(raw, byte(n))
			}
		}
		if len(raw) == targetBars {
			return raw
		}
		if len(raw) > 0 {
			out := make([]byte, targetBars)
			for i := 0; i < targetBars; i++ {
				srcIdx := (i * len(raw)) / targetBars
				out[i] = raw[srcIdx]
			}
			return out
		}
	}
	// Fallback to a clean 64-bar speech waveform so WhatsApp renders the native waveform bubble
	def := make([]byte, targetBars)
	for i := range def {
		def[i] = byte(15 + ((i * 7) % 35) + ((i % 4) * 8))
	}
	return def
}

func extensionForMime(mime, mediaType string) string {
	m := strings.ToLower(strings.TrimSpace(mime))
	switch {
	case strings.Contains(m, "jpeg"), strings.Contains(m, "jpg"):
		return ".jpg"
	case strings.Contains(m, "png"):
		return ".png"
	case strings.Contains(m, "webp"):
		return ".webp"
	case strings.Contains(m, "mp4"):
		return ".mp4"
	case strings.Contains(m, "ogg"), strings.Contains(m, "opus"):
		return ".ogg"
	case strings.Contains(m, "mpeg"), strings.Contains(m, "mp3"):
		return ".mp3"
	case strings.Contains(m, "m4a"), strings.Contains(m, "aac"):
		return ".m4a"
	case strings.Contains(m, "pdf"):
		return ".pdf"
	case strings.Contains(m, "android.package-archive"):
		return ".apk"
	case strings.Contains(m, "wordprocessingml"):
		return ".docx"
	case strings.Contains(m, "msword"):
		return ".doc"
	case strings.Contains(m, "spreadsheetml"):
		return ".xlsx"
	case strings.Contains(m, "ms-excel"):
		return ".xls"
	case strings.Contains(m, "presentationml"):
		return ".pptx"
	case strings.Contains(m, "ms-powerpoint"):
		return ".ppt"
	case strings.Contains(m, "x-rar"), strings.Contains(m, "vnd.rar"):
		return ".rar"
	case strings.Contains(m, "7z"):
		return ".7z"
	case strings.Contains(m, "zip"):
		return ".zip"
	case strings.Contains(m, "text/csv"):
		return ".csv"
	case strings.Contains(m, "application/json"):
		return ".json"
	case strings.HasPrefix(m, "text/"):
		return ".txt"
	case mediaType == "IMAGE":
		return ".jpg"
	case mediaType == "VIDEO":
		return ".mp4"
	case mediaType == "PTT", mediaType == "AUDIO":
		return ".ogg"
	case mediaType == "STICKER":
		return ".webp"
	default:
		return ".bin"
	}
}

func sanitizeFileName(s string) string {
	replacer := strings.NewReplacer("/", "_", "\\", "_", ":", "_", "@", "_", " ", "_")
	return replacer.Replace(s)
}

func safeJID(jid *types.JID) string {
	if jid == nil {
		return ""
	}
	return jid.String()
}

func randomFloat() float64 {
	rngMu.Lock()
	defer rngMu.Unlock()
	return rng.Float64()
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func formatCallDuration(secs int64) string {
	if secs <= 0 {
		return ""
	}
	if secs < 60 {
		return fmt.Sprintf("%d sec", secs)
	}
	return fmt.Sprintf("%d min", (secs+30)/60)
}

func normalizeTimestampMs(ts uint64) int64 {
	if ts == 0 {
		return time.Now().UnixMilli()
	}
	if ts < 1_000_000_000_000 {
		return int64(ts) * 1000
	}
	return int64(ts)
}

func normalizeUserJID(cli *whatsmeow.Client, rawJID string) string {
	rawJID = strings.TrimSpace(rawJID)
	if rawJID == "" {
		return ""
	}
	j, err := types.ParseJID(rawJID)
	if err != nil {
		return rawJID
	}
	return j.ToNonAD().String()
}

func callStubDetails(stub waWeb.WebMessageInfo_StubType) (title, subtitle string, ok bool) {
	switch stub {
	case waWeb.WebMessageInfo_CALL_MISSED_VOICE, waWeb.WebMessageInfo_CALL_MISSED_GROUP_VOICE:
		return "Voice call", "No answer", true
	case waWeb.WebMessageInfo_CALL_MISSED_VIDEO, waWeb.WebMessageInfo_CALL_MISSED_GROUP_VIDEO:
		return "Video call", "No answer", true
	default:
		return "", "", false
	}
}

func parseCallStubDuration(params []string) int {
	for _, p := range params {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		if secs, err := strconv.Atoi(p); err == nil && secs > 0 && secs < 86400 {
			return secs
		}
	}
	return 0
}

func callRecordSubtitle(res waSyncAction.CallLogRecord_CallResult, dur int64) string {
	if dur > 0 {
		return formatCallDuration(dur)
	}
	switch res {
	case waSyncAction.CallLogRecord_CONNECTED:
		return "Answered"
	case waSyncAction.CallLogRecord_REJECTED:
		return "Declined"
	case waSyncAction.CallLogRecord_CANCELLED:
		return "Cancelled"
	default:
		return "No answer"
	}
}

func pickCallRecordTargetJID(cli *whatsmeow.Client, rec *waSyncAction.CallLogRecord) string {
	if rec == nil {
		return ""
	}
	if gj := strings.TrimSpace(rec.GetGroupJID()); gj != "" {
		return gj
	}
	var ownUser, ownLID string
	if cli != nil && cli.Store != nil {
		if cli.Store.ID != nil {
			ownUser = cli.Store.ID.User
		}
		ownLID = cli.Store.LID.User
	}
	for _, p := range rec.GetParticipants() {
		if p == nil {
			continue
		}
		uj := strings.TrimSpace(p.GetUserJID())
		if uj == "" {
			continue
		}
		if parsed, err := types.ParseJID(uj); err == nil {
			if (ownUser != "" && parsed.User == ownUser) || (ownLID != "" && parsed.User == ownLID) {
				continue
			}
		}
		return uj
	}
	return strings.TrimSpace(rec.GetCallCreatorJID())
}

func formatNewsletterReactionSummary(reactions map[string]int, forwards int) string {
	parts := make([]string, 0, 5)
	if forwards > 0 {
		parts = append(parts, fmt.Sprintf("↪ %d", forwards))
	}
	if len(reactions) > 0 {
		type rc struct {
			emoji string
			count int
		}
		items := make([]rc, 0, len(reactions))
		for k, v := range reactions {
			if k != "" && v > 0 {
				items = append(items, rc{emoji: k, count: v})
			}
		}
		sort.Slice(items, func(i, j int) bool {
			if items[i].count != items[j].count {
				return items[i].count > items[j].count
			}
			return items[i].emoji < items[j].emoji
		})
		limit := len(items)
		if limit > 3 {
			limit = 3
		}
		for i := 0; i < limit; i++ {
			parts = append(parts, fmt.Sprintf("%s %d", items[i].emoji, items[i].count))
		}
	}
	return strings.Join(parts, "  ·  ")
}

func emitNewsletterMessages(cli *whatsmeow.Client, chJID types.JID, count int) {
	if cli == nil || chJID.IsEmpty() || !cli.IsConnected() {
		return
	}
	if count <= 0 {
		count = 25
	}
	ctx, cancel := context.WithTimeout(baseCtx, 20*time.Second)
	msgs, err := cli.GetNewsletterMessages(ctx, chJID, &whatsmeow.GetNewsletterMessagesParams{
		Count: count,
	})
	cancel()
	if err != nil {
		lower := strings.ToLower(err.Error())
		if strings.Contains(lower, "404") || strings.Contains(lower, "item-not-found") {
			deadNewsletters.Store(chJID.String(), true)
		}
		emitJSON("BRIDGE_LOG", map[string]any{
			"level":  "WARN",
			"module": "NewsletterMsgs",
			"msg":    fmt.Sprintf("GetNewsletterMessages(%s) err=%v", chJID.String(), err),
		})
		return
	}
	for _, nm := range msgs {
		if nm == nil || nm.Message == nil {
			continue
		}
		inner, isVO, isEph, ephSec, _ := unwrapMessageContainer(nm.Message)
		if inner == nil {
			continue
		}
		msgID := string(nm.MessageID)
		if msgID == "" && nm.MessageServerID > 0 {
			msgID = fmt.Sprintf("nl_%s_%d", chJID.User, nm.MessageServerID)
		}
		if msgID == "" {
			continue
		}
		ts := nm.Timestamp
		if ts.IsZero() {
			ts = time.Now()
		}
		info := &types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:     chJID,
				Sender:   chJID,
				IsFromMe: false,
				IsGroup:  false,
			},
			ID:        types.MessageID(msgID),
			Timestamp: ts,
		}
		payload, job := buildNormalizedMessage(info, inner, isVO, isEph, ephSec)
		mt, _ := payload["msgType"].(string)
		bd, _ := payload["body"].(string)
		if !isVO && job == nil && mt == "UNKNOWN" && strings.TrimSpace(bd) == "" {
			continue
		}
		payload["reactionSummary"] = formatNewsletterReactionSummary(nm.ReactionCounts, nm.ForwardsCount)
		payload["isSync"] = true
		emitJSON("MESSAGE_UPSERT", payload)
		if shouldAutoDownloadMedia(job) {
			enqueueMedia(job)
		}
	}
}

// FetchNewsletterMessages syncs the latest channel posts for the given newsletter JID.
func FetchNewsletterMessages(channelJid string) error {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	parsed, err := types.ParseJID(strings.TrimSpace(channelJid))
	if err != nil || parsed.Server != types.NewsletterServer {
		return fmt.Errorf("invalid newsletter jid: %s", channelJid)
	}
	go emitNewsletterMessages(cli, parsed, 30)
	return nil
}

// ToggleNewsletterMute mutes or unmutes a subscribed WhatsApp Channel or regular chat/group via AppState.
func ToggleNewsletterMute(channelJid string, muted bool) error {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	parsed, err := types.ParseJID(strings.TrimSpace(channelJid))
	if err != nil || parsed.IsEmpty() {
		return fmt.Errorf("invalid jid: %s", channelJid)
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	if parsed.Server == types.NewsletterServer {
		if err := cli.NewsletterToggleMute(ctx, parsed, muted); err != nil {
			return err
		}
		emitJSON("CHAT_META_UPDATE", map[string]any{
			"kind":    "MUTE",
			"jid":     parsed.String(),
			"muted":   muted,
			"muteEnd": int64(0),
		})
		return nil
	}
	canon, alt := canonicalizeDMChatJID(ctx, cli, parsed)
	if canon.IsEmpty() {
		canon = parsed
	}
	muteDur := 365 * 24 * time.Hour
	appErr := cli.SendAppState(ctx, appstate.BuildMute(canon, muted, muteDur))
	if !alt.IsEmpty() && alt != canon {
		_ = cli.SendAppState(ctx, appstate.BuildMute(alt, muted, muteDur))
	}
	if appErr != nil {
		return appErr
	}
	var muteEndMs int64
	if muted {
		muteEndMs = time.Now().Add(muteDur).UnixMilli()
	}
	emitJSON("CHAT_META_UPDATE", map[string]any{
		"kind":    "MUTE",
		"jid":     canon.String(),
		"altJid":  alt.String(),
		"muted":   muted,
		"muteEnd": muteEndMs,
	})
	return nil
}

func emitChatRangeMetaUpdate(kind string, jid types.JID, msgRange *waSyncAction.SyncActionMessageRange, deleteMedia bool, fromFullSync bool, ts time.Time) {
	mu.Lock()
	cCli := client
	cCtx := baseCtx
	mu.Unlock()
	var lastMsgTsMs int64
	if msgRange != nil {
		lastMsgTsMs = msgRange.GetLastMessageTimestamp() * 1000
	}
	alt := resolveAlternateChatJID(cCtx, cCli, jid)
	emitJSON("CHAT_META_UPDATE", map[string]any{
		"kind":                   kind,
		"jid":                    jid.String(),
		"altJid":                 alt.String(),
		"deleteMedia":            deleteMedia,
		"fromFullSync":           fromFullSync,
		"lastMessageTimestampMs": lastMsgTsMs,
		"timestamp":              ts.UnixMilli(),
	})
}

func buildDeleteChatMessageKey(target types.JID, messageID string, fromMe bool, participantJID string) *waCommon.MessageKey {
	id := strings.TrimSpace(messageID)
	if id == "" {
		return nil
	}
	key := &waCommon.MessageKey{
		RemoteJID: proto.String(target.String()),
		FromMe:    proto.Bool(fromMe),
		ID:        proto.String(id),
	}
	if target.Server == types.GroupServer && !fromMe {
		pStr := strings.TrimSpace(participantJID)
		if pStr != "" {
			if parsedP, err := types.ParseJID(pStr); err == nil && !parsedP.IsEmpty() {
				key.Participant = proto.String(parsedP.ToNonAD().String())
			} else {
				key.Participant = proto.String(pStr)
			}
		}
	}
	return key
}

// DeleteChatRemote pushes an AppState DeleteChat mutation to WhatsApp so the chat
// is also deleted on the primary phone and linked companion devices.
func DeleteChatRemote(chatJid string, lastMsgId string, lastMsgFromMe bool, lastMsgParticipant string, lastMsgTimestampMs int64, deleteMedia bool) error {
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil {
		return errors.New("client not initialized")
	}
	if cli.Store == nil || cli.Store.ID == nil {
		return errors.New("not paired")
	}
	chat, err := types.ParseJID(strings.TrimSpace(chatJid))
	if err != nil || chat.IsEmpty() {
		return fmt.Errorf("invalid chat jid: %s", chatJid)
	}
	if chat.Server != types.DefaultUserServer && chat.Server != types.HiddenUserServer && chat.Server != types.GroupServer {
		return nil
	}
	if !ensureConnectedAndAuthenticated(cli, 6*time.Second) {
		return errors.New("not connected to WhatsApp")
	}
	if ctx == nil {
		ctx = context.Background()
	}
	lastTs := time.Now()
	if lastMsgTimestampMs > 0 {
		lastTs = time.UnixMilli(lastMsgTimestampMs)
	}
	sendCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()

	altChat := resolveAlternateChatJID(sendCtx, cli, chat)
	key := buildDeleteChatMessageKey(chat, lastMsgId, lastMsgFromMe, lastMsgParticipant)
	appErr := cli.SendAppState(sendCtx, appstate.BuildDeleteChat(chat, lastTs, key, deleteMedia))

	var altAppErr error
	if !altChat.IsEmpty() && (chat.Server == types.DefaultUserServer || chat.Server == types.HiddenUserServer) {
		altKey := buildDeleteChatMessageKey(altChat, lastMsgId, lastMsgFromMe, lastMsgParticipant)
		altAppErr = cli.SendAppState(sendCtx, appstate.BuildDeleteChat(altChat, lastTs, altKey, deleteMedia))
	}

	emitJSON("BRIDGE_LOG", map[string]any{
		"level":  "INFO",
		"module": "DeleteChat",
		"msg":    fmt.Sprintf("chat=%s alt=%s msgId=%s err=%v altErr=%v", chat.String(), altChat.String(), strings.TrimSpace(lastMsgId), appErr, altAppErr),
	})

	if appErr != nil && (altChat.IsEmpty() || altAppErr != nil) {
		return appErr
	}
	return nil
}

func isOwnGroupAdmin(cli *whatsmeow.Client, info *types.GroupInfo) bool {
	if cli == nil || cli.Store == nil || info == nil {
		return false
	}
	var ownUser, ownLID string
	if cli.Store.ID != nil {
		ownUser = cli.Store.ID.User
	}
	ownLID = cli.Store.LID.User
	for _, p := range info.Participants {
		isMe := (ownUser != "" && p.JID.User == ownUser) ||
			(ownLID != "" && p.JID.User == ownLID) ||
			(ownUser != "" && !p.PhoneNumber.IsEmpty() && p.PhoneNumber.User == ownUser) ||
			(ownLID != "" && !p.LID.IsEmpty() && p.LID.User == ownLID)
		if isMe {
			return p.IsAdmin || p.IsSuperAdmin
		}
	}
	if !info.OwnerJID.IsEmpty() && ((ownUser != "" && info.OwnerJID.User == ownUser) || (ownLID != "" && info.OwnerJID.User == ownLID)) {
		return true
	}
	if !info.OwnerPN.IsEmpty() && ownUser != "" && info.OwnerPN.User == ownUser {
		return true
	}
	return false
}

func deliveryStatusFromWebMsg(webMsg *waWeb.WebMessageInfo, fromMe bool) string {
	if webMsg == nil || webMsg.Status == nil {
		if fromMe {
			return "SENT"
		}
		return "DELIVERED"
	}
	switch webMsg.GetStatus() {
	case waWeb.WebMessageInfo_PLAYED:
		return "PLAYED"
	case waWeb.WebMessageInfo_READ:
		return "READ"
	case waWeb.WebMessageInfo_DELIVERY_ACK:
		return "DELIVERED"
	case waWeb.WebMessageInfo_SERVER_ACK:
		return "SENT"
	case waWeb.WebMessageInfo_PENDING, waWeb.WebMessageInfo_ERROR:
		return "PENDING"
	default:
		if fromMe {
			return "SENT"
		}
		return "DELIVERED"
	}
}

func groupStubDetails(webMsg *waWeb.WebMessageInfo) (string, bool) {
	if webMsg == nil {
		return "", false
	}
	params := webMsg.GetMessageStubParameters()
	firstParam := ""
	if len(params) > 0 {
		firstParam = strings.TrimSpace(params[0])
	}
	switch webMsg.GetMessageStubType() {
	case waWeb.WebMessageInfo_GROUP_CHANGE_ANNOUNCE:
		if strings.EqualFold(firstParam, "off") || strings.EqualFold(firstParam, "false") {
			return "Group settings were changed to allow all members to send messages", true
		}
		return "Admin changed this group's settings to allow only admins to send messages", true
	case waWeb.WebMessageInfo_GROUP_CHANGE_RESTRICT:
		return "Group info permissions were updated", true
	case waWeb.WebMessageInfo_GROUP_CREATE:
		if firstParam != "" {
			return "Created group \"" + firstParam + "\"", true
		}
		return "Group created", true
	case waWeb.WebMessageInfo_GROUP_CHANGE_SUBJECT:
		if firstParam != "" {
			return "Changed the group name to \"" + firstParam + "\"", true
		}
		return "Group name was changed", true
	case waWeb.WebMessageInfo_GROUP_CHANGE_DESCRIPTION:
		return "Group description was updated", true
	case waWeb.WebMessageInfo_GROUP_PARTICIPANT_ADD, waWeb.WebMessageInfo_GROUP_PARTICIPANT_INVITE:
		return "Added participant(s) to the group", true
	case waWeb.WebMessageInfo_GROUP_PARTICIPANT_REMOVE:
		return "Removed participant(s) from the group", true
	case waWeb.WebMessageInfo_GROUP_PARTICIPANT_LEAVE:
		return "Left the group", true
	case waWeb.WebMessageInfo_GROUP_PARTICIPANT_PROMOTE:
		return "Promoted a member to admin", true
	case waWeb.WebMessageInfo_GROUP_PARTICIPANT_DEMOTE:
		return "Dismissed an admin", true
	case waWeb.WebMessageInfo_GROUP_ANNOUNCE_MODE_MESSAGE_BOUNCE:
		return "Only admins can send messages", true
	default:
		return "", false
	}
}

// FetchGroupLockState queries group info to emit whether only admins can post in this group.
func FetchGroupLockState(groupJid string) error {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	parsed, err := types.ParseJID(strings.TrimSpace(groupJid))
	if err != nil || parsed.Server != types.GroupServer {
		return fmt.Errorf("invalid group jid: %s", groupJid)
	}
	go func() {
		ctx, cancel := context.WithTimeout(baseCtx, 10*time.Second)
		info, gErr := cli.GetGroupInfo(ctx, parsed)
		cancel()
		if gErr == nil && info != nil {
			emitJSON("CHAT_META_UPDATE", map[string]any{
				"kind":   "GROUP_ANNOUNCE",
				"jid":    parsed.String(),
				"locked": info.IsAnnounce && !isOwnGroupAdmin(cli, info),
			})
		}
	}()
	return nil
}

// SendReaction sends or removes (empty emoji) a reaction on a target message.
func SendReaction(chatJid, targetMessageId, senderJid string, fromMe bool, emoji string) error {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	chatParsed, err := types.ParseJID(strings.TrimSpace(chatJid))
	if err != nil {
		return fmt.Errorf("invalid chat jid: %s", chatJid)
	}
	var targetSender types.JID
	if !fromMe {
		if strings.TrimSpace(senderJid) != "" {
			if sp, sErr := types.ParseJID(strings.TrimSpace(senderJid)); sErr == nil {
				targetSender = sp
			}
		}
		if targetSender.IsEmpty() {
			targetSender = chatParsed
		}
	}
	msg := cli.BuildReaction(chatParsed, targetSender, types.MessageID(targetMessageId), emoji)
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	dest := chatParsed
	if chatParsed == types.StatusBroadcastJID || chatParsed.Server == types.BroadcastServer {
		dest = targetSender
	}
	_, sErr := cli.SendMessage(ctx, dest, msg)
	return sErr
}

// SendStatusReply sends a text reply to a status author quoting their status@broadcast stanza.
func SendStatusReply(
	recipientJid string,
	statusId string,
	statusSenderJid string,
	statusCaption string,
	text string,
	ghostTyping bool,
) (string, error) {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "", errors.New("client not connected")
	}
	rJid, err := types.ParseJID(strings.TrimSpace(recipientJid))
	if err != nil {
		return "", fmt.Errorf("invalid recipient JID: %w", err)
	}

	if !ghostTyping && cli.IsConnected() {
		_ = cli.SendChatPresence(ctx, rJid, types.ChatPresenceComposing, types.ChatPresenceMediaText)
		baseMs := len([]rune(text)) * 35
		if baseMs > 2500 {
			baseMs = 2500
		}
		if baseMs < 250 {
			baseMs = 250
		}
		jitterFactor := 0.8 + randomFloat()*0.4
		time.Sleep(time.Duration(float64(baseMs)*jitterFactor) * time.Millisecond)
		_ = cli.SendChatPresence(ctx, rJid, types.ChatPresencePaused, types.ChatPresenceMediaText)
	}

	ctxInfo := &waE2E.ContextInfo{
		StanzaID:  proto.String(strings.TrimSpace(statusId)),
		RemoteJID: proto.String(types.StatusBroadcastJID.String()),
	}
	if strings.TrimSpace(statusSenderJid) != "" {
		if sParsed, sErr := types.ParseJID(strings.TrimSpace(statusSenderJid)); sErr == nil {
			ctxInfo.Participant = proto.String(sParsed.ToNonAD().String())
		} else {
			ctxInfo.Participant = proto.String(strings.TrimSpace(statusSenderJid))
		}
	} else {
		ctxInfo.Participant = proto.String(rJid.ToNonAD().String())
	}
	cleanCaption := strings.TrimSpace(statusCaption)
	if cleanCaption == "" {
		cleanCaption = "Status"
	}
	ctxInfo.QuotedMessage = &waE2E.Message{
		Conversation: proto.String(cleanCaption),
	}

	msg := &waE2E.Message{
		ExtendedTextMessage: &waE2E.ExtendedTextMessage{
			Text:        proto.String(text),
			ContextInfo: ctxInfo,
		},
	}

	sendCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	resp, err := cli.SendMessage(sendCtx, rJid, msg)
	if err != nil {
		return "", err
	}
	return string(resp.ID), nil
}

// SendStatusReaction sends an emoji reaction to a contact's status broadcast.
func SendStatusReaction(
	statusSenderJid string,
	statusMessageId string,
	emoji string,
) error {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	ctx := baseCtx
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	senderParsed, err := types.ParseJID(strings.TrimSpace(statusSenderJid))
	if err != nil {
		return fmt.Errorf("invalid sender JID: %w", err)
	}
	nonAD := senderParsed.ToNonAD()

	statusKey := &waCommon.MessageKey{
		RemoteJID:   proto.String(types.StatusBroadcastJID.String()),
		FromMe:      proto.Bool(false),
		ID:          proto.String(strings.TrimSpace(statusMessageId)),
		Participant: proto.String(nonAD.String()),
	}
	msg := &waE2E.Message{
		ReactionMessage: &waE2E.ReactionMessage{
			Key:               statusKey,
			Text:              proto.String(emoji),
			SenderTimestampMS: proto.Int64(time.Now().UnixMilli()),
		},
	}
	sendCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	_, sErr := cli.SendMessage(sendCtx, nonAD, msg)
	return sErr
}

// GetOwnProfileJson returns the connected WhatsApp account's phone, push name, and About text.
func GetOwnProfileJson() (string, error) {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || cli.Store == nil || cli.Store.ID == nil {
		return "", errors.New("client not logged in")
	}
	own := cli.Store.ID.ToNonAD()
	pushName := strings.TrimSpace(cli.Store.PushName)
	about := ""
	if cli.IsConnected() {
		ctx, cancel := context.WithTimeout(baseCtx, 10*time.Second)
		if infoMap, err := cli.GetUserInfo(ctx, []types.JID{own}); err == nil {
			if info, ok := infoMap[own]; ok {
				about = strings.TrimSpace(info.Status)
			}
		}
		cancel()
	}
	payload := map[string]any{
		"ownJid":   own.String(),
		"phone":    "+" + own.User,
		"pushName": pushName,
		"about":    about,
	}
	b, _ := json.Marshal(payload)
	return string(b), nil
}

// SetOwnPushName updates the user's WhatsApp display push name via App State sync.
func SetOwnPushName(name string) error {
	clean := strings.TrimSpace(name)
	if clean == "" {
		return errors.New("display name cannot be empty")
	}
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	if err := cli.SendAppState(ctx, appstate.BuildSettingPushName(clean)); err != nil {
		return err
	}
	if cli.Store != nil {
		cli.Store.PushName = clean
	}
	return nil
}

// SetOwnAbout updates the user's WhatsApp profile "About" text.
func SetOwnAbout(text string) error {
	clean := strings.TrimSpace(text)
	if clean == "" {
		return errors.New("about text cannot be empty")
	}
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	return cli.SetStatusMessage(ctx, types.SetStatusInput{
		Text: &clean,
	})
}

func serializePrivacySettings(s *types.PrivacySettings) string {
	payload := map[string]string{
		"lastSeen":     string(s.LastSeen),
		"online":       string(s.Online),
		"profile":      string(s.Profile),
		"status":       string(s.Status),
		"groupAdd":     string(s.GroupAdd),
		"readReceipts": string(s.ReadReceipts),
		"callAdd":      string(s.CallAdd),
		"messages":     string(s.Messages),
		"defense":      string(s.Defense),
	}
	b, _ := json.Marshal(payload)
	return string(b)
}

// GetAccountPrivacySettingsJson fetches the user's synced WhatsApp server privacy settings.
func GetAccountPrivacySettingsJson() (string, error) {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "", errors.New("client not connected")
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	settings, err := cli.TryFetchPrivacySettings(ctx, true)
	if err != nil {
		return "", err
	}
	return serializePrivacySettings(settings), nil
}

// SetAccountPrivacySetting updates a WhatsApp server privacy category and returns updated JSON.
func SetAccountPrivacySetting(category string, value string) (string, error) {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "", errors.New("client not connected")
	}
	cat := types.PrivacySettingType(strings.TrimSpace(category))
	val := types.PrivacySetting(strings.TrimSpace(value))
	switch cat {
	case types.PrivacySettingTypeGroupAdd,
		types.PrivacySettingTypeLastSeen,
		types.PrivacySettingTypeStatus,
		types.PrivacySettingTypeProfile,
		types.PrivacySettingTypeReadReceipts,
		types.PrivacySettingTypeOnline,
		types.PrivacySettingTypeCallAdd,
		types.PrivacySettingTypeMessages,
		types.PrivacySettingTypeDefense:
	default:
		return "", fmt.Errorf("unsupported privacy category: %s", category)
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	updated, err := cli.SetPrivacySetting(ctx, cat, val)
	if err != nil {
		return "", err
	}
	return serializePrivacySettings(&updated), nil
}

// SetDefaultDisappearingTimerSec updates the account-wide default disappearing message duration.
func SetDefaultDisappearingTimerSec(seconds int64) error {
	if seconds < 0 {
		seconds = 0
	}
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	return cli.SetDefaultDisappearingTimer(ctx, time.Duration(seconds)*time.Second)
}

// GetBlocklistJson returns the user's blocked contacts resolved with local names when known.
func GetBlocklistJson() (string, error) {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return "", errors.New("client not connected")
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	bl, err := cli.GetBlocklist(ctx, "")
	if err != nil {
		return "", err
	}
	type blockedEntry struct {
		JID   string `json:"jid"`
		PN    string `json:"pn"`
		LID   string `json:"lid"`
		Name  string `json:"name"`
		Phone string `json:"phone"`
	}
	entries := make([]blockedEntry, 0, len(bl.Items))
	for _, item := range bl.Items {
		target := item.PN
		if target.IsEmpty() {
			target = item.LID
		}
		if target.IsEmpty() {
			continue
		}
		phone := ""
		if !item.PN.IsEmpty() && item.PN.Server == types.DefaultUserServer {
			phone = "+" + item.PN.User
		}
		name := ""
		if cli.Store != nil && cli.Store.Contacts != nil {
			if c, cErr := cli.Store.Contacts.GetContact(ctx, target); cErr == nil && c.Found {
				if strings.TrimSpace(c.FullName) != "" {
					name = strings.TrimSpace(c.FullName)
				} else if strings.TrimSpace(c.PushName) != "" {
					name = strings.TrimSpace(c.PushName)
				}
			}
		}
		if name == "" {
			if phone != "" {
				name = phone
			} else {
				name = target.User
			}
		}
		entries = append(entries, blockedEntry{
			JID:   target.String(),
			PN:    item.PN.String(),
			LID:   item.LID.String(),
			Name:  name,
			Phone: phone,
		})
	}
	sort.Slice(entries, func(i, j int) bool {
		return strings.ToLower(entries[i].Name) < strings.ToLower(entries[j].Name)
	})
	b, _ := json.Marshal(entries)
	return string(b), nil
}

// SetContactBlocked blocks or unblocks a contact JID via WhatsApp server blocklist.
func SetContactBlocked(jidStr string, blocked bool) error {
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	target, err := types.ParseJID(strings.TrimSpace(jidStr))
	if err != nil {
		return fmt.Errorf("invalid jid: %s", jidStr)
	}
	action := events.BlocklistChangeActionUnblock
	if blocked {
		action = events.BlocklistChangeActionBlock
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	_, uErr := cli.UpdateBlocklist(ctx, target, action, "")
	return uErr
}

// PinChatMessage pins or unpins a target message in a chat with durationSec (e.g. 86400, 604800, 2592000).
func PinChatMessage(
	chatJid string,
	targetMessageId string,
	fromMe bool,
	senderJid string,
	durationSec int64,
	isUnpin bool,
) error {
	unfreezePresenceForActivity()
	defer scheduleRefreezePresence()
	mu.Lock()
	cli := client
	mu.Unlock()
	if cli == nil || !cli.IsConnected() {
		return errors.New("client not connected")
	}
	chatParsed, err := types.ParseJID(strings.TrimSpace(chatJid))
	if err != nil {
		return fmt.Errorf("invalid chat jid: %s", chatJid)
	}
	var targetSender types.JID
	if !fromMe {
		if strings.TrimSpace(senderJid) != "" {
			if sp, sErr := types.ParseJID(strings.TrimSpace(senderJid)); sErr == nil {
				targetSender = sp
			}
		}
		if targetSender.IsEmpty() {
			targetSender = chatParsed
		}
	}
	pinType := waE2E.PinInChatMessage_PIN_FOR_ALL
	if isUnpin {
		pinType = waE2E.PinInChatMessage_UNPIN_FOR_ALL
	}
	msgKey := cli.BuildMessageKey(chatParsed, targetSender, types.MessageID(targetMessageId))
	pinMsg := &waE2E.PinInChatMessage{
		Key:               msgKey,
		Type:              pinType.Enum(),
		SenderTimestampMS: proto.Int64(time.Now().UnixMilli()),
	}
	msg := &waE2E.Message{
		PinInChatMessage: pinMsg,
	}
	if !isUnpin && durationSec > 0 {
		msg.MessageContextInfo = &waE2E.MessageContextInfo{
			MessageAddOnDurationInSecs: proto.Uint32(uint32(durationSec)),
		}
	}
	ctx, cancel := context.WithTimeout(baseCtx, 15*time.Second)
	defer cancel()
	_, sErr := cli.SendMessage(ctx, chatParsed, msg)
	return sErr
}
