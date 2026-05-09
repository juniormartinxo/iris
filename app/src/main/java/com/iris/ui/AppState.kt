package com.iris.ui

enum class AppMode {
    CONTINUOUS, QUESTION, READING
}

sealed interface AppPhase {
    data object Idle : AppPhase
    data object LoadingModel : AppPhase
    data object Listening : AppPhase
    data object Capturing : AppPhase
    data object Inferring : AppPhase
    data object Speaking : AppPhase
    data class FatalError(val message: String) : AppPhase
}

data class AppState(
    val mode: AppMode = AppMode.CONTINUOUS,
    val phase: AppPhase = AppPhase.LoadingModel,
    val lastDescription: String = "",
    val modelVariant: String? = null,
    val tutorialDone: Boolean = false,
    val preflightDone: Boolean = false,
    val micPermissionDenied: Boolean = false,
)
