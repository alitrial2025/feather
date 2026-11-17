//! Experimental no-JIT runtime for compiler-produced Kotlin/JS applications.
use crate::{ModuleCall, state::StateValue};
use rquickjs::{Context, Function, Runtime};
use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

type State = Arc<Mutex<HashMap<String, StateValue>>>;

pub struct JavaScriptRuntime {
    context: Context,
    _runtime: Runtime,
    deadline: Arc<Mutex<Instant>>,
    state_binding: Arc<Mutex<State>>,
    pub source_hash: String,
    logs_binding: Arc<Mutex<Arc<Mutex<Vec<String>>>>>,
    calls_binding: Arc<Mutex<Arc<Mutex<Vec<ModuleCall>>>>>,
}

impl JavaScriptRuntime {
    pub fn new(
        source: &str,
        state: State,
        needs_render: Arc<Mutex<bool>>,
        logs: Arc<Mutex<Vec<String>>>,
        calls: Arc<Mutex<Vec<ModuleCall>>>,
    ) -> Result<Self, String> {
        let logs_binding = Arc::new(Mutex::new(logs));
        let calls_binding = Arc::new(Mutex::new(calls));
        let state_binding = Arc::new(Mutex::new(state));
        let runtime = Runtime::new().map_err(|error| error.to_string())?;
        runtime.set_memory_limit(128 * 1024 * 1024);
        runtime.set_max_stack_size(1024 * 1024);
        let deadline = Arc::new(Mutex::new(Instant::now() + Duration::from_secs(5)));
        let timer = deadline.clone();
        runtime.set_interrupt_handler(Some(Box::new(move || {
            Instant::now() > *timer.lock().unwrap()
        })));
        let context = Context::full(&runtime).map_err(|error| error.to_string())?;
        context.with(|ctx| -> Result<(), String> {
            let globals = ctx.globals();
            let module_dirty = needs_render.clone();
            let get_state = state_binding.clone();
            let set_state = state_binding.clone();
            globals
                .set(
                    "__featherGetState",
                    Function::new(ctx.clone(), move |key: String| {
                        get_state
                            .lock()
                            .unwrap()
                            .lock()
                            .unwrap()
                            .get(&key)
                            .map(StateValue::as_str)
                    })
                    .map_err(|error| error.to_string())?,
                )
                .map_err(|error| error.to_string())?;
            globals
                .set(
                    "__featherSetState",
                    Function::new(
                        ctx.clone(),
                        move |ctx: rquickjs::Ctx<'_>,
                              key: String,
                              value: String|
                              -> rquickjs::Result<()> {
                            if key.len() > 1024 || value.len() > 1024 * 1024 {
                                return Err(rquickjs::Exception::throw_type(
                                    &ctx,
                                    "State key/value exceeds bridge limit",
                                ));
                            }
                            let value = serde_json::from_str::<serde_json::Value>(&value)
                                .map(|value| StateValue::from_json_value(&value))
                                .unwrap_or(StateValue::Str(value));
                            let target = set_state.lock().unwrap();
                            let mut state = target.lock().unwrap();
                            let previous = state.insert(key.clone(), value);
                            if state.len() > 4096
                                || serde_json::to_vec(&*state)
                                    .map_or(true, |bytes| bytes.len() > 16 * 1024 * 1024)
                            {
                                if let Some(previous) = previous {
                                    state.insert(key, previous);
                                } else {
                                    state.remove(&key);
                                }
                                return Err(rquickjs::Exception::throw_type(
                                    &ctx,
                                    "Shared state exceeds bridge limit",
                                ));
                            }
                            *needs_render.lock().unwrap() = true;
                            Ok(())
                        },
                    )
                    .map_err(|error| error.to_string())?,
                )
                .map_err(|error| error.to_string())?;
            let logs = logs_binding.clone();
            let calls = calls_binding.clone();
            globals
                .set(
                    "__featherLog",
                    Function::new(ctx.clone(), move |message: String| {
                        let target = logs.lock().unwrap();
                        let mut logs = target.lock().unwrap();
                        if logs.len() < 1024 && message.len() <= 16384 {
                            logs.push(message);
                        }
                    })
                    .map_err(|error| error.to_string())?,
                )
                .map_err(|error| error.to_string())?;
            globals
                .set(
                    "__featherModule",
                    Function::new(
                        ctx.clone(),
                        move |ctx: rquickjs::Ctx<'_>, encoded: String| -> rquickjs::Result<()> {
                            if encoded.len() > 2 * 1024 * 1024 {
                                return Err(rquickjs::Exception::throw_type(
                                    &ctx,
                                    "Module request exceeds bridge limit",
                                ));
                            }
                            let bytes = hex::decode(encoded).map_err(|_| {
                                rquickjs::Exception::throw_type(&ctx, "Invalid module encoding")
                            })?;
                            let value: serde_json::Value =
                                rmp_serde::from_slice(&bytes).map_err(|_| {
                                    rquickjs::Exception::throw_type(&ctx, "Invalid module request")
                                })?;
                            let module = value["module"].as_str().ok_or_else(|| {
                                rquickjs::Exception::throw_type(&ctx, "Missing module")
                            })?;
                            let method = value["method"].as_str().ok_or_else(|| {
                                rquickjs::Exception::throw_type(&ctx, "Missing method")
                            })?;
                            let target = calls.lock().unwrap();
                            let mut queue = target.lock().unwrap();
                            if queue.len() >= 128 {
                                return Err(rquickjs::Exception::throw_type(
                                    &ctx,
                                    "Module queue is full",
                                ));
                            }
                            *module_dirty.lock().unwrap() = true;
                            queue.push(ModuleCall {
                                module_name: module.to_string(),
                                action: method.to_string(),
                                payload: value["payload"].to_string(),
                                cb_key: value["resultStateKey"]
                                    .as_str()
                                    .unwrap_or_default()
                                    .to_string(),
                            });
                            Ok(())
                        },
                    )
                    .map_err(|error| error.to_string())?,
                )
                .map_err(|error| error.to_string())?;
            ctx.eval::<(), _>(source)
                .map_err(|error| js_error(&ctx, error))?;
            globals
                .get::<_, Function>("__featherRender")
                .map_err(|_| "Application did not install a render function".to_string())?;
            globals
                .get::<_, Function>("__featherAction")
                .map_err(|_| "Application did not install an action function".to_string())?;
            Ok(())
        })?;
        use sha2::Digest;
        Ok(Self {
            context,
            _runtime: runtime,
            deadline,
            state_binding,
            logs_binding,
            calls_binding,
            source_hash: hex::encode(sha2::Sha256::digest(source.as_bytes())),
        })
    }

    pub fn bind_outputs(&self, logs: Arc<Mutex<Vec<String>>>, calls: Arc<Mutex<Vec<ModuleCall>>>) {
        *self.logs_binding.lock().unwrap() = logs;
        *self.calls_binding.lock().unwrap() = calls;
    }

    pub fn bind_state(&self, state: State) {
        *self.state_binding.lock().unwrap() = state;
    }

    pub fn render(&self) -> Result<String, String> {
        *self.deadline.lock().unwrap() = Instant::now() + Duration::from_secs(1);
        self.context.with(|ctx| {
            let render: Function = ctx
                .globals()
                .get("__featherRender")
                .map_err(|error| js_error(&ctx, error))?;
            let result: String = render.call(()).map_err(|error| js_error(&ctx, error))?;
            let root: serde_json::Value = serde_json::from_str(&result)
                .map_err(|error| format!("Invalid render JSON: {error}"))?;
            if !root.is_object() || !root["type"].is_string() {
                return Err("Render must return a widget with a type".into());
            }
            Ok(result)
        })
    }

    pub fn events(&self, events: String) -> Result<(), String> {
        *self.deadline.lock().unwrap() = Instant::now() + Duration::from_secs(1);
        self.context.with(|ctx| {
            let callback: Function = ctx
                .globals()
                .get("__featherEvents")
                .map_err(|error| js_error(&ctx, error))?;
            callback
                .call::<_, ()>((events,))
                .map_err(|error| js_error(&ctx, error))
        })
    }

    pub fn action(&self, name: &str, payload: Option<String>) -> Result<(), String> {
        *self.deadline.lock().unwrap() = Instant::now() + Duration::from_secs(1);
        self.context.with(|ctx| {
            let action: Function = ctx
                .globals()
                .get("__featherAction")
                .map_err(|error| js_error(&ctx, error))?;
            action
                .call::<_, ()>((name, payload))
                .map_err(|error| js_error(&ctx, error))
        })
    }
}

fn js_error(ctx: &rquickjs::Ctx<'_>, error: rquickjs::Error) -> String {
    if error.is_exception() {
        let value = ctx.catch();
        if let Some(exception) = value.as_exception() {
            return format!(
                "{}\n{}",
                exception.message().unwrap_or_default(),
                exception.stack().unwrap_or_default()
            );
        }
        return format!("JavaScript exception: {value:?}");
    }
    error.to_string()
}

#[cfg(test)]
mod tests {
    use crate::AppRuntime;
    const APP: &str = r#"
        if (__featherGetState('count') == null) __featherSetState('count', '0');
        globalThis.__featherRender = () => JSON.stringify({type:'Text', text:__featherGetState('count')});
        globalThis.__featherAction = () => __featherSetState('count', String(Number(__featherGetState('count')) + 1));
    "#;

    #[test]
    fn actions_and_reload_keep_state_and_change_behavior() {
        let app = AppRuntime::new();
        app.load_javascript(APP.into()).unwrap();
        app.apply_action_event("increment");
        app.load_javascript(APP.replace("+ 1", "+ 2")).unwrap();
        app.apply_action_event("increment");
        let root: serde_json::Value =
            serde_json::from_str(&app.render_javascript().unwrap()).unwrap();
        assert_eq!(root["text"], "3");
    }

    #[test]
    fn identical_source_does_not_reset_the_interpreter() {
        let source = "let count = 0; globalThis.__featherRender = () => JSON.stringify({type:'Text',text:String(count)}); globalThis.__featherAction = () => count++;";
        let app = AppRuntime::new();
        app.load_javascript(source.into()).unwrap();
        app.apply_action_event("increment");
        app.load_javascript(source.into()).unwrap();
        assert!(app.render_javascript().unwrap().contains("\"1\""));
    }

    #[test]
    fn bad_reload_keeps_last_good_code_state_and_side_effects() {
        let app = AppRuntime::new();
        app.load_javascript(APP.into()).unwrap();
        assert!(app.load_javascript("__featherSetState('count','999'); __featherLog('uncommitted'); throw new Error('bad reload');".into()).is_err());
        assert_eq!(app.get_state("count"), Some("0".into()));
        assert!(app.drain_console_logs().is_empty());
        app.apply_action_event("increment");
        assert_eq!(app.get_state("count"), Some("1".into()));
    }

    #[test]
    fn render_errors_are_rejected_before_promotion() {
        let app = AppRuntime::new();
        app.load_javascript(APP.into()).unwrap();
        assert!(app.load_javascript("globalThis.__featherRender = () => 'not json'; globalThis.__featherAction = () => {};".into()).is_err());
        assert!(app.render_javascript().unwrap().contains("Text"));
    }

    #[test]
    fn runaway_actions_are_interrupted_and_reported() {
        let app = AppRuntime::new();
        app.load_javascript("globalThis.__featherRender = () => '{\"type\":\"Text\"}'; globalThis.__featherAction = () => {while(true){}};".into()).unwrap();
        let start = std::time::Instant::now();
        app.apply_action_event("runaway");
        assert!(start.elapsed() < std::time::Duration::from_secs(5));
        assert!(!app.drain_diagnostics().is_empty());
        assert!(app.render_javascript().unwrap().contains("Text"));
    }
}

#[cfg(test)]
mod trust_tests {
    use crate::{AppRuntime, artifact_for};
    use base64::Engine;
    use ring::signature::KeyPair;

    #[test]
    fn signed_loading_rejects_tampering_and_preserves_the_live_app() {
        let key = ring::signature::Ed25519KeyPair::generate_pkcs8(&ring::rand::SystemRandom::new())
            .unwrap();
        let key = ring::signature::Ed25519KeyPair::from_pkcs8(key.as_ref()).unwrap();
        let source = "globalThis.__featherRender = () => '{\"type\":\"Text\",\"text\":\"trusted\"}'; globalThis.__featherAction = () => {};";
        let mut manifest = serde_json::json!({
            "name":"test", "bundle_version":0, "sdk_version":"1.0.0", "protocol_version":1,
            "runtime":"kotlin-js", "javascript": artifact_for("app.js", source.as_bytes()),
            "render": artifact_for("app.feather.json", b"{}"), "assets":[], "fonts":[], "modules":[], "dependencies":[],
            "delta":{"changed":[], "removed":[]}, "metadata": {"app_id":null,"version_name":null,"version_code":null,"display_name":null},
            "deep_links":[], "platform_overrides":{}
        });
        let signature = key.sign(&serde_json::to_vec(&manifest).unwrap());
        manifest["signature"] =
            serde_json::json!(base64::prelude::BASE64_STANDARD.encode(signature));
        let public = base64::prelude::BASE64_STANDARD.encode(key.public_key());
        let app = AppRuntime::new();
        app.load_signed_javascript(manifest.to_string(), source.into(), public.clone())
            .unwrap();
        assert!(
            app.load_signed_javascript(
                manifest.to_string(),
                source.to_string() + "// changed",
                public.clone()
            )
            .is_err()
        );
        manifest["protocol_version"] = serde_json::json!(2);
        assert!(
            app.load_signed_javascript(manifest.to_string(), source.into(), public)
                .is_err()
        );
        assert!(app.render_javascript().unwrap().contains("trusted"));
    }
}

#[cfg(test)]
mod event_tests {
    use crate::AppRuntime;

    #[test]
    fn native_events_reach_javascript_before_the_next_document() {
        let app = AppRuntime::new();
        app.load_javascript(r#"
            globalThis.__featherEvents = events => __featherSetState('event', JSON.parse(events)[0].name);
            globalThis.__featherRender = () => JSON.stringify({type:'Text', text:__featherGetState('event') || 'none'});
            globalThis.__featherAction = () => {};
        "#.into()).unwrap();
        app.push_native_event("onResume".into(), "{}".into());
        let document: serde_json::Value =
            serde_json::from_str(&app.render_javascript_document().unwrap()).unwrap();
        assert_eq!(document["type"], "render");
        assert_eq!(document["root"]["text"], "onResume");
        assert!(app.drain_native_events().is_empty());
    }
}

#[cfg(test)]
mod module_action_tests {
    use crate::AppRuntime;

    #[test]
    fn declarative_module_actions_dispatch_before_app_handlers_with_unique_ids() {
        let app = AppRuntime::new();
        app.load_javascript("globalThis.__featherRender=()=>'{\"type\":\"Text\"}'; globalThis.__featherAction=()=>{throw new Error('must not receive module descriptor')};".into()).unwrap();
        let descriptor = serde_json::json!({"type":"module_call","module":"feather-network","method":"get","payload":{"url":"https://example.invalid"},"resultStateKey":"response"});
        let event =
            serde_json::json!({"type":"action","action":descriptor.to_string()}).to_string();
        app.apply_action_event(&event);
        app.apply_action_event(&event);
        let calls = app.drain_module_calls();
        assert_eq!(calls.len(), 2);
        let first: serde_json::Value = serde_json::from_str(&calls[0]).unwrap();
        let second: serde_json::Value = serde_json::from_str(&calls[1]).unwrap();
        assert_ne!(first["id"], second["id"]);
        assert_eq!(first["module"], "feather-network");
        assert_eq!(first["resultStateKey"], "response");
        assert!(app.drain_diagnostics().is_empty());
    }
}
