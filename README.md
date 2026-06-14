# Is It Compatible?

**Pick the right emulator + driver + settings for any game on your Android gaming handheld — based on reports from people with similar hardware.**

The decision layer the Android emulation scene is missing. **Is It Compatible?** answers the question every handheld gamer asks: *for this specific game on my specific device, which emulator do I use, and with what settings?*

It reads your chip, GPU, and RAM at runtime and matches you to compatibility reports from similar hardware — then tells you what to install, what driver to use, and what config gets the best result on **your** device.

## Works on any Android gaming handheld

The app reads your hardware at runtime and recommends per-device — it is **not** built for one device. It works on:

- **AYN** — Odin / Odin 2 / Odin 3 / Thor
- **Retroid Pocket** — 2 / 3 / 4 / 5 / Mini / Flip
- **AYANEO Pocket** — S / DS / Micro / Air
- **GPD** — XP2 / G1, **Anbernic** — RG556 / RG405 / others
- **Logitech G Cloud, Razer Edge**, and generic Snapdragon / Dimensity / Helio / Unisoc handhelds
- Even a **phone with a controller** — detected the same way

The SoC catalog covers Snapdragon (8-series, 7-series, G-series), Dimensity (9xxx–900), Helio (G99/G95/G90T), Unisoc, Rockchip, Allwinner, and Exynos. **If your exact chip isn't catalogued, the app still works** — it falls back to GPU-vendor-level recommendations with honest "estimated" labelling, so you're never left with a blank screen.

> **Tested on:** AYN Odin 3 (Snapdragon 8 Elite) and AYN Thor (Snapdragon 8 Gen 2). Coverage and accuracy improve as more devices report results — your device doesn't need to be in the tested list to use the app.

## What it does

- **Detects your device** — SoC, GPU, RAM, Android version, GPU driver.
- **Browse 1089 games** with per-device compatibility, FPS estimates, and the best emulator for each, sorted by what runs best on *your* chip.
- **My Library** — scan your ROMs + PC games and see compatibility for the games you actually own.
- **My Device dashboard** — your chip, a hardware tier, and honest coverage (how many games have real data on your hardware vs. estimated).
- **Windows games via GameNative (IIC)** — one-tap launch with auto-applied per-game configs; the companion fork's auto-tuner results flow back as verified recommendations for your device.
- **Journal** — log your results, track what you've got working, see your stats.
- **Favorites** — star a game and get notified when its compatibility improves on your device.
- **In-app updates** — verified for safety (SHA-256 + signing-certificate check before any install).

## Honest data

Every recommendation is labelled by how closely the reporting hardware matches yours — from **"Same SoC + RAM"** (strong) down to **"Any device"** (rough estimate). AI-estimated entries are clearly marked as estimated — no fake "Perfect" on data we haven't verified. Every number is either traceable to a real report or shown as a best-guess.

Console compatibility data comes from **EmuReady**. Windows-game data comes from this project's own community reports.

## What it does NOT do

- **Doesn't install emulators or tune the SoC** — it's the decision layer, not the installer.
- **Doesn't run a server or collect any data** — no accounts, no analytics, no phone-home. Compatibility data lives in a GitHub repo the app fetches periodically.

## Build

```bash
cd iSiTCompatible
./gradlew assembleDebug      # or assembleRelease for a signed build
```

Requires JDK 17 and Android SDK 35. Debug APK lands at `app/build/outputs/apk/debug/app-debug.apk` and uses a `.debug` applicationId suffix so it can coexist with release builds on the same device.

## Credits

- **[EmuReady](https://emuready.com)** ([Producdevity/EmuReady](https://github.com/Producdevity/EmuReady)) — primary source of console-emulation compatibility data. License, attribution, and back-links are surfaced on every recommendation card that uses their data.
- **[K11MCH1/AdrenoToolsDrivers](https://github.com/K11MCH1/AdrenoToolsDrivers)** — source of the Adreno/Turnip GPU drivers the apply flow downloads.
- **[Obtainium Emulation Pack](https://github.com/RJNY/Obtainium-Emulation-Pack)** — cross-reference for emulator metadata.
- Built with assistance from Claude (Anthropic).

## License

MIT.
