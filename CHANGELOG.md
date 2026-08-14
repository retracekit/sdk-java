# Changelog

## 0.1.1

- Publish to Maven Central from GitHub Actions
- Harden GPG signing in CI (normalize armored key and 8-char key id)

## 0.1.0

- Initial JVM SDK (Kotlin, Java 17 bytecode)
- Auto-capture via default uncaught exception handler
- Manual `captureException`, breadcrumbs, tags, user, session ping
- Fire-and-forget HTTP transport to Retrace Kit ingest
