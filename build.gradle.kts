plugins {
    java
}

group = "com.codetinkerer.servedir"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.codetinkerer.servedir.ServeDir"
    val dependencies = configurations.runtimeClasspath.get()
        .map { zipTree(it) }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
