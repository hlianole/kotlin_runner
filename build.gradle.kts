plugins {
    kotlin("jvm") version "2.0.20"
    id("org.openjfx.javafxplugin") version "0.0.10"
    application
}

group = "com.hlianole.guikotlin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:1.9.0")
    implementation("org.openjfx:javafx-controls:20.0.1")
    implementation("org.openjfx:javafx-graphics:17.0.2")
    implementation("org.fxmisc.richtext:richtextfx:0.11.4")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.9.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

javafx {
    version = "17.0.2"
    modules("javafx.controls", "javafx.graphics")
}

application {
    mainClass.set("com.hlianole.guikotlin.MainKt")
}
