plugins {
    kotlin("jvm") version "1.9.22"
    antlr
}

group = "me.avrong.stella"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.generateGrammarSource {
    // Generate base visitor classes
    arguments.add("-visitor")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

sourceSets.configureEach {
    val generateGrammarSource = tasks.named(getTaskName("generate", "GrammarSource"))
    java.srcDir(generateGrammarSource.map { files() })
}