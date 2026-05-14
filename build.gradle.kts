import com.diffplug.spotless.LineEnding

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(ktorLibs.plugins.ktor)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.spotless)
  jacoco
}

group = "com.qlink"
version = "1.0.0-SNAPSHOT"

application {
  mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
  jvmToolchain(21)
}

spotless {
  lineEndings = LineEnding.UNIX

  val ktlintEditorConfig =
    mapOf(
      "indent_size" to "2",
      "max_line_length" to "100",
      "tab_width" to "2",
    )

  kotlin {
    target("src/**/*.kt")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
    ktlint().editorConfigOverride(ktlintEditorConfig)
  }

  kotlinGradle {
    target("*.gradle.kts", "**/*.gradle.kts")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
    ktlint().editorConfigOverride(ktlintEditorConfig)
  }

  format("misc") {
    target(
      "*.md",
      "*.yml",
      "*.yaml",
      "*.sql",
      "src/**/*.sql",
      "src/**/*.yaml",
      "gradle/**/*.toml",
    )
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
  }
}

val jacocoCoverageClassDirectories =
  files(
    layout.buildDirectory
      .dir("classes/kotlin/main")
      .map {
        fileTree(it) {
          include("**/domain/**", "**/service/**")
        }
      },
  )

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  classDirectories.setFrom(jacocoCoverageClassDirectories)

  reports {
    html.required.set(true)
    xml.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("jacocoReports/jacoco.html"))
    xml.outputLocation.set(layout.buildDirectory.file("jacocoReports/jacoco.xml"))
  }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.test)
  classDirectories.setFrom(jacocoCoverageClassDirectories)

  violationRules {
    rule {
      element = "CLASS"

      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.5".toBigDecimal()
      }

      limit {
        counter = "BRANCH"
        value = "COVEREDRATIO"
        minimum = "0.5".toBigDecimal()
      }
    }
  }
}

tasks.check {
  dependsOn(tasks.jacocoTestCoverageVerification)
}

dependencies {
  // Code
  implementation(ktorLibs.server.core)
  implementation(ktorLibs.server.config.yaml)
  implementation(ktorLibs.server.netty)

  // Http
  implementation(ktorLibs.serialization.kotlinx.json)
  implementation(ktorLibs.server.compression)
  implementation(ktorLibs.server.contentNegotiation)
  implementation(ktorLibs.server.cors)
  implementation(ktorLibs.server.defaultHeaders)
  implementation(ktorLibs.server.requestValidation)
  implementation(ktorLibs.server.resources)
  implementation(ktorLibs.server.statusPages)

  // Auth
  implementation(ktorLibs.server.auth)
  implementation(ktorLibs.server.auth.jwt)

  // DI
  implementation(libs.koin.ktor)
  implementation(libs.koin.loggerSlf4j)

  // Persistence
  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.java.time)
  implementation(libs.postgresql)
  implementation(libs.hikaricp)

  // Migration
  implementation(libs.flyway.core)
  implementation(libs.flyway.database.postgresql)

  // Monitoring
  implementation(ktorLibs.server.callLogging)
  implementation(ktorLibs.server.metrics.micrometer)
  implementation(libs.logback.classic)
  implementation(libs.micrometer.registryPrometheus)

  // Documentation
  implementation("io.github.smiley4:ktor-openapi:5.7.0")
  implementation("io.github.smiley4:ktor-swagger-ui:5.7.0")
  implementation("io.github.smiley4:ktor-redoc:5.7.0")
  implementation("io.github.smiley4:schema-kenerator-core:2.7.2")
  implementation("io.github.smiley4:schema-kenerator-serialization:2.7.2")
  implementation("io.github.smiley4:schema-kenerator-swagger:2.7.2")

  // Test
  testImplementation(kotlin("test"))
  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(ktorLibs.server.testHost)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.postgresql)
}
