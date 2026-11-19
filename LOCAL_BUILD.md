# Building FluxPlayer yourself

Everything below is for a machine with normal internet — a laptop, Android Studio, or a Colab
session. The repository is already fixed; the four defects are on `main` at `4e48e0f`.

## The one thing that can trip you up: compileSdk 37

The project asks for `compileSdk = 37`, and API 37 is **not in the stable SDK channel yet**. The
canary platform reports `ApiLevel=37.1` and is what AGP resolves for 37.0. If your SDK Manager
does not offer "Android 37", install it by hand:

```bash
export ANDROID_HOME=$HOME/Android/Sdk     # adjust to your SDK path
mkdir -p "$ANDROID_HOME/platforms/android-37.0" "$ANDROID_HOME/licenses"

# licence files, so Gradle stops asking
printf '\n24333f8a63b6825ea9c5514f83c2829b004d1fee\nd56f5187479451eabf01fb78af6dfcb131a6481e\n' \
  > "$ANDROID_HOME/licenses/android-sdk-license"
printf '\n8933bad161af4178b1185d1a37fbf41ea5269c55\n' \
  > "$ANDROID_HOME/licenses/android-sdk-preview-license"

cd /tmp
curl -sL -o p37.zip https://dl.google.com/android/repository/platform-CANARY_r15.zip
unzip -q -o p37.zip -d p37
cp -r p37/android-*/* "$ANDROID_HOME/platforms/android-37.0/"
```

Build-tools 36 and 34 are also needed:

```bash
for v in 36:build-tools_r36_linux.zip 34:build-tools_r34-linux.zip; do
  ver=${v%%:*}; zip=${v#*:}
  curl -sL -o /tmp/bt.zip "https://dl.google.com/android/repository/$zip"
  unzip -q -o /tmp/bt.zip -d /tmp/btx
  mkdir -p "$ANDROID_HOME/build-tools/$ver"
  cp -r /tmp/btx/android-*/* "$ANDROID_HOME/build-tools/$ver/"
done
```

## The build

```bash
git clone https://github.com/1234567890933/fluxplayer.git
cd fluxplayer
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x gradlew

./gradlew test            # unit tests; the two PlayerServiceArtworkTest cases now pass
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Give Gradle memory — 4 GB is enough:

```bash
./gradlew :app:assembleDebug -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
```

## Two notes

* `gradle/gradle-daemon-jvm.properties` asks for a JetBrains JDK 21. On a machine with internet
  Gradle downloads it automatically from foojay. If you would rather use your own JDK 21, delete
  that file.
* The first build downloads roughly 550 MB of dependencies. jitpack and the Gradle plugin portal
  are both required; neither is reachable from the sandbox this was written in, which is the only
  reason the build was never finished there.

## If it fails

Send the first `e:` line (Kotlin) or the `* What went wrong:` block. Every failure so far has been
one of four things, and all four are already fixed on `main`.
