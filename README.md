# Game Recorder

Android screen recorder configured for Android 8+ and intended for the Infinix X693 / Android 11.

## Features
- Screen recording to MP4 using Android MediaProjection and H.264 encoding.
- Balanced, high-bitrate, and battery-saver quality presets.
- Audio choices: microphone, silent, internal game/device playback, or game playback mixed with microphone.
- Internal/mixed audio uses Android AudioPlaybackCapture (Android 10+), AAC encoding, and MP4 track muxing.
- Optional floating REC launcher. It is hidden before capture starts and reappears after recording stops, so the control itself is not included in the saved video.
- Persistent notification with a Stop action.
- Recordings are saved to Movies/Game Recorder on Android 10+.

## Important limitations
- Internal playback capture depends on Android's AudioPlaybackCapture policy. Some games/apps disable capture or may produce silent internal audio.
- Audio mixing and screen capture have not yet been tested on the user's physical Infinix X693. Please test a short clip first and check that video and audio are both present and synchronized.
- Recording consumes battery and may affect frame rate. Lower resolution/bitrate and 30 FPS are intended to reduce load, but the impact depends on the game and device.
- The floating REC button is intentionally hidden during capture. Use the persistent notification to stop recording.

## Build
GitHub Actions builds the debug APK on pushes to `main` and `game-recorder`. Download the `GameRecorder-debug` artifact from the latest successful workflow run.
