PHOTO WEBCAM PRO 6.0
====================
Android app: upload photos, individually Show/Hide them, select one visible photo, and stream only that selected photo to a PC over MJPEG. No automatic slideshow. Selecting another visible thumbnail switches the PC display. Hiding the selected photo switches to another visible photo; if none remain, the stream pauses.

PC / OBS / SplitCam
1. Phone and PC same Wi-Fi/hotspot par connect karein.
2. App mein CAMERA OFF - START PHOTO STREAM dabayein.
3. App ka URL dekhein, usually http://PHONE_IP:8080/mjpeg
4. OBS: Sources > + > Browser > URL paste karein. SplitCam/browser source mein bhi MJPEG URL use karein if supported.

USB (ADB reverse)
1. Android Developer Options mein USB debugging ON karein.
2. USB cable se PC connect karein aur debugging prompt Allow karein.
3. PC par Android Platform Tools (ADB) install karein.
4. PC-USB-Connect.bat chalayein. Yeh adb reverse tcp:8080 tcp:8080 set karega.
5. PC par http://127.0.0.1:8080/mjpeg open karein.
6. Disconnect ke liye PC-USB-Disconnect.bat chalayein.

APK build
GitHub repository ke root mein project upload karein. GitHub Actions > Build Android APK > Run workflow. Artifact Phone-Webcam-Debug-APK download karein.

IMPORTANT
- This project is Android source code; APK is not built in this package. GitHub Actions builds the APK.
- MJPEG URL is a browser/OBS source, not a Windows virtual camera driver. Apps that require a system webcam device need a separate virtual-camera driver/bridge.
- USB mode uses ADB reverse and requires USB debugging.
- Use only your own images and private trusted networks.
