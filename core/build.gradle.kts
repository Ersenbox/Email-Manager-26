import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.jvm)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
  api("androidx.room:room-common:2.7.0")
  api(libs.kotlinx.coroutines.core)
  api(libs.okhttp)
  // Posta kütüphanesini her platform kendisi ekler: Android android-mail, masaüstü javax.mail
  compileOnly("com.sun.mail:android-mail:1.6.7")
  compileOnly("com.sun.mail:android-activation:1.6.7")
}
