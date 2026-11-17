//! Verify a runtime bundle against an explicitly supplied trusted public key.
use feather_core::{BundleManifest, artifact_for, verify_manifest_signature};
use std::{
    fs,
    path::{Component, Path},
};
fn main() {
    let result = (|| -> Result<(), Box<dyn std::error::Error>> {
        let mut arguments = std::env::args_os().skip(1);
        let directory = arguments
            .next()
            .ok_or("usage: feather-bundle-check <directory> <trusted-public-key-file>")?;
        let key = arguments
            .next()
            .ok_or("A trusted public key file is required")?;
        let root = fs::canonicalize(directory)?;
        let json = fs::read_to_string(root.join("manifest.json"))?;
        let manifest: BundleManifest = serde_json::from_str(&json)?;
        if !verify_manifest_signature(
            json,
            manifest.signature.clone().ok_or("Unsigned manifest")?,
            fs::read_to_string(key)?,
        ) {
            return Err("Manifest signature verification failed".into());
        }
        if manifest.protocol_version != 1 || manifest.sdk_version != "1.0.0" {
            return Err("Unsupported bundle protocol or SDK".into());
        }
        match manifest.runtime.as_deref() {
            Some("kotlin-js") if manifest.javascript.is_some() && manifest.wasm.is_none() => {}
            None if manifest.javascript.is_none() => {}
            _ => return Err("Unsupported or inconsistent runtime declaration".into()),
        }
        let mut artifacts = vec![&manifest.render];
        artifacts.extend(manifest.javascript.iter());
        artifacts.extend(manifest.wasm.iter());
        artifacts.extend(manifest.assets.iter());
        artifacts.extend(manifest.fonts.iter());
        for artifact in &artifacts {
            let path = Path::new(&artifact.path);
            if path.as_os_str().is_empty()
                || path
                    .components()
                    .any(|part| !matches!(part, Component::Normal(_)))
            {
                return Err("Invalid artifact path".into());
            }
            let path = fs::canonicalize(root.join(path))?;
            if !path.starts_with(&root) {
                return Err("Artifact escapes bundle directory".into());
            }
            let bytes = fs::read(path)?;
            if bytes.len() as u64 != artifact.bytes
                || artifact_for(&artifact.path, &bytes).sha256 != artifact.sha256
            {
                return Err(format!("Artifact verification failed: {}", artifact.path).into());
            }
        }
        println!(
            "Verified manifest and {} runtime artifacts",
            artifacts.len()
        );
        Ok(())
    })();
    if let Err(error) = result {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
