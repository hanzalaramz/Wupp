mod client;
mod contacts_groups;
mod events;
mod jid_helpers;
mod jni_api;
mod messaging;
mod meta_db;
mod runtime;
mod voip;

use std::sync::Once;

static LOG_INIT: Once = Once::new();

pub(crate) fn init_android_logger() {
    LOG_INIT.call_once(|| {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("WuppRustBridge"),
        );
    });
}
