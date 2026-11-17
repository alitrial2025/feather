use ring::signature::KeyPair;
fn main() {
    let rng = ring::rand::SystemRandom::new();
    let pkcs8_bytes = ring::signature::Ed25519KeyPair::generate_pkcs8(&rng).unwrap();
    let key_pair = ring::signature::Ed25519KeyPair::from_pkcs8(pkcs8_bytes.as_ref()).unwrap();
    use base64::Engine as _;
    let pub_key_b64 = base64::engine::general_purpose::STANDARD.encode(key_pair.public_key().as_ref());
    let priv_key_b64 = base64::engine::general_purpose::STANDARD.encode(pkcs8_bytes.as_ref());
    println!("KEY_GEN_PUB: {}", pub_key_b64);
    println!("KEY_GEN_PRIV: {}", priv_key_b64);
}
