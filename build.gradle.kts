plugins {
    id("java")
    id("application")
}

group = "com.admctrl"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

application {
    mainClass.set("com.admctrl.Main")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.admctrl.Main"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
