//! Read a signed bundle using an independently trusted publisher key.
use super::{BundleArtifact, BundleManifest, artifact_for};
use std::{
    error::Error,
    fs,
    path::{Component, Path, PathBuf},
};

pub struct VerifiedBundle {
    root: PathBuf,
    pub manifest: BundleManifest,
    pub manifest_json: String,
}

impl VerifiedBundle {
    pub fn open(directory: &Path, key: &str) -> Result<Self, Box<dyn Error>> {
        let root = fs::canonicalize(directory)?;
        let manifest_json = fs::read_to_string(root.join("manifest.json"))?;
        let manifest: BundleManifest = serde_json::from_str(&manifest_json)?;
        let signature = manifest.signature.as_ref().ok_or("Unsigned manifest")?;
        if !crate::verify_manifest_signature(manifest_json.clone(), signature.clone(), key.into()) {
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
        Ok(Self {
            root,
            manifest,
            manifest_json,
        })
    }

    pub fn artifacts(&self) -> impl Iterator<Item = &BundleArtifact> {
        std::iter::once(&self.manifest.render)
            .chain(self.manifest.javascript.iter())
            .chain(self.manifest.wasm.iter())
            .chain(self.manifest.assets.iter())
            .chain(self.manifest.fonts.iter())
    }

    pub fn read_artifact(&self, artifact: &BundleArtifact) -> Result<Vec<u8>, Box<dyn Error>> {
        let path = Path::new(&artifact.path);
        if path.as_os_str().is_empty()
            || artifact.path.contains('\\')
            || path
                .components()
                .any(|part| !matches!(part, Component::Normal(_)))
        {
            return Err("Invalid artifact path".into());
        }
        let path = fs::canonicalize(self.root.join(path))?;
        if !path.starts_with(&self.root) {
            return Err("Artifact escapes bundle directory".into());
        }
        let bytes = fs::read(path)?;
        if bytes.len() as u64 != artifact.bytes
            || artifact_for(&artifact.path, &bytes).sha256 != artifact.sha256
        {
            return Err(format!("Artifact verification failed: {}", artifact.path).into());
        }
        Ok(bytes)
    }
}
