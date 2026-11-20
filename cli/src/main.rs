mod app_logs;
mod bundler;
mod compiler;
mod compatibility;
mod connection;
mod cli;
mod commands;
mod config;
mod constants;
mod models;
mod parser;
mod symbolic_parser;
mod fui_parser;
mod optimizer;
mod release;
mod rewrite;
mod server;

use clap::Parser;
use cli::{Cli, Commands};
use config::DevServerConfig;
use server::run_server;

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    let result = match cli.command {
        Commands::Start { project, host, port, tunnel, phone } => {
            let session = connection::session_token(phone).unwrap_or_else(|error| {
                eprintln!("{error}");
                std::process::exit(1);
            });
            let config = match DevServerConfig::new(project, host, port, tunnel) {
                Ok(config) => config,
                Err(error) => {
                    eprintln!("{error}");
                    std::process::exit(1);
                }
            };
            if config.manifest.runtime.as_deref() == Some("kotlin-js") {
                if let Err(error) = compiler::compile_javascript(&config.project_path) {
                    eprintln!("{error}"); std::process::exit(1);
                }
            }
            run_server(config, session).await;
            Ok(())
        }
        Commands::Install { project, module } => commands::install_module(project, module, "latest".to_string()).await,
        Commands::Uninstall { project, module } => commands::uninstall_module(project, module).await,
        Commands::Doctor { project } => commands::doctor(project).await,
        Commands::Modules { project } => commands::list_modules(project).await,
        Commands::Update { project } => commands::upgrade_modules(project).await,
        Commands::Init { name, template } => commands::init_project(name, template).await,
        Commands::Build { project, target } => commands::build_production(project, target).await,
        Commands::CompileJs { project } => compiler::compile_javascript(&project).map_err(Into::into),
        Commands::Inspect { project, action } => compatibility::inspect(project, action).map_err(Into::into),
        Commands::Release { store, channel, key, operation } => release::run(store, &channel, &key, operation),
        Commands::Rewrite { source, entry, component, output, refresh } => rewrite::run(source, entry, component, output, refresh),
        Commands::CompileWasm { project } => commands::compile_wasm(project).await,
    };

    if let Err(error) = result {
        eprintln!("❌ Error: {error}");
        std::process::exit(1);
    }
}
