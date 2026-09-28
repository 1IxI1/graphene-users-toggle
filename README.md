# User Tap

Triple-tap either top screen edge to switch Android users on GrapheneOS. Uses the system user switcher through Accessibility; no root, Shizuku, or persistent ADB connection.

<img src="media/demo.gif" alt="User switching demo at 2× speed" width="360">

[MP4 version (10 seconds, 2× speed)](media/demo.mp4)

## Setup

Install the APK in each user. Open User Tap, enter an exact user name for each edge, save, and enable its accessibility service. Settings are local to each user. A blank target disables that edge. Use distinct user names.

If you use Happ, enable **Disconnect Happ before switching** in each user. User Tap opens Happ's `disconnect_without_ui` link before showing the system user switcher. It does not start Happ in the destination user; if the switch fails, Happ remains disconnected in the original user.

Colored touch zones can be hidden. Single and double taps inside a zone are consumed; a downward swipe opens notifications. Switching does not end the previous user's session or bypass its lock screen.

Accessibility is a broad permission. This app only inspects SystemUI after a switching request and has no network permission. It depends on GrapheneOS's user-switcher view IDs, which may change between releases.

<img src="screenshots/touch-zones.png" alt="Touch zones" width="280"> <img src="screenshots/accessibility.png" alt="Accessibility settings" width="280">

## Build

Requires Python 3.9+, JDK 17+, Android SDK Platform 35 and Build Tools 35.0.0.

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 build.py
```

Output: `build/user-tap.apk`. The build runs gesture tests and verifies the APK signature. Keep the generated `signing/local.keystore` to sign future updates; build outputs and signing keys are ignored by Git.

The **Build APK** GitHub Action publishes a signed APK to [Releases](https://github.com/1IxI1/graphene-users-toggle/releases) after each successful build on `main`. Release titles use the UTC date; tags include the run number to allow multiple releases per day. It requires the repository secret `USER_TAP_KEYSTORE_BASE64` containing a base64-encoded copy of `signing/local.keystore` (for example, `base64 < signing/local.keystore | gh secret set USER_TAP_KEYSTORE_BASE64`). Manual runs on other branches produce a test-signed artifact that cannot update an existing installation.
