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
for keyboard visibility detection. Visibility hints are sent to both the open
source app package (`com.brycewg.asrkb`) and the Pro app package
(`com.brycewg.asrkb.pro`).

Streaming preview is treated as composing text: it should remain reversible until
the final result is committed or the session is cancelled. Newer bridge builds
use a session id for preview, final commit, and cancellation, so stale partial
results from an old recording session are rejected by the hooked IME side.

The module returns capability metadata such as module version, active input
connection state, sensitive-field blocking state, IME window visibility,
composing preview support, session support, PCM recording trigger support, and
input-context support. When the Pro app's input-context post-processing option
is enabled, BiBi can request text around the cursor so AI post-processing can
use it as reference material. The module refuses this request for sensitive
input fields. BiBi records decisive bridge calls in its existing API log with
text length/context length and result codes, not the recognized text content or
input-context content.

Recent builds can also inject a tiny transparent long-press strip into the
bottom edge of a standard `InputMethodService` window. The strip is attached
only through `onStartInput` / `onWindowShown` and the IME window decor root; it
does not know about any keyboard's spacebar, candidate row, or private key
model. Holding the strip starts `AudioRecord` in the hooked IME process and
pushes PCM16 mono frames to the BiBi app's bridge PCM session service. Releasing
finishes the session; gesture cancel, `onFinishInput`, `onWindowHidden`, and
`onDestroy` cancel the active session and stop local recording.

Because Android touch dispatch requires the injected view to own the initial
`ACTION_DOWN` in order to receive release/cancel events, the first version keeps
this area intentionally small (`18dp` at the bottom edge). Taps inside that tiny
bottom strip may be consumed by the bridge even when they do not become a long
press. If the module cannot attach the strip to a safe standard window root, or
if the hooked IME process lacks microphone permission / `AudioRecord` cannot
start, it reports the trigger as unsupported or failed and does not fall back to
main-app recording.

The APK includes `META-INF/xposed/module.prop` for LSPosed/Xposed module
metadata. Release update checks still depend on publishing this standalone
module repository through the chosen release/feed channel.

The module still does not run ASR or post-processing itself. Recognition,
partial preview, final commit, and cleanup remain owned by the BiBi app through
the bridge protocols.
