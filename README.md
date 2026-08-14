<p align="center">
  <img src="docs/assets/logo.png" alt="Retrace Kit" width="96" />
</p>

# retrace-kit (JVM)

[![license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/RetraceKit/sdk-java/blob/main/LICENSE)
[![java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://github.com/RetraceKit/sdk-java)

Lightweight error tracking for Java and Kotlin on the JVM.  
Catch exceptions, attach context, and send them to [Retrace Kit](https://retracekit.cloud).

<p align="center">
  <img src="docs/assets/dashboard.png" alt="Retrace Kit incident dashboard" width="920" />
</p>

## Features

- Auto-capture uncaught exceptions via `Thread.setDefaultUncaughtExceptionHandler`
- Manual reporting with `captureException`
- Breadcrumbs, user context, and tags
- Session ping on init
- Kotlin source, Java 17 bytecode — works from Java and Kotlin
- Only runtime dependency: `kotlin-stdlib`

## Install

Maven Central publishing is coming soon. Coordinates:

```text
cloud.retracekit:sdk:0.1.0
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("cloud.retracekit:sdk:0.1.0")
}
```

Maven:

```xml
<dependency>
  <groupId>cloud.retracekit</groupId>
  <artifactId>sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

Until the package is on Maven Central, publish locally:

```bash
./gradlew publishToMavenLocal
```

## Kotlin

```kotlin
import com.retracekit.sdk.Config
import com.retracekit.sdk.RetraceKit

RetraceKit.init(
    Config(
        apiKey = System.getenv("RETRACE_KIT_API_KEY")!!,
        endpoint = System.getenv("RETRACE_KIT_ENDPOINT")
            ?: "https://api.retracekit.cloud/api/error-events",
        serverUrl = System.getenv("RETRACE_KIT_SERVER_URL"),
        environment = System.getenv("RETRACE_KIT_ENVIRONMENT"),
        release = System.getenv("RETRACE_KIT_RELEASE"),
    ),
)
```

Requires Java 17+.  
Uncaught errors are reported automatically after `init`.  
Events use `userAgent` like `Java 21.0.2` and tag `runtime=java`.

## Java

```java
import com.retracekit.sdk.Config;
import com.retracekit.sdk.RetraceKit;

RetraceKit.init(
    Config.builder()
        .apiKey(System.getenv("RETRACE_KIT_API_KEY"))
        .endpoint("https://api.retracekit.cloud/api/error-events")
        .environment("production")
        .release("1.0.0")
        .build()
);
```

## API

| Export | Description |
| --- | --- |
| `RetraceKit.init` | Initialize with API key and options |
| `RetraceKit.captureException` | Report handled errors |
| `RetraceKit.addBreadcrumb` | Add context before an error |
| `RetraceKit.setUser` | Attach a user id |
| `RetraceKit.setTag` | Set a key/value tag |

```kotlin
import com.retracekit.sdk.BreadcrumbType
import com.retracekit.sdk.Config
import com.retracekit.sdk.RetraceKit

RetraceKit.init(
    Config(
        apiKey = System.getenv("RETRACE_KIT_API_KEY")!!,
        environment = "production",
        release = "1.2.3",
    ),
)

RetraceKit.setUser("user_123")
RetraceKit.setTag("plan", "pro")
RetraceKit.addBreadcrumb(
    type = BreadcrumbType.COMMON,
    name = "checkout",
    value = "started",
)

try {
    checkout()
} catch (error: Throwable) {
    RetraceKit.captureException(error)
}
```

See `examples/basic/Main.kt`.

## Documentation

**https://retracekit.cloud/docs/**

Repository: [github.com/RetraceKit/sdk-java](https://github.com/RetraceKit/sdk-java)

## Development

```bash
./gradlew test
./gradlew publishToMavenLocal
```

## Publishing

Releases go to Maven Central from GitHub Actions on `v*` tags (`v0.1.0` → `0.1.0`).

Set these repository secrets before the first tag:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `SIGNING_IN_MEMORY_KEY` | ASCII-armored private key (`gpg --export-secret-keys --armor <key id>`), including BEGIN/END lines |
| `SIGNING_IN_MEMORY_KEY_ID` | Last 8 hex chars of the key id (e.g. `7B81DFCC`). Longer ids are trimmed in CI. |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Key passphrase |

```bash
git tag v0.1.0
git push origin v0.1.0
```

## License

Apache-2.0
