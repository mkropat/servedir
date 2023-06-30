plugins {
    application
    id("org.graalvm.buildtools.native") version "0.9.23"
}

group = "com.codetinkerer.servedir"
version = "0.1-SNAPSHOT"

val systemArchitecture = System.getProperty("os.arch")

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

val compressTask by tasks.register<Exec>("compress") {
    dependsOn("nativeCompile")

    val outputDir = File("$buildDir/native/compressed")
    inputs.file(File("$buildDir/native/nativeCompile/${project.name}"))
    outputs.file(outputDir.resolve(project.name))
    doFirst {
        outputDir.mkdirs()
        outputs.files.singleFile.delete()
    }
    commandLine("upx", "-o", outputs.files.singleFile.absolutePath, inputs.files.singleFile.absolutePath)
}

tasks.named("nativeCompile") {
    finalizedBy(compressTask)
}

tasks.register<Zip>("packageRelease") {
    dependsOn(compressTask)
    from("$buildDir/native/compressed")
    archiveFileName.set("${project.name}-${systemArchitecture}.zip")
}
