plugins {
    application
    java
}

group = "com.example"
version = "1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.example.desktop.Main")
}
