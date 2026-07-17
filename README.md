# UShare — Ultrasonic Data Transceiver

**UShare** is a cross-platform mobile app that transfers phone numbers and short text between devices using **inaudible ultrasonic sound waves** — no Bluetooth, NFC, Wi-Fi, or internet required. Just bring two phones close together (< 5 cm) and tap send.

---

## How It Works

###  Ultrasonic Protocol

UShare encodes data as a sequence of simultaneous **dual-tone pairs** in the 15–19 kHz range (barely audible to most humans, if at all). Each character is mapped to a unique frequency pair from an 8×8 grid:

| Band | Frequency Range | Purpose |
|------|---------------|---------|
| Low row | 15,000 – 16,400 Hz | Row encoding (8 frequencies) |
| High col | 17,500 – 18,900 Hz | Column encoding (8 frequencies) |

A **phase-shift offset** (+300 Hz) is applied every other character to prevent repetition blurring, and a **Hanning window** smooths each tone to eliminate clicks.

###  Two-Way Verification

Every transmission follows a **3-step handshake** to ensure the data arrived correctly:

1. **Device A** encodes the number as ultrasonic tones and plays them through the speaker
2. **Device B** hears the tones, decodes them, **shows the number** on screen, and **echoes it back** to Device A
3. **Device A** hears the echo, confirms it matches the original, and sends an **ACK confirmation** — at which point both devices show `SUCCESS`

If the echo doesn't arrive or doesn't match, Device A automatically retransmits.

###  Key Features

| Feature | Description |
|---------|-------------|
| **Profile carousel** | Scroll through saved numbers — each with a type icon (Send Money, Till, Paybill, Pochi) |
| **Manual entry** | Type any number on the fly and send it |
| **Long-press send** | Hold the send button for the full verification handshake |
| **Quick tap send** | Tap to send instantly (single-shot transmission) |
| **Received log** | Incoming numbers appear in a chat-style log with copy-to-clipboard |
| **Settings** | Toggle sending sound, adjust ultrasonic volume, edit your profile name and photo |
| **No permissions needed** (beyond microphone) | Works entirely offline |

###  UI

Dark neumorphic interface with:
- Soft-UI extruded shadows (`neumorphicRaised` / `neumorphicInset`)
- Cyan accent (#0DFCAC) for active states and glow effects  
- Monospace typography (Share Tech Mono) for all numeric displays
- Subtle vertical-gradient fade on the received log window

---

## Tech Stack

- **Language:** Kotlin (Multiplatform)
- **UI Framework:** Jetpack Compose (with Compose Multiplatform)
- **Targets:** Android (primary), Desktop (JVM)
- **Audio:** `AudioTrack` / `AudioRecord` (Android), `sounddevice` (Python prototype)
- **Signal Detection:** Goertzel algorithm (efficient single-bin DFT)
- **Build System:** Gradle with Kotlin DSL

---

## Project Structure

```
UShare/
├── androidApp/          # Android application module
│   └── src/main/        # Manifest, resources, MainActivity
├── src/
│   ├── commonMain/      # Shared Compose UI + protocol logic
│   │   └── kotlin/.../
│   │       ├── App.kt              # Main composable / state management
│   │       ├── Components.kt       # UI components (cards, nav, modals)
│   │       ├── Theme.kt            # Colors, typography, neumorphic modifiers
│   │       ├── Models.kt           # Data classes & enums
│   │       ├── UltrasonicProtocol.kt # Signal generator, Goertzel analyzer
│   │       ├── NotificationBeep.kt # 800 Hz confirmation beep
│   │       └── Platform.kt         # Expect declarations for platform APIs
│   ├── androidMain/     # Android-specific implementations
│   │   └── kotlin/.../
│   │       ├── UltrasonicTransceiver.android.kt # AudioRecord/AudioTrack
│   │       └── Platform.android.kt              # Clipboard, image picker
│   └── desktopMain/     # Desktop JVM implementations
├── build.gradle.kts     # Shared module build config
└── settings.gradle.kts  # Project settings
```

Also in this repo:
- `Fronted.html` — Web-based UI prototype/mockup
- `backend.py` — Python ultrasonic core (original CLI prototype)
- `UI.md` — Full UI specification document

---

## Building & Installing

```bash
# Clone
git clone https://github.com/Joshkihiu/Ushare.git

# Build debug APK (signed with included release keystore)
cd Ushare/UShare
./gradlew androidApp:assembleDebug

# Install via ADB
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

[📥 **Download the latest APK**](https://github.com/Joshkihiu/Ushare/raw/main/UShare_signed.apk) — install directly on any Android device.

---

## Volume & Distance Tuning

The app includes a **US Volume** slider in Settings. For best results:
1. Set volume to ~30%
2. Hold two phones **2–5 cm apart**, speakers facing each other
3. Tap send on one device — the other should receive and display the number
4. Adjust volume up/down to find your sweet spot

---

## Credits

Built with ❤️ by Joshkihiu
