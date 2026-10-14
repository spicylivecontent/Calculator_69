plugins {
    alias(libs.plugins.android.application) apply false
    // Pins only the Kotlin version for AGP 9:s inbyggda Kotlin-stöd (tillämpas aldrig på en modul).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
