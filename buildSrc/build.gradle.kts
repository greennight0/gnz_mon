plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

dependencies {
  testImplementation(gradleTestKit())
  testImplementation(kotlin("test-junit5"))
}

tasks.test {
  useJUnitPlatform()
}
