package com.phonewebcam.app

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.Inet4Address
import java.net.NetworkInterface
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.ByteArrayOutputStream
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import java.util.UUID
import java.util.concurrent.Executors

data class MediaItem(val uri: Uri, val name: String, val mime: String, var visible: Boolean = true)

class MainActivity : ComponentActivity() {
    private val items = mutableListOf<MediaItem>()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var listBox: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var uploadButton: Button
    private lateinit var previewImage: ImageView
    private lateinit var cameraPreviewView: PreviewView
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    @Volatile private var latestJpeg: ByteArray? = null
    private var mjpegServer: MjpegServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureLoopRunning = false
    @Volatile private var photoStreamRunning = false
    private var photoStreamIndex = 0
    private var photoStreamTask: Runnable? = null
    private lateinit var streamStatus: TextView
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraRunning = false
    private lateinit var cameraButton: Button
    private lateinit var switchCameraButton: Button
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startPhoneCamera() else {
            status.text = "Camera permission denied. Android Settings mein Camera permission allow karein."
            previewLive.text = "● CAMERA PERMISSION NEEDED"
            previewLive.background = bg(red, 8)
        }
    }
    private lateinit var previewText: TextView
    private lateinit var previewLive: TextView
    private lateinit var interval: Spinner
    private lateinit var playMode: Spinner
    private lateinit var tabs: LinearLayout
    private var activeTab = "Photos"
    private var uploading = false
    private var loopPlay = true
    private var autoNext = true

    private val blue = Color.rgb(18, 83, 190)
    private val blue2 = Color.rgb(35, 105, 230)
    private val pale = Color.rgb(239, 245, 255)
    private val ink = Color.rgb(25, 35, 52)
    private val green = Color.rgb(0, 150, 55)
    private val red = Color.rgb(225, 35, 45)
    private val gray = Color.rgb(105, 113, 125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 55, 137)
        window.navigationBarColor = Color.BLACK
        buildUi()
        renderItems()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int = 16, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(246,248,252)) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(22))
        }
        scroll.addView(page)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(14), dp(17), dp(14)); setBackgroundColor(blue)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_phone_webcam_logo); scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(43), dp(43)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10),0,0,0) }
        brand.addView(label("Phone Webcam Pro", 21, Color.WHITE, true))
        brand.addView(label("HD MEDIA STUDIO  •  v5.1 PRO", 10, Color.rgb(210,226,255), true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("👑 PRO", 14, Color.YELLOW, true))
        // Keep the Phone Webcam Pro brand header fixed at the very top.
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        page.addView(sectionCard {
            addView(label("LIVE PREVIEW", 13, blue, true))
            val preview = FrameLayout(this@MainActivity).apply {
                background = bg(Color.rgb(225,231,240), 14)
                minimumHeight = dp(185)
            }
            cameraPreviewView = PreviewView(this@MainActivity).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            preview.addView(cameraPreviewView, FrameLayout.LayoutParams(-1,-1))
            previewImage = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
                contentDescription = "Selected photo preview"
            }
            preview.addView(previewImage, FrameLayout.LayoutParams(-1,-1))
            previewText = TextView(this@MainActivity).apply {
                text = "📷\nCamera preview\n\nSelect a photo to see its preview here"
                textSize = 19f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(80,95,115))
            }
            preview.addView(previewText, FrameLayout.LayoutParams(-1,-1))
            previewLive = TextView(this@MainActivity).apply {
                text = "● CAMERA NOT STARTED"; textSize = 10f; setTextColor(Color.WHITE)
                setPadding(dp(10),dp(6),dp(10),dp(6)); background = bg(red,8)
            }
            val lp = FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply { setMargins(dp(9),dp(9),0,0) }
            preview.addView(previewLive, lp)
            addView(preview, LinearLayout.LayoutParams(-1, dp(205)))
            val cameraControls = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            cameraButton = actionButton("▶ START CAMERA", green) {
                if (cameraRunning) {
                    val hasPhotos = items.any { it.mime.startsWith("image/") && it.visible }
                    if (mjpegServer?.isAlive == true && hasPhotos) startPhotoStream() else stopPhoneCamera()
                } else if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startPhoneCamera()
                else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
            switchCameraButton = actionButton("↻ SWITCH", blue2) {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                if (photoStreamRunning) stopPhotoStream()
                if (cameraRunning) startPhoneCamera()
            }
            cameraControls.addView(cameraButton, LinearLayout.LayoutParams(0, dp(44), 1f))
            cameraControls.addView(spaceHorizontal(8))
            cameraControls.addView(switchCameraButton, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(cameraControls)
            addView(label("Phone camera live preview. Start Camera dabayein, phir PC par webcam receiver/streaming setup se connect karein.", 11, gray))
        })
        page.addView(space(10))
        page.addView(sectionCard {
            addView(label("PC WEBCAM STREAMING", 15, blue, true))
            addView(label("Wi-Fi: phone aur PC same network par. USB: USB debugging + PC par ADB reverse.", 11, gray))
            val streamRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            streamRow.addView(actionButton("▶ START PC STREAM", green) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startPcStream()
                else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            streamRow.addView(spaceHorizontal(8))
            streamRow.addView(actionButton("■ STOP", red) { stopPcStream() }, LinearLayout.LayoutParams(dp(90), dp(46)))
            addView(streamRow)
            addView(actionButton("▣ CAMERA OFF  •  START PHOTO STREAM", blue2) { startPhotoStream() }, LinearLayout.LayoutParams(-1, dp(46)))
            addView(actionButton("■ STOP PHOTO STREAM", gray) { stopPhotoStream() }, LinearLayout.LayoutParams(-1, dp(42)))
            streamStatus = label("Stream stopped. Camera Live ya Camera OFF + Photo Stream chunein.", 11, ink)
            addView(streamStatus)
            addView(actionButton("COPY WI-FI URL", blue2) {
                val ip = getLocalIpAddress()
                val url = if (ip == null) "Wi-Fi connect karein" else "http://$ip:8080/mjpeg"
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phone Webcam URL", url))
                status.text = "Wi-Fi URL copied: $url"
            })
        })
        page.addView(space(10))

        tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; background = bg(Color.rgb(231,238,249),14); setPadding(dp(4),dp(4),dp(4),dp(4))
        }
        listOf("Photos", "Videos", "Settings").forEach { tab ->
            val b = actionButton(if (tab == "Photos") "▧  Photos" else if (tab == "Videos") "▶  Videos" else "⚙  Settings",
                if (tab == activeTab) blue else Color.rgb(231,238,249),
                if (tab == activeTab) Color.WHITE else ink) { activeTab = tab; updateTabs(); renderItems() }
            tabs.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        page.addView(tabs)
        page.addView(space(10))

        val mediaActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        mediaActions.addView(actionButton("＋ Add Photos", green) { pick("image/*") }, LinearLayout.LayoutParams(0, dp(46), 1f))
        mediaActions.addView(spaceHorizontal(8))
        mediaActions.addView(actionButton("＋ Add Videos", blue2) { pick("video/*") }, LinearLayout.LayoutParams(0, dp(46), 1f))
        page.addView(mediaActions)
        page.addView(space(8))

        val bulk = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bulk.addView(actionButton("☑ Select All", blue) { items.forEach { it.visible = true }; renderItems() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        bulk.addView(spaceHorizontal(6))
        bulk.addView(actionButton("Hide All", gray) { items.forEach { it.visible = false }; renderItems() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        bulk.addView(spaceHorizontal(6))
        bulk.addView(actionButton("Unhide All", green) { items.forEach { it.visible = true }; renderItems() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        page.addView(bulk)
        count = label("Photos (0)", 15, ink, true)
        page.addView(count)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(listBox)

        page.addView(space(8))
        page.addView(sectionCard {
            addView(label("Multi Play Options", 18, blue, true))
            addView(label("Play Mode", 13, ink, true))
            playMode = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Single", "Multiple", "Random"))
            }
            addView(playMode)
            addView(label("Switch Interval", 13, ink, true))
            interval = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("2 Sec", "5 Sec", "10 Sec", "15 Sec", "30 Sec", "Manual"))
            }
            addView(interval)
            addView(toggleRow("Loop Play", loopPlay) { loopPlay = it })
            addView(toggleRow("Auto Next (Multiple)", autoNext) { autoNext = it })
        })
        page.addView(space(10))

        page.addView(sectionCard {
            addView(label("PC CONNECTION", 17, blue, true))
            urlInput = EditText(this@MainActivity).apply {
                setSingleLine(true); setText("http://192.168.1.100:8765/upload")
                hint = "http://PC-IP:8765/upload"; textSize = 13f
                setTextColor(ink); setHintTextColor(gray); background = bg(Color.WHITE,10,Color.rgb(205,216,232))
                setPadding(dp(12),dp(5),dp(12),dp(5))
            }
            addView(urlInput)
            addView(label("Phone aur PC same Wi-Fi par hon. PC par receiver/server chalu rakhein.", 11, gray))
        })
        page.addView(space(10))
        uploadButton = actionButton("▶  START LIVE  (SEND TO PC)", green) { uploadVisible() }
        page.addView(uploadButton, LinearLayout.LayoutParams(-1, dp(54)))
        val stop = actionButton("■  STOP", Color.WHITE, red) {
            status.text = "Upload/transfer start hone se pehle hi roka ja sakta hai. Running upload ko complete hone dein."
        }
        stop.background = bg(Color.WHITE,12,red)
        page.addView(stop, LinearLayout.LayoutParams(-1, dp(48)))
        status = label("Ready. Photos ya videos add karein.", 13, blue, true)
        page.addView(status)
        page.addView(space(8))
        page.addView(label("Live camera preview phone par chalta hai. Media upload alag feature hai; PC webcam streaming ke liye PC receiver/stream transport bhi zaroori hai.", 11, gray))
    }

    private fun updateTabs() {
        for (i in 0 until tabs.childCount) {
            val b = tabs.getChildAt(i) as Button
            val selected = listOf("Photos","Videos","Settings")[i] == activeTab
            b.background = bg(if (selected) blue else Color.rgb(231,238,249), 11)
            b.setTextColor(if (selected) Color.WHITE else ink)
        }
    }

    private fun sectionCard(content: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13),dp(12),dp(13),dp(12))
            background = bg(Color.WHITE,16,Color.rgb(222,230,242))
            elevation = dp(2).toFloat()
            content()
        }

    private fun toggleRow(title: String, initial: Boolean, changed: (Boolean) -> Unit): LinearLayout {
        var state = initial
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(label(title,14,ink,true), LinearLayout.LayoutParams(0,-2,1f))
            val sw = Switch(this@MainActivity).apply {
                isChecked = state
                setOnCheckedChangeListener { _, checked -> state = checked; changed(checked) }
            }
            addView(sw)
        }
    }

    private fun weight() = LinearLayout.LayoutParams(0, dp(46), 1f)
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun spaceHorizontal(w: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(w), 1) }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size.toFloat(); setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0,dp(5),0,dp(5))
        }

    private fun actionButton(text: String, color: Int, textColor: Int = Color.WHITE, action: () -> Unit) =
        Button(this).apply {
            this.text = text; textSize = 12f; isAllCaps = false; setTextColor(textColor)
            background = bg(color,11)
            setPadding(dp(5),0,dp(5),0)
            setOnClickListener { action() }
        }

    private fun pick(type: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); this.type = type
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, if (type.startsWith("image")) 10 else 11)
    }

    @Deprecated("Legacy result callback supports broad Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val picked = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri) }
        if (picked.isEmpty() && data.data != null) picked.add(data.data!!)
        // Keep access to selected documents so previews still work after reopening the app.
        val grantedFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        picked.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* Some document providers do not offer persistable grants. */ }
        }
        var added = 0
        picked.forEach { uri ->
            val mime = contentResolver.getType(uri) ?: if (requestCode == 10) "image/*" else "video/*"
            val name = queryName(uri) ?: "media_${System.currentTimeMillis()}"
            if (items.none { it.uri == uri }) { items.add(MediaItem(uri,name,mime)); added++ }
        }
        renderItems()
        val newestPhoto = items.lastOrNull { it.mime.startsWith("image") }
        // Uploading a photo must not cover an active camera preview or interrupt photo streaming.
        if (newestPhoto != null && !cameraRunning && !photoStreamRunning) showPhotoPreview(newestPhoto)
        status.text = "$added file(s) added. Tap any photo thumbnail to preview it above."
    }

    private fun showPhotoPreview(item: MediaItem) {
        if (!::previewImage.isInitialized || !item.mime.startsWith("image")) return
        try {
            previewImage.setImageURI(null)
            previewImage.setImageURI(item.uri)
            previewImage.visibility = View.VISIBLE
            previewText.visibility = View.GONE
            previewLive.text = "● PHOTO PREVIEW"
            previewLive.background = bg(green, 8)
        } catch (e: Exception) {
            previewImage.setImageDrawable(null)
            previewImage.visibility = View.GONE
            previewText.text = "Photo preview could not be loaded.\nPlease select the photo again."
            previewText.visibility = View.VISIBLE
            previewLive.text = "● PREVIEW ERROR"
            previewLive.background = bg(red, 8)
        }
    }

    private fun startPhoneCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val captureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(70)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, previewUseCase, captureUseCase)
                imageCapture = captureUseCase
                if (photoStreamRunning) stopPhotoStream()
                captureLoopRunning = false
                if (mjpegServer?.isAlive == true) startFrameCaptureLoop()
                cameraRunning = true
                previewImage.visibility = View.GONE
                previewText.visibility = View.GONE
                cameraPreviewView.visibility = View.VISIBLE
                previewLive.text = "● LIVE CAMERA"
                previewLive.background = bg(green, 8)
                cameraButton.text = "■ STOP CAMERA"
                status.text = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Front camera live hai." else "Back camera live hai."
                if (mjpegServer?.isAlive == true) {
                    startFrameCaptureLoop()
                    updateStreamStatus()
                }
            } catch (e: Exception) {
                cameraRunning = false
                previewLive.text = "● CAMERA ERROR"
                previewLive.background = bg(red, 8)
                status.text = "Camera start nahi hua: ${e.localizedMessage ?: "Unknown error"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun ensureMjpegServer() {
        if (mjpegServer == null || mjpegServer?.isAlive != true) {
            mjpegServer = MjpegServer(8080) { latestJpeg }
            mjpegServer!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
    }

    private fun startPcStream() {
        try {
            ensureMjpegServer()
            streamStatus.text = "Starting phone camera and PC stream..."
            if (photoStreamRunning) {
                updateStreamStatus()
            } else if (cameraRunning) {
                startFrameCaptureLoop()
                updateStreamStatus()
            } else startPhoneCamera()
        } catch (e: Exception) {
            streamStatus.text = "Stream server error: ${e.localizedMessage}"
        }
    }

    // Camera OFF mode: uploaded photos become frames while the MJPEG server stays alive.
    private fun startPhotoStream() {
        if (items.none { it.mime.startsWith("image/") && it.visible }) {
            status.text = "Pehle kam se kam ek photo Add karein aur use visible rakhein."
            streamStatus.text = "No visible photos available for Camera OFF stream."
            return
        }
        try {
            captureLoopRunning = false
            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
            cameraRunning = false
            imageCapture = null
            cameraButton.text = "▶ START CAMERA"
            cameraPreviewView.visibility = View.GONE
            previewImage.visibility = View.GONE
            previewText.text = "▣\nCAMERA OFF\nPhoto slideshow is streaming to PC"
            previewText.visibility = View.VISIBLE
            previewLive.text = "● CAMERA OFF  •  PHOTO STREAM ON"
            previewLive.background = bg(green, 8)

            ensureMjpegServer()
            photoStreamRunning = true
            photoStreamIndex = 0
            photoStreamTask?.let { mainHandler.removeCallbacks(it) }
            val task = object : Runnable {
                override fun run() {
                    if (!photoStreamRunning || mjpegServer?.isAlive != true) return
                    val current = items.filter { it.mime.startsWith("image/") && it.visible }
                    if (current.isEmpty()) {
                        streamStatus.text = "Photo stream paused: no visible photos."
                        mainHandler.postDelayed(this, 1000)
                        return
                    }
                    if (photoStreamIndex >= current.size) photoStreamIndex = 0
                    val item = current[photoStreamIndex]
                    executor.execute {
                        try {
                            val bytes = photoUriToJpeg(item.uri)
                            if (bytes != null && photoStreamRunning) latestJpeg = bytes
                            runOnUiThread {
                                if (photoStreamRunning) {
                                    previewText.text = "▣\nCAMERA OFF\nStreaming: ${item.name}"
                                    streamStatus.text = "CAMERA OFF • PHOTO STREAM LIVE\n${item.name}\n${streamUrlText()}"
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { if (photoStreamRunning) status.text = "Photo frame error: ${e.localizedMessage}" }
                        }
                    }
                    photoStreamIndex = (photoStreamIndex + 1) % current.size
                    mainHandler.postDelayed(this, selectedIntervalMs())
                }
            }
            photoStreamTask = task
            mainHandler.post(task)
            status.text = "Camera OFF. Uploaded photos are now keeping the PC stream live."
            updateStreamStatus()
        } catch (e: Exception) {
            photoStreamRunning = false
            streamStatus.text = "Photo stream start error: ${e.localizedMessage}"
        }
    }

    private fun selectedIntervalMs(): Long {
        if (!::interval.isInitialized) return 2000L
        val value = interval.selectedItem?.toString()?.substringBefore(" ")?.toLongOrNull() ?: 2L
        return value.coerceIn(1L, 60L) * 1000L
    }

    private fun photoUriToJpeg(uri: Uri): ByteArray? {
        val source = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val maxSide = 1280
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true) else source
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        if (bitmap !== source) bitmap.recycle()
        source.recycle()
        return output.toByteArray()
    }

    private fun stopPhotoStream() {
        photoStreamRunning = false
        photoStreamTask?.let { mainHandler.removeCallbacks(it) }
        photoStreamTask = null
        if (!cameraRunning && ::previewText.isInitialized) {
            previewLive.text = "● CAMERA OFF"
            previewLive.background = bg(gray, 8)
            previewText.text = "▣\nCamera OFF\nPhoto stream stopped"
            previewText.visibility = View.VISIBLE
        }
        streamStatus.text = "Photo slideshow stopped. Use STOP PC STREAM to close the server."
        status.text = "Photo stream stopped. PC stream server remains on until STOP PC STREAM."
    }

    private fun streamUrlText(): String {
        val ip = getLocalIpAddress()
        return "Wi-Fi: ${if (ip != null) "http://$ip:8080/mjpeg" else "IP unavailable"}\nUSB: http://127.0.0.1:8080/mjpeg"
    }

    private fun updateStreamStatus() {
        val source = if (photoStreamRunning) "CAMERA OFF • PHOTO STREAM LIVE" else if (cameraRunning) "PHONE CAMERA LIVE" else "STREAM SERVER READY"
        streamStatus.text = "$source\n${streamUrlText()}\nPC browser/OBS mein URL open karein."
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun startFrameCaptureLoop() {
        if (photoStreamRunning || captureLoopRunning) return
        captureLoopRunning = true
        lateinit var taskRef: Runnable
        taskRef = object : Runnable {
            override fun run() {
                if (!captureLoopRunning || mjpegServer?.isAlive != true) return
                val capture = imageCapture
                if (capture == null) {
                    mainHandler.postDelayed(this, 250)
                    return
                }
                try {
                    capture.takePicture(executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try { latestJpeg = imageProxyToJpeg(image) }
                                catch (_: Exception) {}
                                finally { image.close() }
                                mainHandler.postDelayed(taskRef, 250)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                mainHandler.postDelayed(taskRef, 500)
                            }
                        })
                } catch (_: Exception) { mainHandler.postDelayed(this, 500) }
            }
        }
        mainHandler.post(taskRef)
    }

    private fun imageProxyToJpeg(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val nv21 = ByteArray(width * height * 3 / 2)
        var out = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                nv21[out++] = yBuffer.get(row * yPlane.rowStride + col * yPlane.pixelStride)
            }
        }
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val ui = row * uPlane.rowStride + col * uPlane.pixelStride
                val vi = row * vPlane.rowStride + col * vPlane.pixelStride
                nv21[out++] = vBuffer.get(vi)
                nv21[out++] = uBuffer.get(ui)
            }
        }
        val output = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 70, output)
        return output.toByteArray()
    }

    private fun stopPcStream() {
        stopPhotoStream()
        captureLoopRunning = false
        try { mjpegServer?.stop() } catch (_: Exception) {}
        mjpegServer = null
        latestJpeg = null
        streamStatus.text = "PC stream stopped."
    }

    private fun stopPhoneCamera() {
        captureLoopRunning = false
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        cameraRunning = false
        cameraPreviewView.visibility = View.VISIBLE
        previewImage.visibility = View.GONE
        previewText.text = "📷\\nCamera stopped\\n\\nTap START CAMERA to resume"
        previewText.visibility = View.VISIBLE
        previewLive.text = "● CAMERA STOPPED"
        previewLive.background = bg(red, 8)
        cameraButton.text = "▶ START CAMERA"
        status.text = "Phone camera stopped."
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri,null,null,null,null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i)
        }
        return uri.lastPathSegment
    }

    private fun renderItems() {
        if (!::listBox.isInitialized) return
        listBox.removeAllViews()
        val filtered = items.withIndex().filter {
            activeTab == "Settings" || (activeTab == "Photos" && it.value.mime.startsWith("image")) ||
                (activeTab == "Videos" && it.value.mime.startsWith("video"))
        }
        if (filtered.isEmpty()) listBox.addView(label(if (activeTab == "Settings") "Settings options neeche hain." else "Abhi koi ${activeTab.lowercase()} add nahi hai.",13,gray))
        filtered.forEach { indexed ->
            val index = indexed.index
            val item = indexed.value
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8),dp(7),dp(8),dp(7)); background = bg(Color.WHITE,12,Color.rgb(225,232,242))
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = bg(pale,8)
                if (item.mime.startsWith("image")) {
                    try { setImageURI(item.uri) } catch (_: Exception) { setImageResource(android.R.drawable.ic_menu_gallery) }
                } else setImageResource(android.R.drawable.ic_media_play)
                setOnClickListener {
                    if (item.mime.startsWith("image")) {
                        if (!cameraRunning && !photoStreamRunning) showPhotoPreview(item)
                        status.text = if (cameraRunning) "Live camera remains active. Selected: ${item.name}" else if (photoStreamRunning) "Photo stream continues. Selected: ${item.name}" else "Preview: ${item.name}"
                    } else {
                        status.text = "Selected video: ${item.name}. Video playback preview is not available yet."
                    }
                }
            }
            card.addView(thumb, LinearLayout.LayoutParams(dp(78),dp(72)))
            val name = TextView(this).apply {
                text = item.name; textSize = 12f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding(dp(8),0,dp(5),0)
            }
            card.addView(name, LinearLayout.LayoutParams(0,-2,1f))
            val show = actionButton(if(item.visible) "◉ Show" else "Hide",
                if(item.visible) green else gray) {
                item.visible = !item.visible; renderItems()
            }
            card.addView(show, LinearLayout.LayoutParams(dp(74),dp(39)))
            val del = actionButton("✕", red) { items.removeAt(index); renderItems() }
            card.addView(del, LinearLayout.LayoutParams(dp(43),dp(39)))
            val outer = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(6) }
            listBox.addView(card,outer)
        }
        count.text = "${activeTab}: ${filtered.size}  |  Visible: ${items.count { it.visible }}  |  Hidden: ${items.count { !it.visible }}"
    }

    private fun uploadVisible() {
        if (uploading) return
        val selected = items.filter { it.visible }
        if (selected.isEmpty()) { status.text = "Koi visible media nahi hai. Files add ya unhide karein."; return }
        val endpoint = urlInput.text.toString().trim()
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) { status.text = "PC Server URL sahi dalein."; return }
        uploading = true; uploadButton.isEnabled = false
        status.text = "Uploading ${selected.size} file(s)..."
        executor.execute {
            var ok = 0
            val errors = mutableListOf<String>()
            selected.forEach { item ->
                try { uploadOne(endpoint,item); ok++ } catch(e: Exception) { errors.add("${item.name}: ${e.message}") }
                runOnUiThread { status.text = "Uploaded $ok / ${selected.size}" }
            }
            runOnUiThread {
                uploading = false; uploadButton.isEnabled = true
                status.text = "Upload complete: $ok/${selected.size}" +
                    if(errors.isNotEmpty()) "\n" + errors.take(2).joinToString("\n") else ""
            }
        }
    }

    private fun uploadOne(endpoint: String, item: MediaItem) {
        val boundary = "----PhoneWebcam${UUID.randomUUID()}"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 120000
            setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
        }
        val safeName = item.name.replace("\"","").replace("\r","").replace("\n","")
        conn.outputStream.use { out ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"media\"; filename=\"$safeName\"\r\n".toByteArray())
            out.write("Content-Type: ${item.mime}\r\n\r\n".toByteArray())
            contentResolver.openInputStream(item.uri).use { input ->
                requireNotNull(input) { "File read nahi ho saki" }; input.copyTo(out)
            }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        val body = (if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if(code !in 200..299) throw Exception("HTTP $code $body")
    }

    override fun onDestroy() {
        photoStreamRunning = false
        photoStreamTask?.let { mainHandler.removeCallbacks(it) }
        captureLoopRunning = false
        try { mjpegServer?.stop() } catch (_: Exception) {}
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        executor.shutdown()
        super.onDestroy()
    }
}


private class MjpegServer(
    port: Int,
    private val frameProvider: () -> ByteArray?
) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/", "/status" -> newFixedLengthResponse(
                Response.Status.OK, "text/plain; charset=utf-8",
                "Phone Webcam Pro is running. Open /mjpeg for the live camera stream."
            )
            "/snapshot" -> {
                val frame = frameProvider()
                if (frame == null) newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Camera warming up")
                else newFixedLengthResponse(Response.Status.OK, "image/jpeg", java.io.ByteArrayInputStream(frame), frame.size.toLong())
            }
            "/mjpeg" -> {
                val input = PipedInputStream(256 * 1024)
                val output = PipedOutputStream(input)
                Thread({
                    try {
                        while (isAlive) {
                            val frame = frameProvider()
                            if (frame != null) {
                                output.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
                                output.write(frame)
                                output.write("\r\n".toByteArray())
                                output.flush()
                            }
                            Thread.sleep(120)
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { output.close() } catch (_: Exception) {}
                    }
                }, "PhoneWebcam-MJPEG").apply { isDaemon = true }.start()
                newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=frame", input)
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }
}
