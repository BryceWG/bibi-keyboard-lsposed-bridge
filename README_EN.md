<div align="center">

<img src="images/logo.svg" width="128" height="128" alt="BiBi IME Bridge">

# BiBi IME Bridge

**Standalone LSPosed / LSPatch bridge module for BiBi Keyboard**

Lets the floating ball preview and insert recognition text through the current third-party keyboard, and optionally adds hold-to-record at the bottom of a compatible IME.

[简体中文](README.md) | English

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg)](https://developer.android.com/)
[![Latest Release](https://img.shields.io/github/v/release/BryceWG/bibi-keyboard-lsposed-bridge)](https://github.com/BryceWG/bibi-keyboard-lsposed-bridge/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/BryceWG/bibi-keyboard-lsposed-bridge/total)](https://github.com/BryceWG/bibi-keyboard-lsposed-bridge/releases)

[Download & Install](#-download--install) • [Docs](https://bibidocs.brycewg.com/en/advanced/ime-bridge.html) • [BiBi Keyboard](https://github.com/BryceWG/asr-keyboard)

</div>

> **Advanced feature**: The module runs inside selected third-party keyboard processes and requires LSPosed or LSPatch. Install it only from this repository’s Releases and limit its scope to keyboards that actually need the bridge.

## ✨ What it provides

| Capability | Description |
| ---------- | ----------- |
| Floating-ball text insertion | The current third-party IME commits final recognition text directly |
| Streaming preview | Displays partial results as composing text when supported and cleans them up on finish/cancel |
| Keyboard visibility | Reports IME panel visibility without relying only on Accessibility detection |
| In-IME recording | Hold the bridge area at the bottom of a third-party keyboard and release to send audio to BiBi Keyboard |
| Privileged clipboard R/W | Reads/writes the system clipboard inside the target IME process so SyncClipboard can work outside BiBi’s own keyboard |
| Input-field context | Pro: provides cursor-adjacent text to AI post-processing when the related option is enabled |

The module does **not** run ASR or AI post-processing. Recording orchestration, provider selection, backup engines, history, SyncClipboard networking, and final processing remain owned by BiBi Keyboard.

Clipboard sync only works while the target IME process is alive. If the system kills the keyboard, observe/write pause until that IME is opened again.

## 📋 Requirements

- Android 8.0 (API 26) or later
- A recent [BiBi Keyboard](https://github.com/BryceWG/asr-keyboard) OSS or Pro build
- A working ASR provider configured in BiBi Keyboard
- One of these environments:
  - a rooted device with LSPosed installed and enabled
  - a device where you can patch the target keyboard APK with LSPatch
- A target keyboard based on the standard Android `InputMethodService`; heavily customized or integrity-protected IMEs may be incompatible

The “target IME” is the third-party keyboard you normally use and want BiBi Keyboard to insert text through. Select that keyboard in the LSPosed scope—**not** BiBi Keyboard itself.

## 📦 Download & install

### Download

1. Open this repository’s [Releases](https://github.com/BryceWG/bibi-keyboard-lsposed-bridge/releases/latest)
2. Download the latest APK
3. Install it; it appears as **BiBi IME Bridge**

Keep BiBi Keyboard and this module reasonably up to date. Older bridge versions may still insert text but lack newer recording, clipboard, or input-context capabilities.

### Install with LSPosed

1. Install this module APK
2. Open LSPosed Manager and enable **BiBi IME Bridge**
3. Open its scope and select **only** the third-party keyboard(s) you want to bridge
4. Do not add either the OSS or Pro BiBi Keyboard app to the scope
5. Force-stop and reopen the target keyboard; reboot the device if it still does not activate
6. Switch to the target keyboard, then refresh bridge status in BiBi Keyboard

Do not select the Android system framework or unrelated apps. A wider scope does not add functionality.

### Install with LSPatch

1. In LSPatch, select the third-party keyboard APK you want to bridge
2. Patch it with this module as an embedded module
3. Install the patched keyboard and select it as the current IME
4. When the keyboard is updated, patch the new APK again

> LSPatch changes the APK signature. LSPosed modules may cause system instability. Back up the keyboard’s configuration first and make sure you have a rescue method.

## 🚀 Enable it in BiBi Keyboard

1. Open `Settings → UI & Interaction → Floating Settings`
2. Enable “IME bridge text insertion”; optionally enable “Record inside bridged IME”
3. Focus a normal text field and keep the target keyboard open
4. Tap “IME bridge status” to refresh detection
5. Confirm that it shows the correct target keyboard and an active input connection

Floating-ball results now prefer insertion through the target IME. If the bridge is temporarily unavailable, BiBi Keyboard can still try Accessibility insertion or the clipboard fallback.

Password, payment, and other sensitive fields are intentionally blocked.

For full steps, recording-area tuning, and troubleshooting, see: [IME Bridge docs](https://bibidocs.brycewg.com/en/advanced/ime-bridge.html)

## 🔒 Permissions and privacy

- Use minimal scope and install only from this repository’s official Releases
- Normal text insertion does not read existing text from the input field
- Pro input context reads limited cursor-adjacent text only when explicitly enabled and fails closed for sensitive fields
- In-IME recording captures audio in the target IME process and sends it to BiBi Keyboard
- SyncClipboard credentials and HTTP stay in BiBi Keyboard; the module only performs local clipboard read/write
- The module does not independently store/transcribe audio or run AI post-processing

## 🛠 Development notes

This repository is an independent Gradle project and git repo. It is not included by the main BiBi Keyboard `settings.gradle.kts`. The main app remains independent from Xposed APIs; this module only runs inside a hooked third-party input method.

- Package: `com.brycewg.asrkb.imebridge`
- Min SDK: Android 8.0 (API 26)
- Publish built APKs through this repository’s Releases; do not commit APKs into either source tree

## 🔗 Related links

- [BiBi Keyboard (main repo)](https://github.com/BryceWG/asr-keyboard)
- [Docs (中文)](https://bibidocs.brycewg.com/advanced/ime-bridge.html)
- [Docs (English)](https://bibidocs.brycewg.com/en/advanced/ime-bridge.html)
- [Website](https://bibi.brycewg.com)
- [Telegram group](https://t.me/+UGFobXqi2bYzMDFl)
