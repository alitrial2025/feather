use std::net::{IpAddr, Ipv4Addr};

#[allow(dead_code)]
pub const DEFAULT_PROJECT_PATH: &str = ".";
#[allow(dead_code)]
pub const DEFAULT_HOST: IpAddr = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
#[allow(dead_code)]
pub const DEFAULT_PORT: u16 = 8080;
pub const BUNDLE_DIR_NAME: &str = ".featherbundle";
pub const FEATHER_SDK_VERSION: &str = "1.0.0";
pub const ASSET_EXTENSIONS: &[&str] = &["png", "jpg", "jpeg", "webp", "svg", "ttf", "otf", "json"];

pub const OFFICIAL_MODULES: &[&str] = &[
    "feather-network",
    "feather-storage",
    "feather-navigation",
    "feather-media",
    "feather-webview",
    "feather-image",
    "feather-device",
    "feather-files",
    "feather-auth",
    "feather-notifications",
    "feather-location",
    "feather-camera",
    "feather-maps",
    "feather-payments",
];
