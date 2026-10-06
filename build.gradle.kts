plugins {
    java
}

group = "dev.ghiacciolo"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.bg-software.com/repository/api/")
}

// Pinned to the builds the plugin was tested with, so builds are reproducible.
val paperApiVersion = "26.2.build.129-stable"
val wildStackerApiVersion = "2026.2"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.bgsoftware:WildStackerAPI:$wildStackerApiVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
