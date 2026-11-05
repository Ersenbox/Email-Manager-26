import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.kotlin.compose)
  id("org.jetbrains.compose") version "1.9.0"
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

// Çeviri dosyası Android ile ortaktır
sourceSets.main {
  resources.srcDir("../app/src/main/assets")
  kotlin.srcDir("../shared-ui/src")
}

compose.desktop {
  application {
    mainClass = "com.example.desktop.MainKt"
    nativeDistributions {
      targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
      packageName = "Email-Manager-26"
      packageVersion = "1.0.0"
      vendor = "Ersenbox"
      modules("java.naming", "java.sql", "java.desktop", "jdk.crypto.ec")
      windows {
        menuGroup = "Email-Manager 26"
        shortcut = true
        menu = true
        upgradeUuid = "6f3c1d56-0a3e-4e0c-9d57-2a1b7c4e26a1"
      }
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.generateKotlin", "true")
}

dependencies {
  implementation(project(":core"))
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
  implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
  implementation("androidx.room:room-runtime:2.7.0")
  implementation("androidx.sqlite:sqlite-bundled:2.5.0")
  implementation("com.sun.mail:javax.mail:1.6.2")
  implementation("net.java.dev.jna:jna-platform:5.14.0")
  implementation(libs.kotlinx.coroutines.core)
  "ksp"(libs.androidx.room.compiler)

  testImplementation(libs.junit)
  testImplementation("com.icegreen:greenmail:1.6.15")
}
