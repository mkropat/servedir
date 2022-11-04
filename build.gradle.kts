plugins {
    java
}

group = "com.codetinkerer.servedir"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-codec-http:4.1.87.Final")
    implementation("org.slf4j:slf4j-api:2.0.3")
    implementation("ch.qos.logback:logback-classic:1.4.4")
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.codetinkerer.servedir.ServeDir"
    val dependencies = configurations.runtimeClasspath.get()
        .map { zipTree(it) }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
