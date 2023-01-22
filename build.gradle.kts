plugins {
    java
    id("org.graalvm.buildtools.native") version "0.9.4"
}

group = "com.codetinkerer.servedir"
version = "0.1-SNAPSHOT"

val mainClassName = "com.codetinkerer.servedir.ServeDir"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-codec-http:4.1.87.Final")
    implementation("org.slf4j:slf4j-api:2.0.3")
    implementation("ch.qos.logback:logback-classic:1.4.4")
}

tasks.jar {
    manifest.attributes["Main-Class"] = mainClassName
    val dependencies = configurations.runtimeClasspath.get()
        .map { zipTree(it) }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

nativeBuild {
    mainClass.set(mainClassName)
    verbose.set(true)
    useFatJar.set(false)

    buildArgs.add("-H:DashboardDump=servedir.dump")
    buildArgs.add("-H:+DashboardAll")
    buildArgs.add("-H:+StaticExecutableWithDynamicLibC")
    buildArgs.add("--initialize-at-build-time=org.slf4j")
    buildArgs.add("--initialize-at-build-time=ch.qos.logback")
}
