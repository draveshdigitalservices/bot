package com.phonewebcam.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import java.util.UUID
import java.util.concurrent.Executors

data class MediaItem(val uri: Uri, val name: String, val mime: String, var visible: Boolean = true)

class MainActivity : Activity() {
    private val items = mutableListOf<MediaItem>()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var listBox: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var uploadButton: Button
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
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

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
        brand.addView(label("HD MEDIA STUDIO", 10, Color.rgb(210,226,255), true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("👑 PRO", 14, Color.YELLOW, true))
        root.addView(header)

        page.addView(sectionCard {
            addView(label("LIVE PREVIEW", 13, blue, true))
            val preview = FrameLayout(this@MainActivity).apply {
                background = bg(Color.rgb(225,231,240), 14)
                minimumHeight = dp(185)
            }
            val previewText = TextView(this@MainActivity).apply {
                text = "📷\nCamera preview"; textSize = 22f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(80,95,115))
            }
            preview.addView(previewText, FrameLayout.LayoutParams(-1,-1))
            val live = TextView(this@MainActivity).apply {
                text = "● CAMERA NOT STARTED"; textSize = 10f; setTextColor(Color.WHITE)
                setPadding(dp(10),dp(6),dp(10),dp(6)); background = bg(red,8)
            }
            val lp = FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply { setMargins(dp(9),dp(9),0,0) }
            preview.addView(live, lp)
            addView(preview, LinearLayout.LayoutParams(-1, dp(205)))
            addView(label("Camera preview is a UI placeholder. Media upload to PC is supported in this build.", 11, gray))
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
        page.addView(label("Note: Is build mein selected media PC ko upload hota hai. Real-time webcam streaming aur live camera preview abhi implemented nahi hain.", 11, gray))
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
        var added = 0
        picked.forEach { uri ->
            val mime = contentResolver.getType(uri) ?: if (requestCode == 10) "image/*" else "video/*"
            val name = queryName(uri) ?: "media_${System.currentTimeMillis()}"
            if (items.none { it.uri == uri }) { items.add(MediaItem(uri,name,mime)); added++ }
        }
        renderItems()
        status.text = "$added file(s) added."
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
                if (item.mime.startsWith("image")) setImageURI(item.uri)
                else setImageResource(android.R.drawable.ic_media_play)
            }
            card.addView(thumb, LinearLayout.LayoutParams(dp(62),dp(58)))
            val name = TextView(this).apply {
                text = item.name; textSize = 12f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
                maxLines = 2; setPadding(dp(8),0,dp(5),0)
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
        executor.shutdown()
        super.onDestroy()
    }
}
