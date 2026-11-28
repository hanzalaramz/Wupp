package com.wa.beastclient.domain.contacts

import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.GroupReceiptEntity
import com.wa.beastclient.data.db.MessageReactionEntity
import com.wa.beastclient.data.db.WaDao
import com.wa.beastclient.ui.ResolvedParticipantUi

data class ParticipantDisplayResult(
    val primary: String,
    val secondaryPhone: String = "",
    val canonicalJid: String = "",
    val altJid: String = "",
    val avatarPath: String? = null,
    val isSelf: Boolean = false,
    val isUnresolvedLid: Boolean = false
)

/**
 * Shared, local-only participant & LID-to-name resolver used across Message Info,
 * Reactions, Group Info, Chat bubbles, Mentions, Peek preview, Message History, and Voice Notes.
 */
object ParticipantDisplayResolver {

    fun stripDevice(rawJid: String): String {
        val trimmed = rawJid.trim()
        val atIdx = trimmed.indexOf('@')
        if (atIdx <= 0) return trimmed
        val user = trimmed.substring(0, atIdx).substringBefore(':')
        val server = trimmed.substring(atIdx + 1)
        return if (user.isNotBlank() && server.isNotBlank()) "$user@$server" else trimmed
    }

    fun isSelfParticipant(
        rawJid: String,
        altJid: String = "",
        ownJid: String = "",
        ownLid: String = "",
        lidToPn: Map<String, String> = emptyMap()
    ): Boolean {
        val cleanJid = stripDevice(rawJid)
        val cleanAlt = stripDevice(altJid)
        if (cleanJid == MessageReactionEntity.ME_ACTOR ||
            cleanJid.equals("me@s.whatsapp.net", ignoreCase = true)
        ) {
            return true
        }
        val ownCandidates = buildSet {
            val cOwn = stripDevice(ownJid)
            if (cOwn.isNotBlank()) {
                add(cOwn)
                add(cOwn.substringBefore("@"))
                lidToPn[cOwn]?.let { mapped ->
                    add(mapped)
                    add(mapped.substringBefore("@"))
                }
            }
            val cLid = stripDevice(ownLid)
            if (cLid.isNotBlank()) {
                add(cLid)
                add(cLid.substringBefore("@"))
                lidToPn[cLid]?.let { mapped ->
                    add(mapped)
                    add(mapped.substringBefore("@"))
                }
            }
            if (cOwn.endsWith("@s.whatsapp.net")) {
                for ((lid, pn) in lidToPn) {
                    if (pn == cOwn) {
                        add(lid)
                        add(lid.substringBefore("@"))
                    }
                }
            }
        }
        if (ownCandidates.isEmpty()) return false
        val targets = buildList {
            if (cleanJid.isNotBlank()) {
                add(cleanJid)
                add(cleanJid.substringBefore("@"))
                lidToPn[cleanJid]?.let { mapped ->
                    add(mapped)
                    add(mapped.substringBefore("@"))
                }
            }
            if (cleanAlt.isNotBlank()) {
                add(cleanAlt)
                add(cleanAlt.substringBefore("@"))
            }
        }
        return targets.any { it.isNotBlank() && it in ownCandidates }
    }

    fun lookupMeta(
        rawJid: String,
        participantMeta: Map<String, ResolvedParticipantUi>,
        lidToPn: Map<String, String> = emptyMap()
    ): ResolvedParticipantUi? {
        val clean = stripDevice(rawJid)
        if (clean.isBlank()) return null
        participantMeta[clean]?.let { return it }
        participantMeta[rawJid.trim()]?.let { return it }
        lidToPn[clean]?.let { mapped ->
            participantMeta[mapped]?.let { return it }
        }
        val bare = clean.substringBefore("@")
        if (bare.isNotBlank()) {
            participantMeta["$bare@s.whatsapp.net"]?.let { return it }
            participantMeta["$bare@lid"]?.let { return it }
        }
        return participantMeta.values.firstOrNull { meta ->
            val mAlt = stripDevice(meta.altJid)
            mAlt == clean || (bare.isNotBlank() && mAlt.substringBefore("@") == bare)
        }
    }

    fun resolveCanonicalJid(
        rawJid: String,
        meta: ResolvedParticipantUi? = null,
        lidToPn: Map<String, String> = emptyMap()
    ): String {
        val clean = stripDevice(rawJid)
        if (clean.endsWith("@s.whatsapp.net", ignoreCase = true)) return clean
        val mappedPn = lidToPn[clean]?.let(::stripDevice).orEmpty()
        if (mappedPn.endsWith("@s.whatsapp.net", ignoreCase = true)) return mappedPn
        val metaJid = meta?.jid?.let(::stripDevice).orEmpty()
        if (metaJid.endsWith("@s.whatsapp.net", ignoreCase = true)) return metaJid
        val metaAlt = meta?.altJid?.let(::stripDevice).orEmpty()
        if (metaAlt.endsWith("@s.whatsapp.net", ignoreCase = true)) return metaAlt
        val phoneDigits = meta?.phone.orEmpty().filter(Char::isDigit)
        if (phoneDigits.length >= 7) return "$phoneDigits@s.whatsapp.net"
        return clean.ifBlank { rawJid.trim() }
    }

    fun resolveAltJid(
        rawJid: String,
        canonicalJid: String,
        meta: ResolvedParticipantUi? = null,
        lidToPn: Map<String, String> = emptyMap()
    ): String {
        val clean = stripDevice(rawJid)
        val candidates = listOf(
            meta?.altJid.orEmpty(),
            meta?.jid.orEmpty(),
            clean,
            lidToPn[clean].orEmpty()
        ).map(::stripDevice)
        candidates.firstOrNull { it.isNotBlank() && it != canonicalJid }?.let { return it }
        if (canonicalJid.endsWith("@s.whatsapp.net", ignoreCase = true)) {
            for ((lid, pn) in lidToPn) {
                if (stripDevice(pn) == canonicalJid) return stripDevice(lid)
            }
        }
        return ""
    }

    fun formatUnresolvedLidLabel(rawJid: String): String {
        val tail = stripDevice(rawJid).substringBefore("@").takeLast(4)
        return if (tail.isNotBlank()) "~ User · …$tail" else "~ User"
    }

    fun resolve(
        rawJidOrText: String,
        explicitName: String = "",
        isSelf: Boolean = false,
        ownJid: String = "",
        ownLid: String = "",
        participantMeta: Map<String, ResolvedParticipantUi> = emptyMap(),
        chatsByJid: Map<String, ChatEntity> = emptyMap(),
        contactsByJid: Map<String, ContactEntity> = emptyMap(),
        senderPushByJid: Map<String, String> = emptyMap(),
        lidToPn: Map<String, String> = emptyMap(),
        prefixPushWithTilde: Boolean = true,
        isGroupAnnounceLocked: Boolean = false
    ): ParticipantDisplayResult {
        val cleanJid = stripDevice(rawJidOrText)
        val meta = lookupMeta(cleanJid, participantMeta, lidToPn)
        val canonicalJid = resolveCanonicalJid(cleanJid, meta, lidToPn)
        val altJid = resolveAltJid(cleanJid, canonicalJid, meta, lidToPn)

        val effectiveSelf = isSelf ||
            meta?.isSelf == true ||
            isSelfParticipant(cleanJid, altJid, ownJid, ownLid, lidToPn) ||
            isSelfParticipant(canonicalJid, altJid, ownJid, ownLid, lidToPn)
        if (effectiveSelf) {
            val ownAvatar = ownJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it]?.avatarPath }
                ?: meta?.avatarPath?.takeIf { it.isNotBlank() }
            return ParticipantDisplayResult(
                primary = "You",
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = ownAvatar,
                isSelf = true
            )
        }

        if (cleanJid.endsWith("@g.us", ignoreCase = true) ||
            DeviceContactFallbackResolver.isGroupJidLikeIdentifier(cleanJid)
        ) {
            return ParticipantDisplayResult(
                primary = "",
                canonicalJid = cleanJid,
                altJid = altJid
            )
        }

        val jidCandidates = listOf(canonicalJid, cleanJid, altJid)
            .filter { it.isNotBlank() }
            .distinct()

        fun isValidName(candidate: String?): Boolean {
            val c = candidate?.trim().orEmpty()
            if (c.isBlank()) return false
            if (DeviceContactFallbackResolver.isGroupJidLikeIdentifier(c)) return false
            return jidCandidates.all { j ->
                DeviceContactFallbackResolver.isUsableDisplayName(c, j, altJid)
            }
        }

        val chatMatch = jidCandidates.firstNotNullOfOrNull { chatsByJid[it] }
        val contactMatch = jidCandidates.firstNotNullOfOrNull { contactsByJid[it] }
        val avatarPath = meta?.avatarPath?.takeIf { it.isNotBlank() }
            ?: chatMatch?.avatarPath?.takeIf { it.isNotBlank() }
            ?: contactMatch?.avatarPath?.takeIf { it.isNotBlank() }

        val savedName = sequenceOf(
            meta?.savedName,
            contactMatch?.takeIf { it.isSaved }?.name,
            chatMatch?.takeIf { !it.isGroup }?.name
        ).firstOrNull { isValidName(it) }?.trim().orEmpty()

        val pushName = sequenceOf(
            explicitName,
            meta?.pushName,
            jidCandidates.firstNotNullOfOrNull { senderPushByJid[it] },
            contactMatch?.name,
            chatMatch?.takeIf { !it.isGroup }?.name
        ).firstOrNull { isValidName(it) }?.trim().orEmpty()

        val rawPhone = when {
            !meta?.phone.isNullOrBlank() -> meta!!.phone
            canonicalJid.endsWith("@s.whatsapp.net", ignoreCase = true) -> canonicalJid.substringBefore("@")
            cleanJid.endsWith("@s.whatsapp.net", ignoreCase = true) -> cleanJid.substringBefore("@")
            altJid.endsWith("@s.whatsapp.net", ignoreCase = true) -> altJid.substringBefore("@")
            else -> {
                val bare = cleanJid.removePrefix("+")
                if (!cleanJid.contains("@") && cleanJid.all { it.isDigit() || it == '+' } && bare.length in 7..13) {
                    bare
                } else ""
            }
        }
        val formattedPhone = DeviceContactFallbackResolver.formatIntlPhoneCompact(rawPhone)

        return when {
            savedName.isNotBlank() -> ParticipantDisplayResult(
                primary = savedName,
                secondaryPhone = formattedPhone,
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = avatarPath
            )
            pushName.isNotBlank() -> ParticipantDisplayResult(
                primary = if (prefixPushWithTilde) "~ $pushName" else pushName,
                secondaryPhone = formattedPhone,
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = avatarPath
            )
            formattedPhone.isNotBlank() -> ParticipantDisplayResult(
                primary = formattedPhone,
                secondaryPhone = "",
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = avatarPath
            )
            canonicalJid.endsWith("@lid", ignoreCase = true) || cleanJid.endsWith("@lid", ignoreCase = true) ->
                ParticipantDisplayResult(
                    primary = formatUnresolvedLidLabel(cleanJid.ifBlank { canonicalJid }),
                    canonicalJid = canonicalJid,
                    altJid = altJid,
                    avatarPath = avatarPath,
                    isUnresolvedLid = true
                )
            cleanJid.contains("@") -> ParticipantDisplayResult(
                primary = cleanJid.substringBefore("@"),
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = avatarPath
            )
            cleanJid.isNotBlank() -> ParticipantDisplayResult(
                primary = cleanJid,
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = avatarPath
            )
            else -> ParticipantDisplayResult(
                primary = if (isGroupAnnounceLocked) "Group Admin" else "~ Member",
                canonicalJid = canonicalJid,
                altJid = altJid,
                avatarPath = avatarPath
            )
        }
    }

    /**
     * Merges duplicate group receipts where the same participant appeared once under `@lid`
     * and once under `@s.whatsapp.net`, preserving highest delivery status and best known name.
     */
    fun deduplicateGroupReceipts(
        receipts: List<GroupReceiptEntity>,
        participantMeta: Map<String, ResolvedParticipantUi> = emptyMap(),
        lidToPn: Map<String, String> = emptyMap(),
        ownJid: String = "",
        ownLid: String = ""
    ): List<GroupReceiptEntity> {
        if (receipts.size <= 1) return receipts
        val byKey = LinkedHashMap<String, GroupReceiptEntity>(receipts.size)
        for (r in receipts) {
            val clean = stripDevice(r.participantJid)
            val meta = lookupMeta(clean, participantMeta, lidToPn)
            val canonical = if (isSelfParticipant(clean, meta?.altJid.orEmpty(), ownJid, ownLid, lidToPn) || meta?.isSelf == true) {
                MessageReactionEntity.ME_ACTOR
            } else {
                resolveCanonicalJid(clean, meta, lidToPn).ifBlank { clean }
            }
            val existing = byKey[canonical]
            if (existing == null) {
                byKey[canonical] = if (canonical.endsWith("@s.whatsapp.net") && r.participantJid != canonical) {
                    r.copy(participantJid = canonical)
                } else {
                    r
                }
            } else {
                val oldRank = WaDao.deliveryRank(existing.status)
                val newRank = WaDao.deliveryRank(r.status)
                val bestStatus = if (newRank > oldRank) r.status else existing.status
                val bestTs = when {
                    newRank > oldRank -> r.timestamp
                    newRank == oldRank -> maxOf(existing.timestamp, r.timestamp)
                    else -> existing.timestamp
                }
                val bestJid = when {
                    existing.participantJid.endsWith("@s.whatsapp.net") -> existing.participantJid
                    r.participantJid.endsWith("@s.whatsapp.net") -> r.participantJid
                    canonical.endsWith("@s.whatsapp.net") -> canonical
                    else -> existing.participantJid
                }
                val bestName = when {
                    DeviceContactFallbackResolver.isUsableDisplayName(existing.participantName, bestJid, r.participantJid) ->
                        existing.participantName
                    DeviceContactFallbackResolver.isUsableDisplayName(r.participantName, bestJid, existing.participantJid) ->
                        r.participantName
                    else -> existing.participantName.ifBlank { r.participantName }
                }
                byKey[canonical] = existing.copy(
                    participantJid = bestJid,
                    participantName = bestName,
                    status = bestStatus,
                    timestamp = bestTs
                )
            }
        }
        return byKey.values.sortedWith(
            compareByDescending<GroupReceiptEntity> { WaDao.deliveryRank(it.status) }
                .thenByDescending { it.timestamp }
        )
    }

    /**
     * Merges duplicate reaction rows where the same actor reacted under both `@lid` and `@s.whatsapp.net`
     * (or own LID and [MessageReactionEntity.ME_ACTOR]), keeping the latest reaction.
     */
    fun deduplicateReactions(
        reactions: List<MessageReactionEntity>,
        participantMeta: Map<String, ResolvedParticipantUi> = emptyMap(),
        lidToPn: Map<String, String> = emptyMap(),
        ownJid: String = "",
        ownLid: String = ""
    ): List<MessageReactionEntity> {
        if (reactions.size <= 1) return reactions
        val byKey = LinkedHashMap<String, MessageReactionEntity>(reactions.size)
        for (rx in reactions) {
            if (rx.actor.startsWith(MessageReactionEntity.LEGACY_PREFIX)) {
                byKey[rx.actor] = rx
                continue
            }
            val clean = stripDevice(rx.actor)
            val meta = lookupMeta(clean, participantMeta, lidToPn)
            val isSelf = rx.actor == MessageReactionEntity.ME_ACTOR ||
                meta?.isSelf == true ||
                isSelfParticipant(clean, meta?.altJid.orEmpty(), ownJid, ownLid, lidToPn)
            val key = if (isSelf) MessageReactionEntity.ME_ACTOR else resolveCanonicalJid(clean, meta, lidToPn).ifBlank { clean }
            val normalized = if (isSelf) {
                rx.copy(actor = MessageReactionEntity.ME_ACTOR)
            } else if (key.endsWith("@s.whatsapp.net") && rx.actor != key) {
                rx.copy(actor = key)
            } else {
                rx
            }
            val existing = byKey[key]
            if (existing == null || normalized.updatedAt >= existing.updatedAt) {
                byKey[key] = normalized
            }
        }
        return byKey.values.toList()
    }
}
