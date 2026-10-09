# Changelog

## Unreleased

## 0.1.3

### Changed

- Open a file parameter's parent directory explicitly, honor it over chooser history, and accept quoted paths.
- Add an unchecked-by-default option to retain the parameter dialog after running; parameterless scripts continue to run directly.

## 0.1.2

### Changed

- Automatically publish version increases on main to JetBrains Marketplace after all CI builds and tests succeed.
- Recommend lowercase var declarations in templates and documentation while retaining Var and pVal compatibility.
- Embed template parameter inputs and live substitution results below the source code; refresh instantly on text, choice, or path changes.
- Display detected default executable paths in environment settings without saving overrides; default to Git Bash on Windows.
- Show template help in a split view with a selectable template list and automatically updated syntax details.
- Keep execution parameter dialogs open for repeated runs without blocking IDE operations; retain accept-and-close behavior for template previews.
- Prefer valid input paths in file/directory choosers, falling back to the current project root.
- Confirm deletion of the selected script with Delete (or Backspace on macOS).
- Persist script sorting by launch frequency, addition order, or name; preserve addition order when editing.
- Fix file chooser listener loading on Android Studio 253 by avoiding an override of the now-final getProject method.
- Use the standard Gradle build/assemble lifecycle to package the plugin, add a shared Build Plugin run configuration, and remove platform-specific build wrappers.
- Remove the device-mode selector; automatically pass available IDE device selection to scripts without requiring Android or adb for ordinary scripts. Retain legacy JSON fields and explicit device prerequisites.
- Normalize NikoTools branding, including resource paths and build artifacts.
- Rewrite all 19 plugin implementation classes in Kotlin while preserving plugin registration, stored settings and PopTool JSON compatibility.
- Use the platform-provided Kotlin runtime with Kotlin 1.9 language/API compatibility for the 242 baseline.
- Replace manual Swing layouts with official Kotlin UI DSL and bound settings forms.
- Move JUnit entry points to Kotlin and add migration checks for legacy JSON, persistent XML and extension constructors.
- Update the isolated Windows smoke launcher to package Kotlin implementation classes.

## 0.1.1

### Changed

- Compile against IntelliJ Platform 242 (IDEA 2024.2) with Java 21.
- Isolate newer Terminal and Android APIs behind optional compatibility adapters.
- Register the Reworked Terminal menu only when that menu exists.

## 0.1.0

### Added

- Custom script management, parameter forms, import/export and execution console.
- Optional Terminal selection and Android device integration.
- Gradle Wrapper build, tests, IDE sandbox, compatibility verification and Marketplace publishing tasks.
