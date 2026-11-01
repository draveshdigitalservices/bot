PHONE WEBCAM MULTI MEDIA - ANDROID SOURCE
==========================================

इस Android ऐप के फीचर:
- एक बार में कई फोटो चुनें
- एक बार में कई वीडियो चुनें
- हर item का अलग Show / Hide
- Remove
- Hide All / Unhide All
- Sequential / Random / Single play mode का UI
- 2, 5, 10 सेकंड या Manual interval का UI
- केवल Visible media को PC पर एक-एक करके upload करना

महत्वपूर्ण सीमाएँ:
- यह Android Studio source project है, compiled APK नहीं।
- इस environment में Android SDK/Gradle उपलब्ध नहीं था, इसलिए APK compile नहीं किया जा सका।
- Play Mode और Interval अभी UI settings हैं; वास्तविक timed playlist playback PC companion player में लागू करना अगला चरण है।
- Android ऐप की PC upload सुविधा पुराने Phone_Webcam_Starter के Flask server के /upload endpoint से जुड़ती है।
- PC और फोन एक ही Wi-Fi नेटवर्क पर रखें।

APK बनाने का तरीका:
1. Android Studio खोलें.
2. Open चुनकर इस ZIP से निकला Phone_Webcam_MultiMedia_Android folder खोलें.
3. Gradle Sync पूरा होने दें.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. APK यहां मिलेगा: app/build/outputs/apk/debug/app-debug.apk

PC Server URL:
http://PC-IP:8765/upload

PC पर start.bat चलाकर IP देखें. उदाहरण के लिए यदि PC IP 192.168.1.10 है तो ऐप में:
http://192.168.1.10:8765/upload

सुरक्षा: इसे केवल भरोसेमंद local Wi-Fi पर इस्तेमाल करें, सार्वजनिक इंटरनेट पर expose न करें।
