plugins {
	kotlin("jvm") version "2.2.0"
	`java-library`
	id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "cloud.retracekit"
version = "0.1.0"
description = "Lightweight JVM error tracking SDK for Retrace Kit"

repositories {
	mavenCentral()
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

kotlin {
	jvmToolchain(17)
}

dependencies {
	testImplementation(kotlin("test-junit5"))
	testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generatedVersionDir = layout.buildDirectory.dir("generated/retrace-kit-version")

tasks.register("writeVersionResource") {
	val outDir = generatedVersionDir
	val ver = provider { project.version.toString() }
	inputs.property("version", ver)
	outputs.dir(outDir)
	doLast {
		val dir = outDir.get().asFile
		dir.mkdirs()
		dir.resolve("retrace-kit-version.txt").writeText(ver.get())
	}
}

tasks.processResources {
	dependsOn("writeVersionResource")
	from(generatedVersionDir)
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}

mavenPublishing {
	coordinates("cloud.retracekit", "sdk", version.toString())
	publishToMavenCentral(automaticRelease = true)
	if (hasSigningConfig()) {
		signAllPublications()
	}

	pom {
		name.set("Retrace Kit SDK")
		description.set(project.description)
		inceptionYear.set("2026")
		url.set("https://retracekit.cloud")
		licenses {
			license {
				name.set("The Apache License, Version 2.0")
				url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
				distribution.set("repo")
			}
		}
		developers {
			developer {
				id.set("dkildar")
				name.set("Ildar Timerbaev")
				url.set("https://github.com/dkildar")
			}
		}
		scm {
			url.set("https://github.com/RetraceKit/sdk-java")
			connection.set("scm:git:git://github.com/RetraceKit/sdk-java.git")
			developerConnection.set("scm:git:ssh://git@github.com/RetraceKit/sdk-java.git")
		}
		issueManagement {
			system.set("GitHub")
			url.set("https://github.com/RetraceKit/sdk-java/issues")
		}
	}
}

fun hasSigningConfig(): Boolean {
	val inMemoryKey = providers.gradleProperty("signingInMemoryKey")
	val keyId = providers.gradleProperty("signing.keyId")
	return inMemoryKey.isPresent || keyId.isPresent
}
