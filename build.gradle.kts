plugins {
	// Important to add shadow plugin first
	id("com.gradleup.shadow") version "8.3.5"
	id("qupath-conventions")
}

qupathExtension {
	name = "qupath-extension-jpen"
	version = "0.4.0-SNAPSHOT"
	group = "io.github.qupath"
	description = "QuPath extension to support graphic tablet input using JPen"
	automaticModule = "qupath.extension.jpen"
}

val libName = "jpen-2.0.0"
val libZipped = file("libs/${libName}-lib.zip")
val libUnzipped = project.layout.buildDirectory.file("unpacked/")

tasks.register<Copy>("extractLibs") {
	description = "Extract JPen library (required before building)"
	group = "QuPath"

	from(zipTree(libZipped))
	into(libUnzipped)
}

tasks.compileJava.configure {
	dependsOn("extractLibs")
}

tasks.shadowJar {
	from(project.layout.buildDirectory.file("unpacked/${libName}")) {
		into("natives/")

		include("'*.dll")
		include("*.jnilib")
		include("*.so")
	}
}

dependencies {

	implementation(fileTree(project.layout.buildDirectory.file("unpacked/${libName}")) { include("jpen-2.jar") })

	shadow(libs.bundles.qupath)
	shadow(libs.bundles.logging)
	implementation(libs.extensionmanager)

	// For testing
	testImplementation(libs.bundles.qupath)
	testImplementation(libs.junit)

}

