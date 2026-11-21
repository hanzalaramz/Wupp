use std::str::FromStr;

use whatsapp_rust::wacore_binary::Jid;

/// Parses phone digits or full JID strings into a `Jid`, defaulting bare phone strings to PN.
pub fn parse_jid_flexible(raw: &str) -> Result<Jid, String> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err("empty JID".to_string());
    }
    if trimmed.contains('@') {
        return Jid::from_str(trimmed).map_err(|e| format!("invalid JID '{trimmed}': {e}"));
    }
    let digits: String = trimmed.chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.is_empty() {
        return Err(format!("invalid JID '{trimmed}': no digits"));
    }
    Ok(Jid::pn(digits))
}

/// Strips device suffixes (`user:12@server` -> `user@server`) while preserving server domains.
#[allow(dead_code)]
pub fn to_non_ad_string(jid: &Jid) -> String {
    jid.to_non_ad_string()
}

/// Canonicalizes a raw string JID without hitting the network (drops `:device` if parseable).
pub fn canonical_jid_str(raw: &str) -> String {
    match parse_jid_flexible(raw) {
        Ok(jid) => jid.to_non_ad_string(),
        Err(_) => raw.trim().to_string(),
    }
}

/// Extracts digits-only phone string when `jid` is a classic `@s.whatsapp.net` user JID.
pub fn phone_digits_from_jid(jid: &Jid) -> String {
    if jid.is_pn() {
        jid.user.chars().filter(|c| c.is_ascii_digit()).collect()
    } else {
        String::new()
    }
}

/// Maps normalized message/media type + MIME type to `(subdir, file_extension)`.
pub fn media_subdir_and_ext(msg_type: &str, mime: &str) -> (&'static str, &'static str) {
    let mime_base = mime
        .split(';')
        .next()
        .unwrap_or("")
        .trim()
        .to_ascii_lowercase();

    match msg_type.to_ascii_lowercase().as_str() {
        "image" => {
            let ext = match mime_base.as_str() {
                "image/png" => ".png",
                "image/webp" => ".webp",
                "image/gif" => ".gif",
                _ => ".jpg",
            };
            ("images", ext)
        }
        "video" | "gif" | "ptv" => {
            let ext = match mime_base.as_str() {
                "video/webm" => ".webm",
                "video/3gpp" => ".3gp",
                _ => ".mp4",
            };
            ("videos", ext)
        }
        "audio" | "ptt" => {
            let ext = match mime_base.as_str() {
                "audio/mpeg" | "audio/mp3" => ".mp3",
                "audio/mp4" | "audio/aac" => ".m4a",
                "audio/amr" => ".amr",
                _ => ".ogg",
            };
            ("audio", ext)
        }
        "sticker" => ("stickers", ".webp"),
        "document" => {
            let ext = match mime_base.as_str() {
                "application/pdf" => ".pdf",
                "text/plain" => ".txt",
                "application/zip" => ".zip",
                "application/vnd.android.package-archive" => ".apk",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" => ".docx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" => ".xlsx",
                "application/msword" => ".doc",
                "image/jpeg" | "image/jpg" => ".jpg",
                "image/png" => ".png",
                "video/mp4" => ".mp4",
                "audio/mpeg" => ".mp3",
                _ => ".bin",
            };
            ("documents", ext)
        }
        _ => ("files", ".bin"),
    }
}

/// Sanitizes an ID or filename component so it cannot escape directories or break FAT/POSIX limits.
pub fn safe_file_component(raw: &str) -> String {
    let cleaned: String = raw
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.' {
                c
            } else {
                '_'
            }
        })
        .take(96)
        .collect();
    if cleaned.is_empty() || cleaned == "." || cleaned == ".." {
        "item".to_string()
    } else {
        cleaned
    }
}

/// Splits a comma-separated string into non-empty trimmed elements.
pub fn split_csv(csv: &str) -> Vec<String> {
    csv.split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_string)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_phone_and_device_jids() {
        let bare = parse_jid_flexible("+1 (555) 123-4567").unwrap();
        assert_eq!(bare.to_string(), "15551234567@s.whatsapp.net");

        let device = parse_jid_flexible("15551234567:12@s.whatsapp.net").unwrap();
        assert_eq!(to_non_ad_string(&device), "15551234567@s.whatsapp.net");
        assert_eq!(phone_digits_from_jid(&device), "15551234567");

        let lid = parse_jid_flexible("9988776655:2@lid").unwrap();
        assert_eq!(to_non_ad_string(&lid), "9988776655@lid");
        assert!(phone_digits_from_jid(&lid).is_empty());
    }

    #[test]
    fn media_extensions_match_contract() {
        assert_eq!(media_subdir_and_ext("ptt", "audio/ogg; codecs=opus"), ("audio", ".ogg"));
        assert_eq!(media_subdir_and_ext("image", "image/png"), ("images", ".png"));
        assert_eq!(media_subdir_and_ext("sticker", "image/webp"), ("stickers", ".webp"));
        assert_eq!(media_subdir_and_ext("document", "application/pdf"), ("documents", ".pdf"));
    }

    #[test]
    fn sanitize_prevents_path_traversal() {
        assert_eq!(safe_file_component("../etc/passwd"), ".._etc_passwd");
        assert_eq!(safe_file_component(".."), "item");
        assert_eq!(safe_file_component("ABCDEF_123"), "ABCDEF_123");
    }
}
