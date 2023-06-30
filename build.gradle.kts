plugins {
    application
    id("org.graalvm.buildtools.native") version "0.9.23"
}

group = "com.codetinkerer.servedir"
version = "0.1-SNAPSHOT"

application {
    mainClass.set("com.codetinkerer.servedir.ServeDir")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-codec-http:4.1.94.Final")
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.8")
}

tasks.jar {
    manifest.attributes["Main-Class"] = application.mainClass
    val dependencies = configurations.runtimeClasspath.get()
        .map { zipTree(it) }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

graalvmNative {
    binaries.all {
        resources.autodetect()
    }
    toolchainDetection.set(false)

    binaries {
        named("main") {
            verbose.set(true)

            buildArgs.add("-H:DashboardDump=servedir.dump")
            buildArgs.add("-H:+DashboardAll")
            buildArgs.add("-H:+StaticExecutableWithDynamicLibC")
            buildArgs.add("--initialize-at-build-time=org.slf4j")
            buildArgs.add("--initialize-at-build-time=ch.qos.logback")
        }
    }
}
