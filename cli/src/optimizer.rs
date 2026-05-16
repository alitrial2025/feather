use crate::models::WidgetNode;
use std::collections::{HashMap, HashSet};

/// Ahead-Of-Time optimizer for Feather UI trees.
pub struct UiOptimizer;

impl UiOptimizer {
    pub fn optimize(root: WidgetNode, state: &mut HashMap<String, String>) -> WidgetNode {
        let mut optimized = root;
        
        // 1. Flatten redundant containers
        optimized = Self::flatten_containers(optimized);
        
        // 2. Prune unused state variables
        Self::prune_state(&optimized, state);
        
        optimized
    }

    fn flatten_containers(node: WidgetNode) -> WidgetNode {
        match node {
            WidgetNode::Column { mut children, style } => {
                children = children.into_iter().map(Self::flatten_containers).collect();
                if children.len() == 1 && style.is_none() {
                    if let WidgetNode::Column { children: inner_children, style: inner_style } = children.remove(0) {
                        return WidgetNode::Column { children: inner_children, style: inner_style };
                    }
                }
                WidgetNode::Column { children, style }
            }
            WidgetNode::Row { mut children, style } => {
                children = children.into_iter().map(Self::flatten_containers).collect();
                if children.len() == 1 && style.is_none() {
                    if let WidgetNode::Row { children: inner_children, style: inner_style } = children.remove(0) {
                        return WidgetNode::Row { children: inner_children, style: inner_style };
                    }
                }
                WidgetNode::Row { children, style }
            }
            WidgetNode::Box { mut children, style } => {
                children = children.into_iter().map(Self::flatten_containers).collect();
                if children.len() == 1 && style.is_none() {
                    return children.remove(0);
                }
                WidgetNode::Box { children, style }
            }
            _ => node
        }
    }

    fn prune_state(root: &WidgetNode, state: &mut HashMap<String, String>) {
        let mut used_keys = HashSet::new();
        Self::collect_used_keys(root, &mut used_keys);
        
        // Keep only used keys
        state.retain(|k, _| used_keys.contains(k));
    }

    fn collect_used_keys(node: &WidgetNode, used: &mut HashSet<String>) {
        match node {
            WidgetNode::StateText { key, .. } => { used.insert(key.clone()); }
            WidgetNode::Condition { key, then, otherwise } => {
                used.insert(key.clone());
                Self::collect_used_keys(then, used);
                if let Some(o) = otherwise { Self::collect_used_keys(o, used); }
            }
            WidgetNode::ForEach { items_key, template } => {
                used.insert(items_key.clone());
                Self::collect_used_keys(template, used);
            }
            WidgetNode::Column { children, .. } | WidgetNode::Row { children, .. } | WidgetNode::Box { children, .. } => {
                for child in children { Self::collect_used_keys(child, used); }
            }
            WidgetNode::Scaffold { body, .. } => { Self::collect_used_keys(body, used); }
            // Actions might use state too (e.g. Button(action="count++"))
            WidgetNode::Button { action, .. } | WidgetNode::TextInput { action, .. } => {
                // Heuristic: any identifier-looking string might be a state key
                for part in action.split(|c: char| !c.is_alphanumeric() && c != '_') {
                    if !part.is_empty() { used.insert(part.to_string()); }
                }
            }
            _ => {}
        }
    }
}
