package dev.feather.sdk

/** Annotation for importing other Feather files or modules. */
@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Import(val path: String)

/** Annotation for declaring a custom UI component. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Composable

/** Marks an API as experimental and subject to change. */
@RequiresOptIn(message = "This Feather API is experimental and may change in the future.", level = RequiresOptIn.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalFeatherApi

/** Declare a reactive state key with a default value. */
class State<T>(
    val key: String,
    val default: T,
)

/** Base class for Feather applications. */
abstract class FeatherApp {
    open fun onCreate() {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}

    /** Renders the current UI state. */
    abstract fun render(): Widget

    /** Handles user actions sent from the host app. */
    open fun handleAction(action: String, payload: String?) {}
}