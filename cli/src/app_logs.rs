use serde_json::Value;

pub fn print(source: &str, level: &str, message: &str) {
    let level = match level.to_ascii_lowercase().as_str() {
        "error" | "fatal" => "ERROR",
        "warning" | "warn" => "WARN",
        "debug" => "DEBUG",
        _ => "LOG",
    };
    // Device and app output must never be interpreted as terminal commands.
    let safe_message: String = message
        .chars()
        .take(16_384)
        .filter(|ch| !ch.is_control() || *ch == '\n' || *ch == '\t')
        .collect();
    let output = safe_message
        .lines()
        .map(|line| format!("[{source} {level}] {line}"))
        .collect::<Vec<_>>()
        .join("\n");
    if output.is_empty() {
        return;
    }
    if level == "ERROR" || level == "WARN" {
        eprintln!("{output}");
    } else {
        println!("{output}");
    }
}

pub fn device_event(event: &Value, kind: &str) {
    let Some(message) = event["message"].as_str() else {
        return;
    };
    let source = match event["platform"].as_str() {
        Some("android") => "Android",
        Some("ios") => "iOS",
        _ => "Device",
    };
    match event["level"].as_str() {
        Some(level) => print(source, level, message),
        None if kind == "log" => console(source, message),
        None => print(source, "error", message),
    }
}

pub fn console(source: &str, message: &str) {
    for (prefix, level) in [
        ("[ERROR] ", "error"),
        ("[WARN] ", "warn"),
        ("[DEBUG] ", "debug"),
    ] {
        if let Some(message) = message.strip_prefix(prefix) {
            print(source, level, message);
            return;
        }
    }
    print(source, "info", message);
}
