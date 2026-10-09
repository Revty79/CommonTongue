// The accepted inference/ownership source stays byte-identical in this module.
#[path = "lib.rs"]
mod accepted;

// Catch-unwind still runs the default panic hook. Suppress it before model work
// so paths, tensor details and user content cannot reach stderr/logcat.
#[no_mangle]
pub extern "C" fn local_t5_privacy_init() {
    std::panic::set_hook(Box::new(|_| {}));
}
