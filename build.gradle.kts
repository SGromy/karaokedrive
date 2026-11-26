buildscript {
    repositories { mavenCentral() }
    dependencies {
        // AGP 9 intègre Kotlin (2.2.10 par défaut) : on impose une version plus récente
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
