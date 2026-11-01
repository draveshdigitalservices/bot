# Photo Webcam Pro (Android)

A fresh Android Studio project matching the requested phone UI and only the requested photo-to-webcam features.

## Features
- Upload one or multiple photos from the phone.
- Each photo has its own Hide / Show control. Hiding never deletes the photo.
- Show All / Hide All.
- Tap a visible thumbnail to make it the large live display.
- Start / Stop a local MJPEG stream server on port 8080.
- Copy the stream URL for OBS / SplitCam.

## Build APK
Open this folder in Android Studio and build `app` > `assembleDebug`, or push it to GitHub and run **Actions > Build Photo Webcam Pro APK**. Download the `Photo-Webcam-Pro-APK` artifact.

## Use with OBS / SplitCam
1. Connect the phone and PC to the same Wi-Fi / phone hotspot.
2. Open the app, upload photos, and leave the desired photo(s) shown.
3. Tap **Start Live** and copy the displayed URL.
4. In OBS, add a **Media Source** and enter the URL, or use a compatible MJPEG/network source in SplitCam.

The stream repeats the currently selected visible photo as MJPEG frames. This is a photo stream, not a real camera feed. The phone must stay awake and the app must remain running while streaming.
