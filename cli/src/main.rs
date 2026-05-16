mod bundler;
mod cli;
mod commands;
mod config;
mod constants;
mod models;
mod parser;
mod server;

use clap::Parser;
use cli::{Cli, Commands};
use config::DevServerConfig;
use server::run_server;

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    match cli.command {
        Commands::Start { project, host, port } => {
            let config = match DevServerConfig::new(project, host, port) {
                Ok(config) => config,
                Err(error) => {
                    eprintln!("{error}");
                    std::process::exit(1);
                }
            };
            run_server(config).await;
        }
        Commands::Install { project, module } => commands::install_module(project, module).await,
        Commands::Doctor { project } => commands::doctor(project).await,
        Commands::Modules { project } => commands::list_modules(project).await,
        Commands::Upgrade { project } => commands::upgrade_modules(project).await,
        Commands::Init { name } => commands::init_project(name).await,
        Commands::Build { project, target } => commands::build_production(project, target).await,
        Commands::CompileWasm { project } => commands::compile_wasm(project).await,
    }
}
