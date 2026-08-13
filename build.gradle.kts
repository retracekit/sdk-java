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
			}
		}
	}
}
