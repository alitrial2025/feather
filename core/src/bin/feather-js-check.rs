//! Execute a compiler-produced Kotlin/JS application using the real core.
use feather_core::AppRuntime;
fn main() {
    let result = (|| -> Result<(), Box<dyn std::error::Error>> {
        let path = std::env::args_os()
            .nth(1)
            .ok_or("usage: feather-js-check <app.js>")?;
        let runtime = AppRuntime::new();
        runtime.load_javascript(std::fs::read_to_string(path)?)?;
        println!("INITIAL {}", runtime.render_javascript()?);
        runtime.apply_action_event(r#"{"type":"action","action":"increment"}"#);
        println!("AFTER_ACTION {}", runtime.render_javascript()?);
        Ok(())
    })();
    if let Err(error) = result {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
