plugins {
    java
}

group = "com.maris7"
version = "1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.1")
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.7")
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    runtimeOnly("org.xerial:sqlite-jdbc:3.53.0.0")
    runtimeOnly("com.mysql:mysql-connector-j:9.7.0")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("MarisAuction")
    archiveVersion.set("")
}


