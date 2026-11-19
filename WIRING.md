# FluxPlayer 1.2.0 — wiring audit and protection layer

This build is about the pipes, not new screens. Every feature was traced from the control the
user touches to the last line of code that does the work, and every break found on the way was
joined back up. On top of that sits a protection layer so a device that cannot run a step loses
that step only, never playback.

## What was broken, and why

**1. HDR content was never detected.**
`HdrContentDetector` reads the real colour metadata of the video (ST2084 is HDR10, HLG is
broadcast HDR) and `selectedVideoFormat()` picks the track. Nothing in the app called either
function, so the controller's `hdrContent` stayed `false` for every video. The tone map could not
engage on real HDR material.
Fixed in `PlayerService.onTracksChanged`: the detector now feeds `setHdrContent()` on every track
change.

**2. The HDR switch did nothing visible on an SDR panel.**
`ColorEffects.build()` added an ACES tone map whenever `hdrEnabled && !deviceHasHdrDisplay`. On an
SDR source there is no dynamic range to compress, so the picture only lost contrast — the switch
looked broken.
Now the branch is three-way:
* HDR panel + HDR video: nothing added, ExoPlayer passes HDR through.
* SDR panel + HDR video: the ACES curve compresses the highlights.
* SDR panel + SDR video: a contrast and colour lift, which is what an "HDR look" means on a
  display that cannot show more range.

**3. The buttons could not be reached.**
The top bar already held back, decoder, playlist, audio and subtitle in one row with no scrolling.
Adding HDR and Colour pushed them past the right edge, so they existed in the APK and were
invisible on the phone.
Both moved to the bottom bar next to playback speed and rotate, and the top bar is back to its
original five controls.

**4. The video info chip had an engine and no UI.**
`VideoInfo.of(format)` returns resolution, frame rate, codec and the HDR label. Nothing displayed
it. There is now an Info button that fills a chip from the real track metadata and hides itself
after four seconds, as section 13.1 of the brief asks.

**5. The HDR state lived in the wrong place.**
The toggle kept its own `remember` inside the bottom bar, so nothing else could know whether HDR
was on. The state is now owned by `PlayerControls`, which drives the engine and shows the
confirmation text, so the button, the engine and the UI cannot disagree.

## The protection layer

The effect pipeline is the only part of the app that talks to the GPU, and on a cheap or busy
device that call can fail. `PlayerEffectsController.apply()` now runs the list through three
stages instead of one:

1. the full grade (blur, unsharp, tone, colour);
2. if that is refused, the same grade without blur or convolution — the cheapest effects the
   engine has, which work on almost any GL ES 2.0 device;
3. if even that fails, no grade at all and a `isDegraded` flag.

Each stage is wrapped in `runCatching`, so a refused effect can never take playback down with it.
The display capability query is guarded the same way: an unusual display stack must not crash the
player service while it is being constructed.

## Verified here

* `ktlint` **1.8.0**, the exact version in `gradle/libs.versions.toml`: **0 violations** in
  `core:effect` and `feature:player`.
* The Kotlin compiler parses every changed file without a syntax error.
* The patch applies cleanly to a fresh clone of the repository and all twelve feature checks pass.

## Not verified here

There is no Android device or emulator in this environment, so the runtime behaviour — the actual
look of the grade, the taps, the chip timing — has to be confirmed on a phone. The unit tests run
in CI on every push.

## Where the remaining work is

* `VideoContentScale.HUNDRED_PERCENT` still carries a `// TODO: fix this` from the base project.
  It needs a device to judge what "100 %" should mean, so it was left alone rather than guessed at.
* Per-file colour presets (section 13.2) need the Room table, which is not part of this change.
* The lock-button alignment setting (13.1) still follows the existing `controlButtonsPosition`
  preference rather than a separate key.

## The breaker box (1.2.1)

`core/effect/EffectGovernor.kt` is the MCB of this pipeline. It folds every reason a device might
not cope — thermal level, battery saver, a low-RAM phone, and a driver that refused the last
attempt — into one decision: `EffectBudget.FULL`, `LIGHT` or `OFF`, plus an `hdrStrength` value
between 0 and 1.

The controller no longer guesses. It asks the governor what to attempt, passes the strength into
`ColorEffects.build()` so the grade is proportional to what the device can afford, and reports
every success and failure back so the box can trip. Two refusals in a row take the pipeline out of
the way completely instead of burning battery on retries.

`apply()` is `@Synchronized`, which is the protection lock on the connection: a slider drag and a
thermal change arriving together can no longer interleave two effect lists into the player, which
is what shows up as flicker.

`EffectGovernorTest` covers ten states with real JUnit — healthy, battery saver, moderate heat,
severe heat, cooling down, low RAM, one refusal, two refusals, recovery after a success, and
battery saver winning over everything else. All ten pass.

## Round two: no switch may lie (1.2.2)

`PlayerEffectsBridge.setHdrSwitch()` and `setColorSettings()` now return `Boolean`: true only when
a live controller accepted the call. The HDR button uses that answer, so it can no longer light up
while nothing happens — if the engine is not reachable the chip says "HDR unavailable - no player
attached" and the switch stays off.

Six more JUnit tests cover fluctuation: ten battery-saver toggles leave no residue, harmless heat
levels never move the budget, reporting the same state fifty times is idempotent, strength falls
monotonically as the device heats, recovery after tripping restores the full grade, and five
fail/recover cycles stay consistent. The suite is now sixteen tests, all passing.
