# WebCam Pro 2.1

Android photo-to-webcam app with refreshed WebCam Pro branding and a cleaner Media Layers panel.

## Media Layers
- Add multiple image layers from Android storage.
- Each layer has Lock/Unlock, Eye Show/Hide, and Delete controls.
- Tap a layer name to rename it.
- Lock prevents visibility changes or deletion until unlocked.
- Tap a visible layer to select it as the live output.
- No bulk Show All, Hide All, Delete All, or three-dot menu.

## Live stream
- Start / Stop a local HTTP MJPEG stream on port 8080.
- Copy the URL for OBS / SplitCam.
- Photos are resized and JPEG-encoded when added, then reused by the stream.
- Approximate 15 FPS output target; actual smoothness depends on Wi-Fi, phone performance, and receiving app.

## Build APK
Open the project in Android Studio and run `assembleDebug`, or push it to GitHub and run **Actions > Build Photo Webcam Pro APK**. Download the `Photo-Webcam-Pro-APK` artifact.

## Connect
Connect phone and PC to the same Wi-Fi/hotspot, add photos, keep the desired layer visible, tap Start Live, and use the displayed `http://PHONE-IP:8080/stream` URL in OBS/SplitCam. Keep the app open and phone awake while streaming. This is an MJPEG network stream, not a USB UVC webcam device.


## Stream resolution selector
Choose 640x360, 848x480, 960x540, or 1280x720 from the RES dropdown above the preview. The selected resolution is used for the live MJPEG stream and updates immediately.
