# ADR-001: Native Android with Kotlin and Compose

Status: Accepted for Pass 1.

## Context

Android is the first platform. The application needs a native lifecycle, accessible mobile UI, and a foundation suitable for later on-device translation. iPhone support is planned but its implementation is outside this pass.

## Decision

Build a native Android application with Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL, and a version catalog. Use exactly `:app` and a pure Kotlin/JVM `:core:domain`. Do not introduce a website, WebView, PWA, iOS target, or premature cross-platform infrastructure.

## Consequences

Android-specific code lives in `:app`; domain concepts remain free of Android and provider dependencies. Future iPhone work can revisit code sharing without forcing platform concerns into domain types. Native testing and SDK compatibility must be validated. User-visible branding stays in resources, and the application ID remains provisional until release preparation.
