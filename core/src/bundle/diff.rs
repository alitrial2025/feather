use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Serialize, Deserialize, Clone, Debug, uniffi::Record)]
pub struct UiPatch {
    pub path: Vec<u32>,        // Path indices to the target node (e.g. [0, 2])
    pub op: String,            // "Replace", "UpdateProperty", "InsertChild", "RemoveChild"
    pub property: Option<String>, // Property name (e.g. "text", "style.color")
    pub value: String,         // New serialized value
}

pub fn diff_layouts(old_json: &str, new_json: &str) -> Vec<UiPatch> {
    let mut patches = Vec::new();
    
    let old_val: Value = match serde_json::from_str(old_json) {
        Ok(v) => v,
        Err(_) => return patches,
    };
    let new_val: Value = match serde_json::from_str(new_json) {
        Ok(v) => v,
        Err(_) => return patches,
    };

    let mut path = Vec::new();
    diff_values(&old_val, &new_val, &mut path, &mut patches);
    patches
}

fn diff_values(old_val: &Value, new_val: &Value, path: &mut Vec<u32>, patches: &mut Vec<UiPatch>) {
    if old_val == new_val {
        return;
    }

    match (old_val, new_val) {
        (Value::Object(old_obj), Value::Object(new_obj)) => {
            let old_type = old_obj.get("type").and_then(|v| v.as_str());
            let new_type = new_obj.get("type").and_then(|v| v.as_str());

            if old_type != new_type {
                // If the type is different, replace the whole node
                patches.push(UiPatch {
                    path: path.clone(),
                    op: "Replace".to_string(),
                    property: None,
                    value: new_val.to_string(),
                });
                return;
            }

            // Type is the same, compare properties except "children"
            let mut keys: std::collections::HashSet<&str> = old_obj.keys().map(|k| k.as_str()).collect();
            for k in new_obj.keys() {
                keys.insert(k.as_str());
            }

            for key in keys {
                if key == "children" {
                    continue;
                }

                let old_prop = old_obj.get(key);
                let new_prop = new_obj.get(key);

                if old_prop != new_prop {
                    if key == "style" {
                        // Recursively diff style keys to be granular
                        match (old_prop, new_prop) {
                            (Some(Value::Object(old_style)), Some(Value::Object(new_style))) => {
                                let mut style_keys: std::collections::HashSet<&str> = old_style.keys().map(|k| k.as_str()).collect();
                                for sk in new_style.keys() {
                                    style_keys.insert(sk.as_str());
                                }
                                for skey in style_keys {
                                    let old_s_val = old_style.get(skey);
                                    let new_s_val = new_style.get(skey);
                                    if old_s_val != new_s_val {
                                        patches.push(UiPatch {
                                            path: path.clone(),
                                            op: "UpdateProperty".to_string(),
                                            property: Some(format!("style.{}", skey)),
                                            value: new_s_val.unwrap_or(&Value::Null).to_string(),
                                        });
                                    }
                                }
                            }
                            _ => {
                                patches.push(UiPatch {
                                    path: path.clone(),
                                    op: "UpdateProperty".to_string(),
                                    property: Some("style".to_string()),
                                    value: new_prop.unwrap_or(&Value::Null).to_string(),
                                });
                            }
                        }
                    } else {
                        patches.push(UiPatch {
                            path: path.clone(),
                            op: "UpdateProperty".to_string(),
                            property: Some(key.to_string()),
                            value: new_prop.unwrap_or(&Value::Null).to_string(),
                        });
                    }
                }
            }

            // Compare children
            let old_children = old_obj.get("children").and_then(|v| v.as_array());
            let new_children = new_obj.get("children").and_then(|v| v.as_array());

            match (old_children, new_children) {
                (Some(old_arr), Some(new_arr)) => {
                    let old_len = old_arr.len();
                    let new_len = new_arr.len();

                    // Compare existing overlapping children
                    let common_len = std::cmp::min(old_len, new_len);
                    for i in 0..common_len {
                        path.push(i as u32);
                        diff_values(&old_arr[i], &new_arr[i], path, patches);
                        path.pop();
                    }

                    // Remove extra children from the end in reverse order
                    if old_len > new_len {
                        for i in (new_len..old_len).rev() {
                            patches.push(UiPatch {
                                path: path.clone(),
                                op: "RemoveChild".to_string(),
                                property: Some(i.to_string()),
                                value: "".to_string(),
                            });
                        }
                    }

                    // Insert new children
                    if new_len > old_len {
                        for i in old_len..new_len {
                            patches.push(UiPatch {
                                path: path.clone(),
                                op: "InsertChild".to_string(),
                                property: Some(i.to_string()),
                                value: new_arr[i].to_string(),
                            });
                        }
                    }
                }
                (None, Some(new_arr)) => {
                    // All children are new
                    for (i, child) in new_arr.iter().enumerate() {
                        patches.push(UiPatch {
                            path: path.clone(),
                            op: "InsertChild".to_string(),
                            property: Some(i.to_string()),
                            value: child.to_string(),
                        });
                    }
                }
                (Some(old_arr), None) => {
                    // All children removed
                    for i in (0..old_arr.len()).rev() {
                        patches.push(UiPatch {
                            path: path.clone(),
                            op: "RemoveChild".to_string(),
                            property: Some(i.to_string()),
                            value: "".to_string(),
                        });
                    }
                }
                (None, None) => {}
            }
        }
        _ => {
            // Primitive mismatch, replace the whole node
            patches.push(UiPatch {
                path: path.clone(),
                op: "Replace".to_string(),
                property: None,
                value: new_val.to_string(),
            });
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_diff_layouts_property_update() {
        let old = r##"{"type":"Text","text":"Hello World","style":{"color":"#000000"}}"##;
        let new = r##"{"type":"Text","text":"Hello Feather","style":{"color":"#ffffff","background":"#000000"}}"##;
        let patches = diff_layouts(old, new);

        assert_eq!(patches.len(), 3);
        
        let text_patch = patches.iter().find(|p| p.property.as_deref() == Some("text")).unwrap();
        assert_eq!(text_patch.op, "UpdateProperty");
        assert_eq!(text_patch.value, "\"Hello Feather\"");

        let color_patch = patches.iter().find(|p| p.property.as_deref() == Some("style.color")).unwrap();
        assert_eq!(color_patch.op, "UpdateProperty");
        assert_eq!(color_patch.value, "\"#ffffff\"");

        let bg_patch = patches.iter().find(|p| p.property.as_deref() == Some("style.background")).unwrap();
        assert_eq!(bg_patch.op, "UpdateProperty");
        assert_eq!(bg_patch.value, "\"#000000\"");
    }

    #[test]
    fn test_diff_layouts_child_insert_and_remove() {
        let old = r##"{"type":"Column","children":[{"type":"Text","text":"One"},{"type":"Text","text":"Two"}]}"##;
        let new = r##"{"type":"Column","children":[{"type":"Text","text":"One"},{"type":"Text","text":"Three"},{"type":"Text","text":"Four"}]}"##;
        let patches = diff_layouts(old, new);

        // Children:
        // Index 0: same
        // Index 1: "Two" vs "Three" -> property "text" changed
        // Index 2: inserted -> "Four"
        assert_eq!(patches.len(), 2);
        
        // Property update on index 1
        assert_eq!(patches[0].path, vec![1]);
        assert_eq!(patches[0].op, "UpdateProperty");
        assert_eq!(patches[0].property.as_deref(), Some("text"));
        assert_eq!(patches[0].value, "\"Three\"");

        // Insert on root path
        assert!(patches[1].path.is_empty());
        assert_eq!(patches[1].op, "InsertChild");
        assert_eq!(patches[1].property.as_deref(), Some("2"));
        assert!(patches[1].value.contains("Four"));
    }
}

