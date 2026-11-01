@echo off
title Phone Webcam Pro - USB Connect
echo Phone Webcam Pro USB connection
echo.
where adb >nul 2>nul
if errorlevel 1 (
  echo ADB not found. Install Android Platform Tools and add its folder to PATH.
  echo Download: https://developer.android.com/tools/releases/platform-tools
  pause
  exit /b 1
)
echo Unlock your phone and accept the USB debugging prompt.
adb devices
echo.
adb reverse tcp:8080 tcp:8080
if errorlevel 1 (
  echo USB forwarding failed. Check the USB cable and USB debugging permission.
  pause
  exit /b 1
)
echo.
echo Connected. Open this URL on the PC:
echo http://127.0.0.1:8080/mjpeg
pause
