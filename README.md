# Pitch Tuner (offline, from scratch)

## What this is
A fresh, offline Android tuner app, built after analyzing (not copying) the
old PitchLab APK. The old app's real pitch-detection logic lived in a
stripped native library (`libPitchLab.so`), not in Java, and used a
**chromogram (chroma-spectrogram) approach**: FFT the audio, fold energy into
12 pitch-class bins, pick the note from there. This project reimplements
that *category* of algorithm from scratch using standard, published DSP
(Cooley-Tukey FFT, chroma folding) — no code, coefficients, or binary data
from the original app.

## Why it's offline
- No `INTERNET` permission is requested at all.
- Only permission used is `RECORD_AUDIO` (for the mic) and
  `MODIFY_AUDIO_SETTINGS`.
- Everything (FFT, chroma folding, note detection) runs on-device in Kotlin.

## How the algorithm works (`PitchDetector.kt`)
1. Capture 4096 mono samples at 44.1kHz from the mic.
2. Apply a Hann window, run a radix-2 FFT (`FFT.kt`).
3. Fold every FFT bin's energy into one of 12 chroma bins based on its
   nearest semitone — this is the chromogram.
4. The loudest chroma bin = detected pitch class (note letter).
5. Within that pitch class, find the strongest FFT bin and refine it with
   parabolic interpolation for a precise frequency.
6. Convert frequency → note name, octave, and cents sharp/flat, shown live.

## How to build it — no Android Studio needed
This project includes a GitHub Actions workflow (`.github/workflows/build.yml`)
that builds the APK in the cloud for free. Steps:

1. Create a free account at github.com if you don't have one.
2. Create a new repository (public or private, either works) and upload
   this whole `PitchTuner` folder to it — easiest way is via the GitHub
   website: "Add file" → "Upload files", drag everything in, commit.
3. Go to the **Actions** tab of your new repo. A workflow called
   "Build APK" should run automatically (takes 2-3 minutes).
4. When it finishes (green checkmark), click into that workflow run, and
   under **Artifacts** download `PitchTuner-debug-apk` — it's a zip
   containing `app-debug.apk`.
5. Transfer `app-debug.apk` to your tablet (email it to yourself, upload
   to Google Drive, USB cable, whatever's easiest) and open it there.
6. Your tablet will likely block the install by default — you'll be
   prompted to allow "install unknown apps" for whichever app you used to
   open the file (Files, Chrome, Drive, etc.) in Settings. Approve that,
   then tap the APK again to install.
7. Open the app, grant the mic permission — it should immediately start
   showing note names as you play a sound.

## Alternative: build it yourself with Android Studio
1. Install **Android Studio** (free, from developer.android.com).
2. Open this folder (`PitchTuner/`) as a project, let Gradle sync.
3. Plug in your tablet (USB debugging on) or use an emulator, hit **Run**.

## Extending it
Not included yet (say the word and I'll add any of these):
- Chord Matrix (detect multiple simultaneous notes)
- Stage/strobe tuner visual display
- Tone generator (reference pitch playback)
- Adjustable A4 reference frequency (currently fixed at 440Hz)
