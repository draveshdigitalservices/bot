package com.phonewebcam.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
    private var uploading = false

    private val blue = Color.rgb(24, 99, 219)
    private val dark = Color.rgb(11, 20, 36)
    private val pale = Color.rgb(235, 243, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        renderItems()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(dark) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(label("PHONE WEBCAM PRO", 25, Color.WHITE, true))
        page.addView(label("Multiple Photos • Multiple Videos • PC Live", 13, Color.LTGRAY))
        page.addView(space(14))

        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addRow.addView(actionButton("＋ Add Photos", blue) { pick("image/*") }, weight())
        addRow.addView(spaceHorizontal(8))
        addRow.addView(actionButton("＋ Add Videos", blue) { pick("video/*") }, weight())
        page.addView(addRow)
        page.addView(space(10))

        count = label("Media: 0", 15, Color.WHITE, true)
        page.addView(count)
        val allRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        allRow.addView(actionButton("Select All", Color.rgb(55, 75, 105)) {
            Toast.makeText(this@MainActivity, "सभी मीडिया सूची में जोड़ा जा चुका है।", Toast.LENGTH_SHORT).show()
        }, weight())
        allRow.addView(spaceHorizontal(6))
        allRow.addView(actionButton("Hide All", Color.rgb(90, 100, 120)) {
            items.forEach { it.visible = false }; renderItems()
        }, weight())
        allRow.addView(spaceHorizontal(6))
        allRow.addView(actionButton("Unhide All", Color.rgb(20, 145, 70)) {
            items.forEach { it.visible = true }; renderItems()
        }, weight())
        page.addView(allRow)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        page.addView(listBox)

        page.addView(label("PC CONNECTION", 17, Color.WHITE, true))
        urlInput = EditText(this).apply {
            setSingleLine(true)
            setText("http://192.168.1.100:8765/upload")
            hint = "http://PC-IP:8765/upload"
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(30, 48, 75))
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        page.addView(urlInput)
        page.addView(label("Phone aur PC same Wi-Fi par hon. PC server start.bat se chalu karein.", 12, Color.LTGRAY))
        page.addView(space(8))

        page.addView(label("Play Mode", 14, Color.WHITE, true))
        playMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Sequential", "Random", "Single"))
        }
        page.addView(playMode)
        page.addView(label("Switch Interval", 14, Color.WHITE, true))
        interval = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("2 seconds", "5 seconds", "10 seconds", "Manual"))
        }
        page.addView(interval)
        page.addView(space(10))

        uploadButton = actionButton("UPLOAD VISIBLE MEDIA TO PC", Color.rgb(20, 145, 70)) { uploadVisible() }
        page.addView(uploadButton)
        status = label("Ready. Pehle photos/videos add karein.", 14, Color.rgb(150, 195, 255))
        page.addView(status)
        page.addView(space(12))
        page.addView(label("Note: yeh version media ko PC par upload karta hai. PC par live playlist playback ke liye companion PC player bhi chahiye.", 12, Color.LTGRAY))
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(48), 1f)

    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun spaceHorizontal(w: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), 1)
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun actionButton(text: String, color: Int, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            setOnClickListener { action() }
        }

    private fun pick(type: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, if (type.startsWith("image")) 10 else 11)
    }

    @Deprecated("Legacy result callback supports broad Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val picked = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri)
        }
        if (picked.isEmpty() && data.data != null) picked.add(data.data!!)
        var added = 0
        picked.forEach { uri ->
            val mime = contentResolver.getType(uri) ?: if (requestCode == 10) "image/*" else "video/*"
            val name = queryName(uri) ?: "media_${System.currentTimeMillis()}"
            if (items.none { it.uri == uri }) {
                items.add(MediaItem(uri, name, mime))
                added++
            }
        }
        renderItems()
        status.text = "$added file(s) added."
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i)
        }
        return uri.lastPathSegment
    }

    private fun renderItems() {
        if (!::listBox.isInitialized) return
        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(label("Abhi koi photo/video add nahi hai.", 14, Color.LTGRAY))
        }
        items.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(Color.rgb(23, 37, 59))
            }
            val title = TextView(this).apply {
                text = "${if (item.mime.startsWith("image")) "🖼" else "▶"}  ${item.name}"
                textSize = 14f; setTextColor(Color.WHITE)
            }
            card.addView(title)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(actionButton(if (item.visible) "Hide" else "Show",
                if (item.visible) Color.rgb(90, 100, 120) else Color.rgb(20, 145, 70)) {
                item.visible = !item.visible; renderItems()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(spaceHorizontal(6))
            row.addView(actionButton("Remove", Color.rgb(190, 45, 55)) {
                items.removeAt(index); renderItems()
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            card.addView(row)
            val outer = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
            listBox.addView(card, outer)
        }
        count.text = "Media: ${items.size}  |  Visible: ${items.count { it.visible }}  |  Hidden: ${items.count { !it.visible }}"
    }

    private fun uploadVisible() {
        if (uploading) return
        val selected = items.filter { it.visible }
        if (selected.isEmpty()) {
            status.text = "Koi visible media nahi hai. Unhide karein ya files add karein."
            return
        }
        val endpoint = urlInput.text.toString().trim()
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            status.text = "PC Server URL sahi dalein."
            return
        }
        uploading = true
        uploadButton.isEnabled = false
        status.text = "Uploading ${selected.size} file(s)..."
        executor.execute {
            var ok = 0
            val errors = mutableListOf<String>()
            selected.forEach { item ->
                try { uploadOne(endpoint, item); ok++ }
                catch (e: Exception) { errors.add("${item.name}: ${e.message}") }
                runOnUiThread { status.text = "Uploaded $ok / ${selected.size}" }
            }
            runOnUiThread {
                uploading = false
                uploadButton.isEnabled = true
                status.text = "Upload complete: $ok/${selected.size}" +
                    if (errors.isNotEmpty()) "\n" + errors.take(2).joinToString("\n") else ""
            }
        }
    }

    private fun uploadOne(endpoint: String, item: MediaItem) {
        val boundary = "----PhoneWebcam${UUID.randomUUID()}"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 120000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        val safeName = item.name.replace("\"", "").replace("\r", "").replace("\n", "")
        conn.outputStream.use { out ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"media\"; filename=\"$safeName\"\r\n".toByteArray())
            out.write("Content-Type: ${item.mime}\r\n\r\n".toByteArray())
            contentResolver.openInputStream(item.uri).use { input ->
                requireNotNull(input) { "File read nahi ho saki" }
                input.copyTo(out)
            }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw Exception("HTTP $code $body")
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
