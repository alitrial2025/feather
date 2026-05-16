use clap::{Parser, Subcommand};
use std::net::IpAddr;
use std::path::PathBuf;

#[derive(Parser)]
#[command(name = "feather")]
#[command(about = "Feather CLI", long_about = None)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Commands,
}

#[derive(Subcommand)]
pub enum Commands {
    /// Start the development server
    Start {
        #[arg(long, default_value = ".")]
        project: PathBuf,
        #[arg(long, default_value = "127.0.0.1")]
        host: IpAddr,
        #[arg(long, default_value_t = 8080)]
        port: u16,
        #[arg(long, help = "Expose the server via a tunnel/public URL")]
        tunnel: bool,
    },
    /// Install a module
    Install {
        #[arg(long, default_value = ".")]
        project: PathBuf,
        module: String,
    },
    /// Uninstall a module
    Uninstall {
        #[arg(long, default_value = ".")]
        project: PathBuf,
        module: String,
    },
    /// Check project configuration
    Doctor {
        #[arg(long, default_value = ".")]
        project: PathBuf,
    },
    /// List installed modules
    Modules {
        #[arg(long, default_value = ".")]
        project: PathBuf,
    },
    /// Upgrade/Update project dependencies
    Update {
        #[arg(long, default_value = ".")]
        project: PathBuf,
    },
    /// Initialize a new Feather project
    Init {
        /// Directory name for the new project
        name: String,
    },
    /// Build a production artifact for iOS or Android
    Build {
        #[arg(long, default_value = ".")]
        project: PathBuf,
        #[arg(long, help = "Target platform: ios or android")]
        target: String,
    },
    /// Compile Kotlin source to WebAssembly
    CompileWasm {
        #[arg(long, default_value = ".")]
        project: PathBuf,
    },
}
