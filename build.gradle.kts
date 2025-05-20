// Root project configuration
plugins {
    base
    // Apply spotless here to ensure the plugin is available for all subprojects
    alias(libs.plugins.spotless) apply false
}

// Apply common configuration to all subprojects
subprojects {
    // All subprojects will have common repositories
    repositories {
        mavenCentral()
    }
}
