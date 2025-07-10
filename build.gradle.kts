plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.admctrl"
version = "1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.admctrl.Main")
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("groupmerger")
    archiveVersion.set("1.0")
    archiveClassifier.set("")

    manifest {
        attributes("Main-Class" to "com.admctrl.Main")
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.distZip {
    dependsOn(tasks.shadowJar)
}

tasks.distTar {
    dependsOn(tasks.shadowJar)
}

tasks.startScripts {
    dependsOn(tasks.shadowJar)
}
