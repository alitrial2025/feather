//! Verify a runtime bundle against an explicitly supplied trusted public key.
use feather_core::VerifiedBundle;
use std::{fs, path::Path};

fn main() {
    let result = (|| -> Result<(), Box<dyn std::error::Error>> {
        let mut arguments = std::env::args_os().skip(1);
        let directory = arguments
            .next()
            .ok_or("usage: feather-bundle-check <directory> <trusted-public-key-file>")?;
        let key = arguments
            .next()
            .ok_or("A trusted public key file is required")?;
        let bundle = VerifiedBundle::open(Path::new(&directory), &fs::read_to_string(key)?)?;
        let mut count = 0;
        for artifact in bundle.artifacts() {
            bundle.read_artifact(artifact)?;
            count += 1;
        }
        println!("Verified manifest and {count} runtime artifacts");
        Ok(())
    })();
    if let Err(error) = result {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
