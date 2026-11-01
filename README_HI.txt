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
