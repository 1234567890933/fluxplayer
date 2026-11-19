# Changes made to this source, 18 November 2026

Nine commits on top of the original project, all at commit `4e48e0f`. Each one fixes a real
defect that stopped the build or crashed the app.

## Build pipeline

1. **`gradlew` had no executable bit.** Git stored it as mode `100644`, so on a fresh runner every
   step that ran `./gradlew` died with `/usr/bin/sh: 1: ./gradlew: Permission denied` (exit 126).
   Both jobs in `.github/workflows/android_build.yaml` now run `chmod +x gradlew` after checkout.

## Crashes in the player service

2. **`Context.getDisplay()` throws on a Service context.** `PlayerService.onCreate` builds
   `PlayerEffectsController`, which asked `ColorEffects.deviceHasHdrDisplay(context)` for the
   display. On API 30+ that call does not return null for a non-visual context, it throws
   `UnsupportedOperationException: Tried to obtain display from a Context not associated with one`.
   The display is now resolved through `DisplayManager`, with the legacy `WindowManager` path as a
   fallback, and HDR is treated as unsupported when no display can be found.

3. **The thermal listener could crash the same path.** With the display fixed, the next failure
   appeared: `RuntimeException: Listener failed to set` from `PowerManager.addThermalStatusListener`.
   The platform caps thermal listeners per process, which is easy to hit when the service is
   recreated. `ThermalManager.start()` now refuses to register twice and falls back to polling if
   registration fails.

4. **A smart cast that does not compile.** My first version of that fix used the nullable
   `PowerManager` property inside a lambda, where the null check does not survive. It is now held in
   a local first.

## Code style

5. **92 ktlint violations** in four files under `core/effect`, plus `VideoInfoProvider.kt`
   declaring a single class named `VideoInfo` — the house rule requires the file name to match, and
   that one cannot be auto-corrected. The file is renamed to `VideoInfo.kt` and the tree was
   formatted with the project's own `ktlintFormat`.

## Result

`./gradlew assembleDebug` compiles, `./gradlew test` passes (the two `PlayerServiceArtworkTest`
failures were defects 2 and 3), and `./gradlew ktlintCheck` is clean.

## Still open from the master brief

The engine is complete; the missing part is the UI listed in section 13.1 of the brief — the HDR
button, the colour sheet, the info chip, the lock button position and the fit modes.

See `LOCAL_BUILD.md` for the exact manual build steps, including the API 37 platform that is not in
the stable SDK channel yet.
