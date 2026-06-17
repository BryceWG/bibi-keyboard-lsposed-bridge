# BiBi IME Bridge

This is an advanced LSPosed/LSPatch bridge module for BiBi Keyboard.

It is intentionally kept as an independent Gradle project and standalone git
repository. It is excluded from the main BiBi Keyboard repository and is not
included by the main repository `settings.gradle.kts`. The main app remains
independent from Xposed APIs; this module only runs inside a hooked third-party
input method.

Publish this module from its own repository release flow. The built APK should
be attached to bridge-module releases instead of being committed to either repo.

## Usage

- Root LSPosed: install this APK, enable the module, scope it only to the target
  input method package, then restart that input method.
- LSPatch: patch the target input method APK with this module, install the
  patched input method, then select it as the current keyboard.

## Behavior

The module hooks `InputMethodService`, receives requests from the BiBi app, and
submits final ASR text through `InputConnection.commitText()`.

It also reports whether the hooked input method panel is currently visible, so
the main app can show or hide the floating ball without relying on Accessibility
for keyboard visibility detection.

It does not read user input, inject UI into the third-party keyboard, or handle
recording / ASR / post-processing.
