plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.bg-software.com/repository/api/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.248-stable")
    compileOnly("com.bgsoftware:WildStackerAPI:2026.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
