use clap::Subcommand;
use feather_core::{VerifiedBundle, artifact_for};
use serde::{Deserialize, Serialize};
use std::{
    error::Error,
    fs::{self, File},
    io::Write,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

#[derive(Subcommand)]
pub enum ReleaseCommand {
    /// Verify and snapshot a signed bundle, then activate it on a channel
    Promote { bundle: PathBuf },
    /// Verify and restore the previous bundle on a channel
    Rollback,
}

#[derive(Deserialize, Serialize)]
struct Channel {
    app: String,
    current: String,
    previous: Option<String>,
}

pub fn run(
    store: PathBuf,
    channel: &str,
    key_file: &Path,
    command: ReleaseCommand,
) -> Result<(), Box<dyn Error>> {
    if channel.is_empty()
        || channel.len() > 63
        || !channel
            .bytes()
            .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
    {
        return Err("Channel names must contain 1–63 lowercase letters, digits or hyphens".into());
    }
    let key = fs::read_to_string(key_file)?;
    let channels = store.join("channels");
    let bundles = store.join("bundles");
    fs::create_dir_all(&channels)?;
    fs::create_dir_all(&bundles)?;
    // Serialize read/modify/write operations across independent CLI processes.
    let lock = File::options()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .open(store.join(".release.lock"))?;
    lock.lock()?;
    let channel_file = channels.join(format!("{channel}.json"));
    let state: Option<Channel> = match fs::read(&channel_file) {
        Ok(bytes) => Some(serde_json::from_slice(&bytes)?),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(error) => return Err(error.into()),
    };
    let stage = store.join(format!(
        ".release-{}-{}",
        std::process::id(),
        SystemTime::now().duration_since(UNIX_EPOCH)?.as_nanos()
    ));
    fs::create_dir(&stage)?;
    let result = (|| -> Result<(), Box<dyn Error>> {
        let updated = match command {
            ReleaseCommand::Promote { bundle } => {
                let bundle = VerifiedBundle::open(&bundle, &key)?;
                let app = release_app(&bundle)?;
                if state.as_ref().is_some_and(|state| state.app != app) {
                    return Err("The bundle belongs to a different app than this channel".into());
                }
                let id = bundle_id(&bundle);
                let destination = bundles.join(&id);
                let snapshot = stage.join("bundle");
                fs::create_dir(&snapshot)?;
                // Verify the exact bytes we store, even if another process edits the source.
                for artifact in bundle.artifacts() {
                    let bytes = bundle.read_artifact(artifact)?;
                    let path = snapshot.join(&artifact.path);
                    fs::create_dir_all(path.parent().unwrap())?;
                    File::create_new(path)?.write_all(&bytes)?;
                }
                File::create_new(snapshot.join("manifest.json"))?
                    .write_all(bundle.manifest_json.as_bytes())?;
                if destination.exists() {
                    verify_snapshot(&bundles, &id, &key, &app)?;
                } else {
                    fs::rename(snapshot, destination)?;
                }
                match state {
                    Some(state) if state.current == id => state,
                    state => Channel {
                        app,
                        current: id,
                        previous: state.map(|state| state.current),
                    },
                }
            }
            ReleaseCommand::Rollback => {
                let state = state.ok_or("Channel does not exist")?;
                let previous = state.previous.ok_or("Channel has no previous release")?;
                verify_snapshot(&bundles, &previous, &key, &state.app)?;
                Channel {
                    app: state.app,
                    current: previous,
                    previous: Some(state.current),
                }
            }
        };
        let pointer = stage.join("channel.json");
        fs::write(&pointer, serde_json::to_vec_pretty(&updated)?)?;
        fs::rename(pointer, channel_file)?;
        println!("{channel}: {}", updated.current);
        Ok(())
    })();
    let cleanup = fs::remove_dir_all(stage);
    result?;
    cleanup?;
    Ok(())
}

fn release_app(bundle: &VerifiedBundle) -> Result<String, Box<dyn Error>> {
    let manifest = &bundle.manifest;
    if manifest.runtime.as_deref() != Some("kotlin-js")
        || manifest
            .javascript
            .as_ref()
            .is_none_or(|artifact| artifact.path != "app.js")
        || !manifest.dependencies.is_empty()
        || !manifest.delta.changed.is_empty()
        || !manifest.delta.removed.is_empty()
    {
        return Err("Expected a Kotlin/JS release produced by feather-cli build".into());
    }
    Ok(manifest
        .metadata
        .app_id
        .as_deref()
        .filter(|id| !id.is_empty())
        .unwrap_or(&manifest.name)
        .into())
}

fn bundle_id(bundle: &VerifiedBundle) -> String {
    artifact_for("manifest.json", bundle.manifest_json.as_bytes()).sha256
}

fn verify_snapshot(store: &Path, id: &str, key: &str, app: &str) -> Result<(), Box<dyn Error>> {
    if id.len() != 64 || !id.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err("Invalid bundle ID in release channel".into());
    }
    let bundle = VerifiedBundle::open(&store.join(id), key)?;
    if bundle_id(&bundle) != id || release_app(&bundle)? != app {
        return Err("Release snapshot does not match the expected bundle or app".into());
    }
    for artifact in bundle.artifacts() {
        bundle.read_artifact(artifact)?;
    }
    Ok(())
}
