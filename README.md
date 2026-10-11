# Game Recorder

Android screen recorder intended for Android 8+ and the Infinix X693 / Android 11.

## Features in this build
- Screen capture using Android MediaProjection.
- MP4/H.264 recording with Android-selected hardware encoding where supported.
- Balanced, high-bitrate, and battery-saver quality presets.
- Microphone recording or mute.
- Persistent notification with a Stop action.
- Saves recordings to Movies/Game Recorder on Android 10+.

## Limitations
- Internal game audio and game-audio-plus-microphone are not implemented in this build; selecting them is blocked.
- Some games can prevent capture. Recording consumes battery and may affect frame rate; use battery-saver quality to reduce load.
- The app does not keep a floating overlay over the game during recording. Stop from the notification so controls do not appear in the saved video.

GitHub Actions builds a debug APK on pushes to game-recorder or main. Download the GameRecorder-debug artifact from the latest successful workflow run.