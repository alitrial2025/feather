use std::fmt::Write;
pub fn project_uri(base: &str, key: &str, name: &str) -> String {
    format!(
        "feather://open?url={}&key={}&name={}",
        encode(base),
        encode(key),
        encode(name)
    )
}
fn encode(value: &str) -> String {
    let mut result = String::new();
    for byte in value.bytes() {
        if byte.is_ascii_alphanumeric() || b"-._~".contains(&byte) {
            result.push(byte as char);
        } else {
            write!(&mut result, "%{byte:02X}").unwrap();
        }
    }
    result
}
#[cfg(test)]
mod tests {
    #[test]
    fn pairing_link_preserves_https_paths_and_base64_keys() {
        let value = super::project_uri("https://demo.test/session/abc", "abc+/=", "Chat demo");
        assert_eq!(value,"feather://open?url=https%3A%2F%2Fdemo.test%2Fsession%2Fabc&key=abc%2B%2F%3D&name=Chat%20demo");
    }
}
