# Photo Webcam Pro - Fast Stream Edition

Android photo-to-webcam app with the supplied WebCam Pro logo.

## Included features
- Upload multiple photos from Android storage.
- Per-photo Hide / Show. Hidden photos remain stored in the app list and are not sent to the stream.
- Show All / Hide All.
- Tap a shown thumbnail to switch the live output immediately.
- Start / Stop a local HTTP MJPEG stream on port 8080.
- Copy URL for OBS / SplitCam.
- Pre-scale and JPEG-encode each photo once at upload time (up to 1280x720). The stream reuses those JPEG bytes instead of re-encoding on every frame.
- Stream output targets approximately 15 FPS and accepts up to four concurrent viewers.
- WebCam Pro logo is included in the app header and launcher icon.

## Build APK
Open this project in Android Studio and run `assembleDebug`, or push it to GitHub and run **Actions > Build Photo Webcam Pro APK**. Download the `Photo-Webcam-Pro-APK` artifact.

## Connect to OBS / SplitCam
1. Connect phone and PC to the same Wi-Fi or phone hotspot.
2. Upload photos and keep the photos you want to broadcast in **Shown** state.
3. Tap **Start Live**, then copy the displayed `http://PHONE-IP:8080/stream` address.
4. In OBS, add a Media Source and paste the URL. In SplitCam, use its network/URL source if available.

The stream is an MJPEG photo feed, not a UVC webcam device. Keep the app open and the phone awake during streaming. Actual smoothness depends on Wi-Fi quality, phone performance, and the receiving app. 15 FPS is a target, not a guaranteed rate.
