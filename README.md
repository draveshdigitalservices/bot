# WebCam Pro 2.0 - Media Layers UI

Android photo-to-webcam app with the WebCam Pro logo and a SplitCam-style Media Layers interface.

## Media Layers
- Add multiple image layers from Android storage.
- Each layer has independent Lock, Visibility (eye), More (three-dot menu), and Delete controls.
- Lock prevents changing visibility or deleting that layer until unlocked.
- Three-dot menu includes Set as Live Output, Lock/Unlock, Rename, Show/Hide, and Delete.
- Show All, Hide All, and Delete All actions. Hide All and Delete All respect locked layers.
- Tap a visible layer to select it as the live output.

## Live stream
- Start / Stop a local HTTP MJPEG stream on port 8080.
- Copy the URL for OBS / SplitCam.
- Photos are resized and JPEG-encoded once when added, then reused by the stream.
- Approximate 15 FPS output target; actual smoothness depends on Wi-Fi, phone performance, and the receiving app.

## Build APK
Open the project in Android Studio and run `assembleDebug`, or push it to GitHub and run **Actions > Build Photo Webcam Pro APK**. Download the `Photo-Webcam-Pro-APK` artifact.

## Connect
Connect the phone and PC to the same Wi-Fi/hotspot, add photos, keep the desired layer visible, tap Start Live, and use the displayed `http://PHONE-IP:8080/stream` URL in OBS/SplitCam. Keep the app open and phone awake while streaming. This is an MJPEG network stream, not a USB UVC webcam device.


UI update: Removed the Show All, Hide All, and Delete All bulk action buttons. Individual per-layer Lock, Show/Hide, and Delete controls remain.
