PHONE WEBCAM PRO v3.0
====================
- Naya Blue & White premium interface
- App logo and branding
- Photos/Videos tabs, multi-file picker
- Per-file Show/Hide/Remove
- Select All, Hide All, Unhide All
- Play mode and interval settings
- PC URL field and visible-media upload

IMPORTANT:
Is version mein media files PC receiver endpoint par upload hoti hain.
Real-time camera streaming / live webcam preview abhi implemented nahi hai.
Camera preview area design placeholder hai. PC receiver URL ko apne PC server ke
actual address se replace karein. Phone aur PC same Wi-Fi par hone chahiye.

GitHub Actions se APK build karne ke liye project ke root folder ko repository
mein upload karein. Workflow .github/workflows/build-apk.yml mein maujood hai.


UPDATE 3.1 - PHOTO PREVIEW FIX
- Phone se Photos select karne par pehli selected photo LIVE PREVIEW panel mein dikhegi.
- List mein kisi photo ke thumbnail par tap karke us photo ka preview badal sakte hain.
- Selected document URI ki read permission persist karne ki koshish hoti hai, taaki app dobara kholne par preview access bana rahe.
- Camera live preview abhi bhi alag feature hai; yeh selected photo preview hai.


VERSION 3.2 - LIVE PHONE CAMERA
- CameraX live preview added using the phone's front/back camera.
- First time START CAMERA dabane par Android camera permission maangega.
- SWITCH button se front/back camera badal sakte hain.
- This provides live preview inside the phone app. Real-time webcam streaming to a PC requires a separate PC receiver/streaming transport and is not claimed by this build.

VERSION 4.0 - WI-FI + USB PC WEBCAM STREAMING
Phone camera frames are published as an MJPEG HTTP stream on port 8080.
Wi-Fi:
1. Phone and PC ko same Wi-Fi network se connect karein.
2. App mein START PC STREAM dabayein aur displayed Wi-Fi URL note karein.
3. PC browser mein http://PHONE_IP:8080/mjpeg kholein. OBS mein Sources > + > Browser > URL paste karein.
USB:
1. Phone mein Developer options > USB debugging ON karein.
2. USB cable se PC connect karein; phone par USB debugging prompt Allow karein.
3. PC par Android Platform Tools (ADB) install karein.
4. PC terminal/CMD mein chalayein: adb devices
5. Device listed hone ke baad chalayein: adb reverse tcp:8080 tcp:8080
6. PC browser/OBS mein http://127.0.0.1:8080/mjpeg kholein.
Stop PC Stream button server ko band karta hai. USB ADB reverse sirf us PC par kaam karta hai jahan command chalayi gayi hai.
Security: stream ko trusted private Wi-Fi par hi use karein. Port 8080 par local network ke devices stream dekh sakte hain.
This is an MJPEG stream; browser and OBS Browser Source support it. It does not install a Windows virtual camera driver automatically.

Windows USB quick connect:
- PC-USB-Connect.bat ko double-click karein (ADB Platform Tools installed hone chahiye).
- Phone par USB debugging prompt Allow karein.
- PC par http://127.0.0.1:8080/mjpeg open karein.
- Disconnect ke liye PC-USB-Disconnect.bat chalayein.

VERSION 5.0 PRO - CAMERA OFF PHOTO STREAM
========================================
New feature:
- START PC STREAM starts the real phone camera and publishes MJPEG.
- While camera streaming is active, tap STOP CAMERA to turn the phone camera off and automatically switch the PC stream source to uploaded visible photos.
- Or tap CAMERA OFF - START PHOTO STREAM to start photo streaming without opening the phone camera.
- Visible uploaded photos rotate automatically using the Switch Interval setting (2/5/10/15/30 seconds).
- PC stream URL stays the same in Wi-Fi and USB mode: /mjpeg.
- Tap START CAMERA to switch back to the real phone camera.
- STOP PHOTO STREAM stops photo changes; STOP PC STREAM closes the MJPEG server completely.

Wi-Fi and USB connection steps remain the same as Version 4.0 above.
Note: Photo-stream mode sends uploaded still photos as changing MJPEG frames. It is not a real camera sensor feed while the phone camera is off.
