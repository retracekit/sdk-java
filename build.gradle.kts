plugins {
	kotlin("jvm") version "2.0.21"
	`java-library`
	`maven-publish`
}

group = "cloud.retracekit"
version = "0.1.0"
description = "Lightweight JVM error tracking SDK for Retrace Kit"

repositories {
	mavenCentral()
}

java {
	withSourcesJar()
	withJavadocJar()
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

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			groupId = "cloud.retracekit"
			artifactId = "sdk"
			version = project.version.toString()
			pom {
				name.set("Retrace Kit SDK")
				description.set(project.description)
				url.set("https://retracekit.cloud")
				licenses {
					license {
						name.set("Apache License 2.0")
						url.set("https://www.apache.org/licenses/LICENSE-2.0")
					}
				}
				developers {
					developer {
						name.set("Ildar Timerbaev")
					}
				}
				scm {
					connection.set("scm:git:git://github.com/RetraceKit/sdk-java.git")
					developerConnection.set("scm:git:ssh://git@github.com/RetraceKit/sdk-java.git")
					url.set("https://github.com/RetraceKit/sdk-java")
				}
				issueManagement {
					system.set("GitHub")
					url.set("https://github.com/RetraceKit/sdk-java/issues")
				}
			}
		}
	}
}
