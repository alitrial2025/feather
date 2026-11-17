//! Repository-based experimental Kotlin/JS compiler adapter.
use std::path::Path;

pub fn compile_javascript(project: &Path) -> Result<(), String> {
    let repo = std::env::var_os("FEATHER_REPOSITORY_ROOT")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|| {
            Path::new(env!("CARGO_MANIFEST_DIR"))
                .parent()
                .unwrap()
                .to_path_buf()
        });
    let output = std::process::Command::new("python3")
        .arg(repo.join("compiler/kotlin-js/compile.py"))
        .arg(&repo)
        .arg(project)
        .output()
        .map_err(|error| {
            format!("Could not start Kotlin/JS compiler (requires Python 3 and JDK 17+): {error}")
        })?;
    if !output.status.success() {
        return Err(format!(
            "Kotlin/JS compilation failed:\n{}\n{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        ));
    }
    println!("{}", String::from_utf8_lossy(&output.stdout).trim());
    Ok(())
}
