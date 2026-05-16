# Fallback Parser Limits

The Feather CLI currently includes a fallback parser (`cli/src/parser.rs`) and the KSP plugin (`FeatherSymbolProcessor`) includes a source scanner that attempts to extract UI structure from Kotlin code.

Both of these approaches use fragile string/regex scanning rather than a full Abstract Syntax Tree (AST). This document outlines the limits of these fallback parsers.

## Limitations

1. **No Real Symbol Resolution**: The parser matches strings like `Text(...)` and `Button(...)`. If you define a local variable named `Text`, or import a different `Text` class, the parser will still treat it as a UI widget.
2. **Limited Nesting Support**: While basic bracket matching exists, deep nesting or complex lambdas (especially trailing lambdas mixed with named parameters) can confuse the scanner.
3. **Control Flow Constraints**: `if/else` statements are somewhat supported, but `when`, `for` loops, and complex boolean expressions within conditionals are not fully parsed.
4. **Function Components**: The scanner attempts to find `fun` or `@Composable` declarations but does not fully understand scope, parameters, or return types.
5. **No Type Checking**: Expressions are evaluated via a custom basic runtime (`resolve_expression`), which only understands basic strings, ints, and variable substitution. It cannot call Kotlin standard library functions.
6. **No Proper Error Diagnostics**: Syntax errors in the UI code may result in silently ignored widgets or mismatched brackets rather than precise line/column compilation errors.

## Mitigation

The fallback parser exists purely as a **development escape hatch** to keep the CLI fast during hot-reloads without constantly invoking a full Gradle build. For production builds, or when relying on complex custom components, Feather relies on either KSP-driven AST parsing or a full Kotlin/WASM runtime path.
