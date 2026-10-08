# Changelog

## [Unreleased]

### Changed
- Fix file chooser listener loading on Android Studio 253 by avoiding an override of the now-final getProject method.
- Use the standard Gradle build/assemble lifecycle to package the plugin, add a shared Build Plugin run configuration, and remove platform-specific build wrappers.
- Remove the device-mode selector; automatically pass available IDE device selection to scripts without requiring Android or adb for ordinary scripts. Retain legacy JSON fields and explicit device prerequisites.
- Normalize NikoTools branding, including resource paths and build artifacts.
- Rewrite all 19 plugin implementation classes in Kotlin while preserving plugin registration, stored settings and PopTool JSON compatibility.
- Use the platform-provided Kotlin runtime with Kotlin 1.9 language/API compatibility for the 242 baseline.
- Replace manual Swing layouts with official Kotlin UI DSL and bound settings forms.
- Move JUnit entry points to Kotlin and add migration checks for legacy JSON, persistent XML and extension constructors.
- Update the isolated Windows smoke launcher to package Kotlin implementation classes.

## [0.1.1]

### Changed
- Compile against IntelliJ Platform 242 (IDEA 2024.2) with Java 21.
- Isolate newer Terminal and Android APIs behind optional compatibility adapters.
- Register the Reworked Terminal menu only when that menu exists.

## [0.1.0]

### Added
- Custom script management, parameter forms, import/export and execution console.
- Optional Terminal selection and Android device integration.
- Gradle Wrapper build, tests, IDE sandbox, compatibility verification and Marketplace publishing tasks.
