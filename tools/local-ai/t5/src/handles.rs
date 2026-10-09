//! IDs crossing JSON/JNI are never addresses. Tagged Android pointers stay in Rust.
use std::collections::BTreeMap;

pub const MAX_HANDLE: u64 = u32::MAX as u64;

pub struct Handles<T> {
    next: u64,
    entries: BTreeMap<u64, Box<T>>,
}

impl<T> Handles<T> {
    pub fn new() -> Self { Self { next: 1, entries: BTreeMap::new() } }

    pub fn insert(&mut self, value: T) -> Option<u64> {
        if self.next > MAX_HANDLE { return None; }
        let id = self.next;
        self.next += 1; // Never recycle an ID or reinterpret it as a pointer.
        self.entries.insert(id, Box::new(value));
        Some(id)
    }

    pub fn get_mut(&mut self, id: u64) -> Option<&mut T> {
        self.entries.get_mut(&id).map(Box::as_mut)
    }

    pub fn remove(&mut self, id: u64) { self.entries.remove(&id); }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn model_ownership_does_not_cross_json_as_an_address() {
        let mut handles = Handles::new();
        let id = handles.insert(vec![42u8; 32]).unwrap();
        assert_eq!(id, 1);
        let encoded = serde_json::to_string(&id).unwrap();
        let round_trip: u64 = serde_json::from_str(&encoded).unwrap();
        assert_eq!(handles.get_mut(round_trip).unwrap()[31], 42);
        assert_eq!(handles.get_mut(round_trip).unwrap().len(), 32);
    }

    #[test]
    fn invalid_tagged_or_lossy_android_handles_are_errors_not_dereferences() {
        let mut handles = Handles::new();
        handles.insert(42);
        for invalid in [0, 2, 0xb400_007a_1234_5678, i64::MAX as u64, u64::MAX] {
            assert!(handles.get_mut(invalid).is_none());
            handles.remove(invalid); // Closing unknown handles is harmless too.
        }
        assert_eq!(*handles.get_mut(1).unwrap(), 42);
    }

    #[test]
    fn closed_id_cannot_alias_a_new_model() {
        let mut handles = Handles::new();
        let first = handles.insert(42).unwrap();
        handles.remove(first);
        let second = handles.insert(43).unwrap();
        assert_ne!(first, second);
        assert!(handles.get_mut(first).is_none());
        assert_eq!(*handles.get_mut(second).unwrap(), 43);
    }

    #[test]
    fn exhaustion_does_not_wrap_or_exceed_exact_json_range() {
        let mut handles = Handles::new();
        handles.next = MAX_HANDLE;
        assert_eq!(handles.insert(42), Some(MAX_HANDLE));
        assert!(handles.insert(43).is_none());
        assert_eq!(MAX_HANDLE as f64 as u64, MAX_HANDLE);
    }
}
