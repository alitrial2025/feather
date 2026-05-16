use walrus::ModuleConfig;

fn main() {
    let wasm = std::fs::read("../../samples/hello-world/.feather/app.wasm").unwrap();
    let config = ModuleConfig::new();
    match config.parse(&wasm) {
        Ok(_) => println!("Walrus successfully parsed WasmGC!"),
        Err(e) => println!("Walrus failed: {}", e),
    }
}
