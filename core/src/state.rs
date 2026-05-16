use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

/// Typed state values that go beyond simple String counters.
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(untagged)]
pub enum StateValue {
    Null,
    Bool(bool),
    Int(i64),
    Float(f64),
    Str(String),
    List(Vec<StateValue>),
    Map(HashMap<String, StateValue>),
}

pub fn get_nested_state<'a>(state: &'a HashMap<String, StateValue>, path: &str) -> Option<&'a StateValue> {
    if let Some(val) = state.get(path) {
        return Some(val);
    }
    let normalized = path.replace('[', ".").replace(']', "");
    let mut parts = normalized.split('.');
    let first = parts.next()?;
    let mut current = state.get(first)?;

    for part in parts {
        if part.is_empty() { continue; }
        match current {
            StateValue::Map(m) => { current = m.get(part)?; }
            StateValue::List(l) => {
                if let Ok(idx) = part.parse::<usize>() { current = l.get(idx)?; } else { return None; }
            }
            _ => return None,
        }
    }
    Some(current)
}

impl StateValue {
    pub fn as_str(&self) -> String {
        match self {
            StateValue::Null => "null".to_string(),
            StateValue::Bool(b) => b.to_string(),
            StateValue::Int(n) => n.to_string(),
            StateValue::Float(f) => f.to_string(),
            StateValue::Str(s) => s.clone(),
            StateValue::List(items) => serde_json::to_string(items).unwrap_or_default(),
            StateValue::Map(map) => serde_json::to_string(map).unwrap_or_default(),
        }
    }

    pub fn as_bool(&self) -> bool {
        match self {
            StateValue::Null => false,
            StateValue::Bool(b) => *b,
            StateValue::Int(n) => *n != 0,
            StateValue::Float(f) => *f != 0.0,
            StateValue::Str(s) => !s.is_empty() && s != "false" && s != "0",
            StateValue::List(items) => !items.is_empty(),
            StateValue::Map(map) => !map.is_empty(),
        }
    }

    pub fn as_int(&self) -> i64 {
        match self {
            StateValue::Null => 0,
            StateValue::Bool(b) => if *b { 1 } else { 0 },
            StateValue::Int(n) => *n,
            StateValue::Float(f) => *f as i64,
            StateValue::Str(s) => s.parse().unwrap_or(0),
            StateValue::List(items) => items.len() as i64,
            StateValue::Map(map) => map.len() as i64,
        }
    }

    pub fn as_float(&self) -> f64 {
        match self {
            StateValue::Null => 0.0,
            StateValue::Bool(b) => if *b { 1.0 } else { 0.0 },
            StateValue::Int(n) => *n as f64,
            StateValue::Float(f) => *f,
            StateValue::Str(s) => s.parse().unwrap_or(0.0),
            _ => 0.0,
        }
    }

    pub fn as_list(&self) -> Vec<StateValue> {
        match self {
            StateValue::List(items) => items.clone(),
            _ => Vec::new(),
        }
    }

    pub fn from_json_value(value: &Value) -> StateValue {
        match value {
            Value::Null => StateValue::Null,
            Value::Bool(b) => StateValue::Bool(*b),
            Value::Number(n) => {
                if let Some(i) = n.as_i64() {
                    StateValue::Int(i)
                } else {
                    StateValue::Float(n.as_f64().unwrap_or(0.0))
                }
            }
            Value::String(s) => StateValue::Str(s.clone()),
            Value::Array(arr) => {
                StateValue::List(arr.iter().map(StateValue::from_json_value).collect())
            }
            Value::Object(map) => {
                let mut result = HashMap::new();
                for (key, val) in map {
                    result.insert(key.clone(), StateValue::from_json_value(val));
                }
                StateValue::Map(result)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_msgpack_roundtrip() {
        let mut map = HashMap::new();
        map.insert("name".to_string(), StateValue::Str("Feather".to_string()));
        map.insert("version".to_string(), StateValue::Int(2));
        map.insert("active".to_string(), StateValue::Bool(true));
        
        let state = StateValue::Map(map);
        let bytes = rmp_serde::to_vec(&state).unwrap();
        let decoded: StateValue = rmp_serde::from_slice(&bytes).unwrap();
        
        assert_eq!(state, decoded);
    }
}
