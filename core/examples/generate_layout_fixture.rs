fn main() {
    let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).parent().unwrap();
    let directory = root.join("host/android/app/src/test/resources");
    let json = std::fs::read_to_string(directory.join("layout.json")).unwrap();
    let bytes = feather_core::bundle::flat_serializer::json_to_flatbuffer(&json).unwrap();
    std::fs::write(directory.join("layout.fb"), bytes).unwrap();
}
