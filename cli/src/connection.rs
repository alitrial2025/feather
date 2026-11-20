use std::fmt::Write;

pub fn session_token(phone: bool) -> Result<Option<String>, String> {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
    use ring::rand::{SecureRandom, SystemRandom};

    let token = match std::env::var("FEATHER_SESSION_TOKEN") {
        Ok(token) => token,
        Err(std::env::VarError::NotPresent) if !phone => return Ok(None),
        Err(std::env::VarError::NotPresent) => {
            let mut bytes = [0; 32];
            SystemRandom::new()
                .fill(&mut bytes)
                .map_err(|_| "Could not generate a Feather Go session token".to_string())?;
            return Ok(Some(URL_SAFE_NO_PAD.encode(bytes)));
        }
        Err(std::env::VarError::NotUnicode(_)) => {
            return Err("FEATHER_SESSION_TOKEN must be valid Unicode".into());
        }
    };
    if token.len() < 24
        || !token
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"-_".contains(&byte))
    {
        return Err("FEATHER_SESSION_TOKEN must contain at least 24 URL-safe characters".into());
    }
    Ok(Some(token))
}

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
