@echo off
title Phone Webcam Pro - USB Disconnect
where adb >nul 2>nul
if errorlevel 1 (
  echo ADB not found.
  pause
  exit /b 1
)
adb reverse --remove tcp:8080
echo USB webcam port forwarding removed.
pause
