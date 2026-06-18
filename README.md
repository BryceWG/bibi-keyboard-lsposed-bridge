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

The module hooks `InputMethodService`, receives requests from the BiBi app,
shows streaming ASR preview through `InputConnection.setComposingText()`, and
submits final ASR text through `InputConnection.commitText()` or by replacing
the active composing preview and finishing composition.

It also reports whether the hooked input method panel is currently visible, so
the main app can show or hide the floating ball without relying on Accessibility
for keyboard visibility detection.

Streaming preview is treated as composing text: it should remain reversible until
the final result is committed or the session is cancelled. Newer bridge builds
use a session id for preview, final commit, and cancellation, so stale partial
results from an old recording session are rejected by the hooked IME side.

The module returns capability metadata such as module version, active input
connection state, sensitive-field blocking state, IME window visibility,
composing preview support, and session support. BiBi records decisive bridge
calls in its existing API log with text length and result codes, not the
recognized text content.

The APK includes `META-INF/xposed/module.prop` for LSPosed/Xposed module
metadata. Release update checks still depend on publishing this standalone
module repository through the chosen release/feed channel.

It does not read user input, inject UI into the third-party keyboard, or handle
recording / ASR / post-processing.
