package com.tv.ytwallpaper

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var res1080Radio: RadioButton
    private lateinit var res1440Radio: RadioButton
    private lateinit var runButton: Button
    private lateinit var cacheButton: Button
    private lateinit var statusText: TextView
    private lateinit var previewImage: ImageView
    private lateinit var previewHint: TextView
    private lateinit var gridContainer: LinearLayout

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var previewJob: Job? = null
    private var lastPreviewId: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
        }

        buildTvUi()
        refreshHistoryGrid()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun getWallpapersDir(): File {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ProjectivyWallpapers"
        )
        if (publicDir.exists() || publicDir.mkdirs()) {
            if (publicDir.canWrite()) return publicDir
        }
        return File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ProjectivyWallpapers").apply {
            mkdirs()
        }
    }

    private fun buildTvUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1216"))
            setPadding(dp(26), dp(14), dp(26), dp(14))
        }

        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val controlsCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(16)
            }
        }

        val title = TextView(this).apply {
            text = "YT Обои ТВ — Видеообои для Projectivy"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(6))
        }
        controlsCol.addView(title)

        urlInput = createInput("Вставь ссылку на YouTube (https://youtu.be/...)").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(7)
            }
        }
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                loadYoutubeThumbnail(s?.toString().orEmpty())
            }
        })
        controlsCol.addView(urlInput)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        startInput = createInput("00:00").apply {
            hint = "Начало"
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        }
        endInput = createInput("00:30").apply {
            hint = "Конец"
            layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
        }
        actionRow.addView(startInput)
        actionRow.addView(endInput)

        val id1080 = View.generateViewId()
        val id1440 = View.generateViewId()

        val radioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
        }
        res1080Radio = RadioButton(this).apply {
            id = id1080
            text = "1080p"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, dp(6), 0)
        }
        res1440Radio = RadioButton(this).apply {
            id = id1440
            text = "1440p"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        radioGroup.addView(res1080Radio)
        radioGroup.addView(res1440Radio)
        radioGroup.check(id1080)
        actionRow.addView(radioGroup)

        runButton = Button(this).apply {
            text = "СКАЧАТЬ И УСТАНОВИТЬ"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            applyFocusStyle(this, "#2563EB", "#3B82F6")
            setOnClickListener { startProcess() }
        }
        actionRow.addView(runButton)

        cacheButton = Button(this).apply {
            text = "КЭШ PROJECTIVY"
            textSize = 11f
            setTextColor(Color.parseColor("#E5E7EB"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            applyFocusStyle(this, "#1F2937", "#374151")
            setOnClickListener { openProjectivySettings() }
        }
        actionRow.addView(cacheButton)

        controlsCol.addView(actionRow)

        statusText = TextView(this).apply {
            text = "Выбери «YT Обои ТВ» в настройках обоев Projectivy"
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        }
        controlsCol.addView(statusText)

        topSection.addView(controlsCol)

        val previewBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(124))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181D24"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(2), Color.parseColor("#2D3748"))
            }
            clipToOutline = true
        }

        previewImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        previewHint = TextView(this).apply {
            text = "Превью видео"
            textSize = 12f
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewBox.addView(previewImage)
        previewBox.addView(previewHint)
        topSection.addView(previewBox)

        root.addView(topSection)

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B22"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.parseColor("#2B3544"))
            }
        }

        val bottomHeader = TextView(this).apply {
            text = "Последние 6 скачанных видео (нажми пультом, чтобы установить как обои)"
            textSize = 13f
            setTextColor(Color.parseColor("#E5E7EB"))
            setPadding(dp(4), 0, 0, dp(6))
        }
        bottomPanel.addView(bottomHeader)

        gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        bottomPanel.addView(gridContainer)

        root.addView(bottomPanel)
        setContentView(root)
        urlInput.requestFocus()
    }

    private fun applyFocusStyle(view: View, normalHex: String, focusedHex: String) {
        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor(normalHex))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#374151"))
        }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.parseColor(focusedHex))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(3), Color.parseColor("#60A5FA"))
        }
        view.background = normalBg
        view.setOnFocusChangeListener { v, hasFocus ->
            v.background = if (hasFocus) focusedBg else normalBg
        }
    }

    private fun createInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            textSize = 12f
            isSingleLine = true
            isFocusable = true
            applyFocusStyle(this, "#1E242D", "#263244")
        }
    }

    private fun extractYoutubeId(url: String): String? {
        val patterns = listOf(
            "youtu\\.be/([0-9A-Za-z_-]{11})",
            "[?&]v=([0-9A-Za-z_-]{11})",
            "shorts/([0-9A-Za-z_-]{11})",
            "embed/([0-9A-Za-z_-]{11})",
            "live/([0-9A-Za-z_-]{11})"
        )
        for (p in patterns) {
            val m = Pattern.compile(p).matcher(url)
            if (m.find()) return m.group(1)
        }
        val trimmed = url.trim()
        if (trimmed.matches(Regex("[0-9A-Za-z_-]{11}"))) return trimmed
        return null
    }

    private fun loadYoutubeThumbnail(url: String) {
        val videoId = extractYoutubeId(url.trim()) ?: return
        if (videoId == lastPreviewId) return
        lastPreviewId = videoId

        previewJob?.cancel()
        previewJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder()
                        .url("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                        } else null
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            if (bmp != null) {
                previewImage.setImageBitmap(bmp)
                previewHint.visibility = View.GONE
            }
        }
    }

    private fun extractLocalVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Throwable) {
            null
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun refreshHistoryGrid() {
        gridContainer.removeAllViews()
        val files = getWallpapersDir()
            .listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (files.size > 6) {
            files.drop(6).forEach { try { it.delete() } catch (_: Throwable) {} }
        }

        val top6 = files.take(6)
        if (top6.isEmpty()) {
            val emptyLabel = TextView(this).apply {
                text = "Пока нет скачанных видео. Вставь ссылку выше и нажми «Скачать и установить»."
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            gridContainer.addView(emptyLabel)
            return
        }

        val prefs = getSharedPreferences("yt_wallpaper_prefs", Context.MODE_PRIVATE)
        val activeName = prefs.getString("active_file", top6.first().name)
        val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

        for (rowIndex in 0 until 2) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    if (rowIndex == 0) bottomMargin = dp(8)
                }
            }

            for (colIndex in 0 until 3) {
                val itemIdx = rowIndex * 3 + colIndex
                val cellWrapper = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (colIndex < 2) marginEnd = dp(8)
                    }
                }

                if (itemIdx < top6.size) {
                    val file = top6[itemIdx]
                    val isActive = file.name == activeName
                    val card = createVideoCard(file, dateFormat, isActive)
                    cellWrapper.addView(card)
                }
                rowLayout.addView(cellWrapper)
            }
            gridContainer.addView(rowLayout)
        }
    }

    private fun createVideoCard(file: File, dateFormat: SimpleDateFormat, isActive: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isFocusable = true
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val thumbView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(52)).apply {
                marginEnd = dp(8)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#11161D"))
        }

        var cachedThumb: Bitmap? = null
        scope.launch {
            val frame = withContext(Dispatchers.IO) { extractLocalVideoFrame(file) }
            if (frame != null) {
                cachedThumb = frame
                thumbView.setImageBitmap(frame)
            }
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val prefix = if (isActive) "★ " else ""
        val nameText = TextView(this).apply {
            text = "$prefix${file.nameWithoutExtension}"
            textSize = 12f
            setTextColor(if (isActive) Color.parseColor("#60A5FA") else Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val sizeMb = String.format(Locale.US, "%.1f МБ", file.length() / (1024.0 * 1024.0))
        val metaText = TextView(this).apply {
            text = "$sizeMb • ${dateFormat.format(Date(file.lastModified()))}"
            textSize = 10f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, dp(3), 0, 0)
        }

        infoCol.addView(nameText)
        infoCol.addView(metaText)

        card.addView(thumbView)
        card.addView(infoCol)

        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1F2631"))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor(if (isActive) "#3B82F6" else "#334155"))
        }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.parseColor("#1E3A8A"))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(3), Color.parseColor("#60A5FA"))
        }
        card.background = normalBg

        card.setOnFocusChangeListener { v, hasFocus ->
            v.background = if (hasFocus) focusedBg else normalBg
            if (hasFocus && cachedThumb != null) {
                previewImage.setImageBitmap(cachedThumb)
                previewHint.visibility = View.GONE
            }
        }

        card.setOnClickListener {
            showVideoActionDialog(file)
        }

        return card
    }

    private fun showVideoActionDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle(file.nameWithoutExtension)
            .setMessage("Установить это видео как обои в Projectivy?")
            .setPositiveButton("Установить как обои") { _, _ ->
                setAsActiveWallpaper(file)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setAsActiveWallpaper(file: File) {
        getSharedPreferences("yt_wallpaper_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("active_file", file.name)
            .apply()

        refreshHistoryGrid()

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        grantUriPermission("com.spocky.projengmenu", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        updateStatus("Активные обои: ${file.nameWithoutExtension}. Если в Projectivy 4.74 не обновилось — нажми «КЭШ PROJECTIVY».")

        val projectivyIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, "video/mp4")
            setPackage("com.spocky.projengmenu")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(projectivyIntent)
        } catch (_: Throwable) {
        }
    }

    private fun openProjectivySettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.spocky.projengmenu")
            }
            startActivity(intent)
        } catch (_: Throwable) {
            updateStatus("Открой Настройки ТВ → Приложения → Projectivy Launcher → Очистить кэш")
        }
    }

    private fun startProcess() {
        val url = urlInput.text.toString().trim()
        val videoId = extractYoutubeId(url)
        if (videoId == null) {
            statusText.text = "Ошибка: не удалось распознать ссылку на YouTube."
            return
        }

        val startSec = parseTimeToSeconds(startInput.text.toString().trim(), 0L)
        val endSec = parseTimeToSeconds(endInput.text.toString().trim(), 30L)
        if (endSec <= startSec) {
            statusText.text = "Ошибка: время конца должно быть больше времени начала."
            return
        }

        val targetHeight = if (res1440Radio.isChecked) 1440 else 1080
        runButton.isEnabled = false

        scope.launch {
            val tempVideo = File(cacheDir, "temp_v.mp4")
            val tempAudio = File(cacheDir, "temp_a.m4a")
            try {
                updateStatus("1/4 Получаю потоки видео с YouTube...")
                val streamData = withContext(Dispatchers.IO) {
                    extractStreamsDirect(videoId, targetHeight)
                }

                updateStatus("2/4 Скачиваю видео (${streamData.resolution})...")
                withContext(Dispatchers.IO) {
                    downloadFile(streamData.videoUrl, tempVideo, streamData.userAgent)
                }

                if (streamData.audioUrl != null) {
                    updateStatus("3/4 Скачиваю звук (AAC)...")
                    withContext(Dispatchers.IO) {
                        downloadFile(streamData.audioUrl, tempAudio, streamData.userAgent)
                    }
                }

                val safeTitle = streamData.title
                    .replace(Regex("[^a-zA-Zа-яА-Я0-9_\\- ]"), "")
                    .trim()
                    .take(24)
                    .ifEmpty { "wallpaper" }
                val stamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                val outFile = File(getWallpapersDir(), "${safeTitle}_$stamp.mp4")

                updateStatus("4/4 Обрезаю (${startSec}с — ${endSec}с) и сохраняю...")
                withContext(Dispatchers.IO) {
                    trimAndMux(
                        videoPath = tempVideo.absolutePath,
                        audioPath = if (streamData.audioUrl != null && tempAudio.exists()) tempAudio.absolutePath else null,
                        outPath = outFile.absolutePath,
                        startUs = startSec * 1_000_000L,
                        endUs = endSec * 1_000_000L
                    )
                }

                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(outFile.absolutePath), arrayOf("video/mp4"), null)
                setAsActiveWallpaper(outFile)
            } catch (t: Throwable) {
                updateStatus("Ошибка: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                try { tempVideo.delete() } catch (_: Throwable) {}
                try { tempAudio.delete() } catch (_: Throwable) {}
                runButton.isEnabled = true
            }
        }
    }

    private data class ExtractedStreams(
        val videoUrl: String,
        val audioUrl: String?,
        val resolution: String,
        val title: String,
        val userAgent: String
    )

    private data class StreamCandidate(
        val url: String,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
        val isAvc: Boolean
    )

    private fun fetchVisitorData(userAgent: String, clientName: String, clientVersion: String): String? {
        return try {
            val payload = JSONObject().put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", clientName)
                        .put("clientVersion", clientVersion)
                        .put("hl", "ru")
                        .put("gl", "RU")
                )
            )
            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string() ?: return@use null
                val json = JSONObject(raw)
                json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractStreamsDirect(videoId: String, maxHeight: Int): ExtractedStreams {
        data class ClientProfile(
            val name: String,
            val id: String,
            val version: String,
            val userAgent: String,
            val deviceMake: String?,
            val deviceModel: String?,
            val osName: String?,
            val osVersion: String?,
            val sdkVersion: Int?
        )

        val clients = listOf(
            ClientProfile(
                name = "ANDROID_VR",
                id = "28",
                version = "1.60.19",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; Quest 3 Build/SQ3A.220605.009.A1) gzip",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L",
                sdkVersion = 32
            ),
            ClientProfile(
                name = "IOS",
                id = "5",
                version = "20.10.4",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X;)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iOS",
                osVersion = "18.3.1.22D72",
                sdkVersion = null
            ),
            ClientProfile(
                name = "ANDROID_TESTSUITE",
                id = "30",
                version = "1.9",
                userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 11; en_US) gzip",
                deviceMake = "Google",
                deviceModel = "Pixel 5",
                osName = "Android",
                osVersion = "11",
                sdkVersion = 30
            )
        )

        var lastError = "Не удалось получить потоки с YouTube"

        for (profile in clients) {
            try {
                val visitorData = fetchVisitorData(profile.userAgent, profile.name, profile.version)

                val clientObj = JSONObject().apply {
                    put("clientName", profile.name)
                    put("clientVersion", profile.version)
                    put("hl", "ru")
                    put("gl", "RU")
                    if (profile.deviceMake != null) put("deviceMake", profile.deviceMake)
                    if (profile.deviceModel != null) put("deviceModel", profile.deviceModel)
                    if (profile.osName != null) put("osName", profile.osName)
                    if (profile.osVersion != null) put("osVersion", profile.osVersion)
                    if (profile.sdkVersion != null) put("androidSdkVersion", profile.sdkVersion)
                    if (!visitorData.isNullOrEmpty()) put("visitorData", visitorData)
                }

                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put("context", JSONObject().put("client", clientObj))
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                val reqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", profile.userAgent)
                    .header("X-YouTube-Client-Name", profile.id)
                    .header("X-YouTube-Client-Version", profile.version)

                if (!visitorData.isNullOrEmpty()) {
                    reqBuilder.header("X-Goog-Visitor-Id", visitorData)
                }

                val request = reqBuilder
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        val apiMsg = try {
                            JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                        } catch (_: Throwable) {
                            null
                        }
                        lastError = if (!apiMsg.isNullOrEmpty()) "HTTP ${resp.code}: $apiMsg" else "HTTP ${resp.code}"
                        return@use
                    }

                    val root = JSONObject(bodyStr)
                    val playability = root.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status", "") ?: ""
                    if (status != "OK") {
                        lastError = playability?.optString("reason") ?: "Статус: $status"
                        return@use
                    }

                    val title = root.optJSONObject("videoDetails")?.optString("title") ?: "YT_Video"
                    val streamingData = root.optJSONObject("streamingData") ?: return@use
                    val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return@use

                    val videos = mutableListOf<StreamCandidate>()
                    val audios = mutableListOf<StreamCandidate>()

                    for (i in 0 until adaptive.length()) {
                        val item = adaptive.getJSONObject(i)
                        val streamUrl = item.optString("url", "")
                        if (streamUrl.isEmpty()) continue

                        val mime = item.optString("mimeType", "")
                        val bitrate = item.optInt("bitrate", 0)

                        if (mime.startsWith("video/mp4")) {
                            val height = item.optInt("height", 0)
                            val fps = item.optInt("fps", 30)
                            val isAvc = mime.contains("avc1", ignoreCase = true)
                            videos.add(StreamCandidate(streamUrl, height, fps, bitrate, isAvc))
                        } else if (mime.startsWith("audio/mp4")) {
                            audios.add(StreamCandidate(streamUrl, 0, 0, bitrate, true))
                        }
                    }

                    // Приоритет H.264 (avc1), так как чипы ТВ на Android 9 аппаратно декодируют его без тормозов
                    val avcVideos = videos.filter { it.isAvc }.ifEmpty { videos }
                    val bestVideo = avcVideos
                        .filter { it.height <= maxHeight }
                        .maxWithOrNull(compareBy({ it.height }, { it.fps }, { it.bitrate }))
                        ?: avcVideos.minByOrNull { it.height }

                    val bestAudio = audios.maxByOrNull { it.bitrate }

                    if (bestVideo != null) {
                        return ExtractedStreams(
                            videoUrl = bestVideo.url,
                            audioUrl = bestAudio?.url,
                            resolution = "${bestVideo.height}p",
                            title = title,
                            userAgent = profile.userAgent
                        )
                    }
                }
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            }
        }

        throw IllegalStateException(lastError)
    }

    private fun downloadFile(fileUrl: String, dest: File, userAgent: String) {
        val request = Request.Builder()
            .url(fileUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Ошибка загрузки: HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Пустой ответ сервера")
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(65536)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                }
            }
        }
    }

    private fun trimAndMux(videoPath: String, audioPath: String?, outPath: String, startUs: Long, endUs: Long) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
        val audioExtractor = if (audioPath != null) MediaExtractor().apply { setDataSource(audioPath) } else null
        val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            val vTrackSrc = selectTrack(videoExtractor, "video/")
            videoExtractor.selectTrack(vTrackSrc)
            val vFormat = videoExtractor.getTrackFormat(vTrackSrc)
            val vTrackDst = muxer.addTrack(vFormat)

            var aTrackSrc = -1
            var aTrackDst = -1
            if (audioExtractor != null) {
                aTrackSrc = findTrack(audioExtractor, "audio/")
                if (aTrackSrc >= 0) {
                    audioExtractor.selectTrack(aTrackSrc)
                    val aFormat = audioExtractor.getTrackFormat(aTrackSrc)
                    aTrackDst = muxer.addTrack(aFormat)
                }
            }

            muxer.start()

            videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = videoExtractor.sampleTime.coerceAtLeast(0L)

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            var lastVideoPts = -1L
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = videoExtractor.sampleTime
                if (sampleTime > endUs) break

                var pts = (sampleTime - actualStartUs).coerceAtLeast(0L)
                if (pts <= lastVideoPts) pts = lastVideoPts + 1000L
                lastVideoPts = pts

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = pts
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(vTrackDst, buffer, bufferInfo)
                videoExtractor.advance()
            }

            if (audioExtractor != null && aTrackSrc >= 0 && aTrackDst >= 0) {
                audioExtractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var lastAudioPts = -1L
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val sampleTime = audioExtractor.sampleTime
                    if (sampleTime > endUs) break

                    if (sampleTime >= actualStartUs) {
                        var pts = sampleTime - actualStartUs
                        if (pts <= lastAudioPts) pts = lastAudioPts + 1000L
                        lastAudioPts = pts

                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = pts
                        bufferInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(aTrackDst, buffer, bufferInfo)
                    }
                    audioExtractor.advance()
                }
            }
        } finally {
            try { muxer.stop() } catch (_: Throwable) {}
            muxer.release()
            videoExtractor.release()
            try { audioExtractor?.release() } catch (_: Throwable) {}
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        val idx = findTrack(extractor, mimePrefix)
        if (idx < 0) throw IllegalStateException("Дорожка $mimePrefix не найдена")
        return idx
    }

    private fun parseTimeToSeconds(input: String, defaultSec: Long): Long {
        if (input.isEmpty()) return defaultSec
        val parts = input.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> defaultSec
        }
    }

    private fun updateStatus(msg: String) {
        statusText.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
