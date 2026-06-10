# Is It Compatible?

**Pick the right emulator + driver + preset for any game on YOUR Android handheld — based on reports from people with similar hardware.**

The decision layer the Android emulation scene is missing. EmuTran installs the
emulators. CalibrateSoc tunes the hardware. **Is It Compatible?** answers the
remaining question: *for this specific game on this specific device, which
emulator/translator do I use and with what settings?*

## What it does (target v0.1)

- Detects your device (SoC, GPU, RAM, Android version, GPU driver).
- Scans your ROM folder and (optionally) a Windows-games folder.
- For every recognised game, recommends the best emulator + driver + preset
  for your hardware, scored by reports from similar-device users.
- Staging-folder "apply preset" — downloads the recommended GPU driver,
  writes the emulator's import-able config file, generates a step-by-step
  `INSTRUCTIONS.md` with copy-able paths.
- Console reports come from **EmuReady** (daily snapshot + live API).
  Windows-translator reports (Winlator / GameNative / GameHub / Mobox /
  ExaGear) come from this project's own community-submitted DB.

## What it does NOT do

- **Doesn't install emulators.** Use EmuTran for that.
- **Doesn't tune the SoC.** Use CalibrateSoc for that.
- **Doesn't talk to other apps via Intents** — fully standalone. You apply
  recommended settings yourself by importing the config files we wrote into
  your staging folder.
- **Doesn't run a server.** All compatibility data lives in a GitHub repo
  the app fetches periodically.
- **Doesn't collect any data.** No accounts, no analytics, no phone-home.

## Status

**Pre-alpha — Phase 1 in progress.** Project scaffold, hardware
fingerprinting, and the 3-step first-launch wizard are complete. Real
compatibility data, recommendations, and the apply flow ship in later
phases — see `app-named-is-modular-sunset.md` for the full plan.

## Build

```bash
cd iSiTCompatible
./gradlew assembleDebug
```

Requires JDK 17 and Android SDK 35. Debug APK lands at
`app/build/outputs/apk/debug/app-debug.apk` and uses a `.debug` applicationId
suffix so it can coexist with release builds on the same device.

## Credits

- **[EmuReady](https://emuready.com)** ([Producdevity/EmuReady](https://github.com/Producdevity/EmuReady))
  — primary source of console-emulation compatibility data. License,
  attribution, and back-links are surfaced on every recommendation card
  that uses their data.
- **[K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers)**
  — source of the Adreno/Turnip GPU drivers the apply flow downloads.
- **[Obtainium Emulation Pack](https://github.com/RJNY/Obtainium-Emulation-Pack)**
  — cross-reference for emulator metadata.
- Built with assistance from Claude (Anthropic).

## License

MIT.
