// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-1.0.29" apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
        }
    }
}
