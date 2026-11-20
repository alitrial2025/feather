use clap::{Parser, Subcommand, ValueEnum};
use std::net::IpAddr;
use std::path::PathBuf;

#[derive(Parser)]
#[command(name = "feather")]
#[command(about = "Feather CLI", long_about = None)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Commands,
}

#[derive(Clone, Copy, ValueEnum)]
pub enum Starter {
    Counter,
    Chat,
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
        #[arg(long, help = "Start a private Feather Go session")]
        phone: bool,
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
        #[arg(long, value_enum, default_value = "counter")]
        template: Starter,
    },
    /// Build a production artifact for iOS or Android
    Build {
        #[arg(long, default_value = ".")]
        project: PathBuf,
        #[arg(long, help = "Target platform: ios or android")]
        target: String,
    },
    /// Compile Kotlin using the experimental no-JIT JavaScript runtime backend
    CompileJs {
        #[arg(long, default_value = ".")]
        project: PathBuf,
    },
    /// Check the current Kotlin/JS render tree against the native JSON host parsers
    Inspect {
        #[arg(long, default_value = ".")]
        project: PathBuf,
        #[arg(long, help = "Apply a widget action and check the next screen; repeat to navigate")]
        action: Vec<String>,
    },
    /// Manage locally staged signed release channels
    Release {
        #[arg(long)]
        store: PathBuf,
        #[arg(long)]
        channel: String,
        /// File containing the trusted publisher's base64 public key
        #[arg(long)]
        key: PathBuf,
        #[command(subcommand)]
        operation: crate::release::ReleaseCommand,
    },
    /// Convert a React Native, Compose, or Android XML screen into a verified Feather project
    Rewrite {
        #[arg(long)]
        source: PathBuf,
        /// Relative screen or layout path when source is a directory
        #[arg(long)]
        entry: Option<PathBuf>,
        /// React component name or Compose screen function
        #[arg(long)]
        component: Option<String>,
        #[arg(long)]
        output: PathBuf,
        /// Refresh an unedited generated project while its dev server is running
        #[arg(long)]
        refresh: bool,
    },
    /// Compile Kotlin source to WebAssembly
    CompileWasm {
        #[arg(long, default_value = ".")]
        project: PathBuf,
    },
}
