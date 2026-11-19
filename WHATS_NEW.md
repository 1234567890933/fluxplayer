# FluxPlayer 1.1 — what is new, and where the buttons are

Built on top of the FluxPlayer base. Everything below is wired to the engine that already existed
in `core/effect`; nothing in the engine was rewritten.

## New: colour grading sheet

`feature/player/.../ui/ColorGradingView.kt`

* Five preset chips: **Cinema, Vivid, Natural, Night, Custom**.
* Six sliders: brightness, contrast, saturation, sharpness, clarity, noise reduction.
* White balance: **Warm / Neutral / Cool**.
* Every change is applied live, so the picture moves while the finger is still on the slider.

## New: HDR toggle

A button in the player top bar. Grey when off, accent colour and "HDR on" when on. It calls
`setHdrSwitch()` on the effects controller, which is what decides whether the ACES tone map is
baked in for an SDR panel.

## New: the bridge between the service and the UI

`core/effect/.../PlayerEffectsBridge.kt`

The controller needs an ExoPlayer, so it is created inside `PlayerService` and cannot be injected
into a composable. The service registers itself with this object while it is alive; every call is a
no-op when nothing is attached.

## Where the buttons are

Top bar, right of the subtitle button: **HDR**, then **Colour**. The bar spacing was reduced from
16 dp to 10 dp so seven controls still fit on a narrow phone in portrait.

## What is still missing from section 13.1 of the brief

* Info chip (video format overlay, auto-hide after 4 s).
* Lock button alignment setting (Start / Center / End from DataStore).
* FIT / FILL / ZOOM cycle in `PlayerContentFrame.kt`.
* Persisting the colour grade per file (the Room table from section 13.2).

## How this was checked

* Every symbol the new code uses is already used elsewhere in the project: `FilterChip`,
  `Slider`, `BasicText`, `PlayerButton`, `BoxScope.OverlayView`.
* Every API it calls exists with the signature used: `setColorSettings(ColorSettings)`,
  `setHdrSwitch(Boolean)`, and `feature/player` already depends on `core:effect`.
* The one existing `ControlsTopView` call site and the preview both use named arguments, so the
  new parameter with a default cannot break them.
* Braces and parentheses balance in all six touched files; no line exceeds 120 characters; the
  project's own ktlint rule (one argument per line) is followed.
* It has **not** been compiled — that happens in CI on the first build.

## To build

Push this to a repository, then either run the GitHub workflow or build locally:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x gradlew
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Permissions

`POST_NOTIFICATIONS` was missing from the app manifest: on Android 13+ the playback notification
and the media session need it, so background playback could run without a visible notification.
It is added. The foreground-service permissions were already correct
(`FOREGROUND_SERVICE` plus the typed `FOREGROUND_SERVICE_MEDIA_PLAYBACK`), and `READ_MEDIA_VIDEO`,
`READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` were already declared.

## Auto orientation and fit

These already exist in the base and were verified, not reimplemented:

* `state/RotationState.kt` already chooses the orientation from the video's own aspect ratio
  (portrait video -> sensor portrait, otherwise landscape).
* `extensions/ScreenOrientation.kt` maps the user's choice to the Activity orientation, and the
  rotate button is present in both the compact and the full control bar.
* `VideoContentScaleSelectorView` exposes BEST_FIT / STRETCH / CROP / HUNDRED_PERCENT.

I deliberately did **not** rewrite the scale selection to "auto fill" without a compiler to check
it, because a wrong change there distorts every video. The building blocks are all present; that
one behaviour change is the next step, with a device to test on.
