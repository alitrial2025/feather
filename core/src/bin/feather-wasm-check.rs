//! Validate a compiler-produced module against the actual Feather runtime.
use feather_core::AppRuntime;
fn main() {
    let result = (|| -> Result<(), Box<dyn std::error::Error>> {
        let path = std::env::args_os()
            .nth(1)
            .ok_or("usage: feather-wasm-check <module.wasm>")?;
        let bytes = std::fs::read(path)?;
        AppRuntime::new().load_wasm(bytes)?;
        println!("Module loaded successfully (application ABI and behavior not checked)");
        Ok(())
    })();
    if let Err(error) = result {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
