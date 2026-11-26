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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
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
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            NewPipe.init(OkHttpDownloader(httpClient), Localization("ru", "RU"))
        } catch (_: Throwable) {
        }

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
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ProjectivyWallpapers"
        ).apply { mkdirs() }
    }

    private fun buildTvUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1216"))
            setPadding(dp(26), dp(14), dp(26), dp(14))
        }

        // === ВЕРХНЯЯ КОМПАКТНАЯ ЧАСТЬ + ПРЕВЬЮ В ПРАВОМ ВЕРХНЕМ УГЛУ ===
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

        // Компактная строка управления
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

        // Правый верхний прямоугольник превью (16:9)
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

        // === ВЫДЕЛЕННАЯ НИЖНЯЯ ОБЛАСТЬ (6 ВИДЕО: 2 РЯДА ПО 3) ===
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
            "(?:v=|/)([0-9A-Za-z_-]{11}).*",
            "youtu\\.be/([0-9A-Za-z_-]{11})",
            "shorts/([0-9A-Za-z_-]{11})"
        )
        for (p in patterns) {
            val m = Pattern.compile(p).matcher(url)
            if (m.find()) return m.group(1)
        }
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
        if (url.isEmpty()) {
            statusText.text = "Ошибка: вставь ссылку на YouTube."
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
                    extractStreams(url, targetHeight)
                }

                updateStatus("2/4 Скачиваю видео (${streamData.resolution} H.264)...")
                withContext(Dispatchers.IO) { downloadFile(streamData.videoUrl, tempVideo) }

                updateStatus("3/4 Скачиваю звук (AAC)...")
                withContext(Dispatchers.IO) { downloadFile(streamData.audioUrl, tempAudio) }

                val safeTitle = streamData.title
                    .replace(Regex("[^a-zA-Zа-яА-Я0-9_\\- ]"), "")
                    .trim()
                    .take(24)
                    .ifEmpty { "wallpaper" }
                val stamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                val outFile = File(getWallpapersDir(), "${safeTitle}_$stamp.mp4")

                updateStatus("4/4 Обрезаю (${startSec}с — ${endSec}с) и склеиваю со звуком...")
                withContext(Dispatchers.IO) {
                    trimAndMux(
                        tempVideo.absolutePath,
                        tempAudio.absolutePath,
                        outFile.absolutePath,
                        startSec * 1_000_000L,
                        endSec * 1_000_000L
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
        val audioUrl: String,
        val resolution: String,
        val title: String
    )

    private fun extractStreams(url: String, maxHeight: Int): ExtractedStreams {
        val info = StreamInfo.getInfo(NewPipe.getService(0), url)

        val mp4Videos = info.videoOnlyStreams.filter {
            it.format?.mimeType?.contains("mp4", ignoreCase = true) == true &&
                    (it.codec?.contains("avc", ignoreCase = true) == true || it.codec == null)
        }
        val bestVideo = mp4Videos
            .filter { it.height <= maxHeight }
            .maxWithOrNull(compareBy({ it.height }, { it.fps }, { it.bitrate }))
            ?: mp4Videos.maxByOrNull { it.height }
            ?: info.videoStreams.maxByOrNull { it.height }
            ?: throw IllegalStateException("Не найден подходящий MP4 видеопоток")

        val bestAudio = info.audioStreams
            .filter { it.format?.mimeType?.contains("mp4", ignoreCase = true) == true || it.format?.suffix == "m4a" }
            .maxByOrNull { it.averageBitrate }
            ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("Не найден аудиопоток")

        return ExtractedStreams(
            videoUrl = bestVideo.content,
            audioUrl = bestAudio.content,
            resolution = "${bestVideo.height}p",
            title = info.name ?: "YT_Video"
        )
    }

    private fun downloadFile(fileUrl: String, dest: File) {
        val request = Request.Builder()
            .url(fileUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
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

    private fun trimAndMux(videoPath: String, audioPath: String, outPath: String, startUs: Long, endUs: Long) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioPath) }
        val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            val vTrackSrc = selectTrack(videoExtractor, "video/")
            val aTrackSrc = selectTrack(audioExtractor, "audio/")

            videoExtractor.selectTrack(vTrackSrc)
            audioExtractor.selectTrack(aTrackSrc)

            val vFormat = videoExtractor.getTrackFormat(vTrackSrc)
            val aFormat = audioExtractor.getTrackFormat(aTrackSrc)

            val vTrackDst = muxer.addTrack(vFormat)
            val aTrackDst = muxer.addTrack(aFormat)
            muxer.start()

            videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = videoExtractor.sampleTime.coerceAtLeast(0L)
            audioExtractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = videoExtractor.sampleTime
                if (sampleTime > endUs) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = (sampleTime - actualStartUs).coerceAtLeast(0L)
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(vTrackDst, buffer, bufferInfo)
                videoExtractor.advance()
            }

            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = audioExtractor.sampleTime
                if (sampleTime > endUs) break

                if (sampleTime >= actualStartUs) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = sampleTime - actualStartUs
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(aTrackDst, buffer, bufferInfo)
                }
                audioExtractor.advance()
            }
        } finally {
            try { muxer.stop() } catch (_: Throwable) {}
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(mimePrefix)) return i
        }
        throw IllegalStateException("Дорожка $mimePrefix не найдена")
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

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val reqBuilder = Request.Builder().url(request.url())
            request.headers().forEach { (k, values) ->
                values.forEach { v -> reqBuilder.addHeader(k, v) }
            }
            if (request.httpMethod().equals("POST", ignoreCase = true)) {
                val bodyBytes = request.dataToSend() ?: ByteArray(0)
                reqBuilder.post(bodyBytes.toRequestBody(null))
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val respBody = resp.body?.string() ?: ""
                val headersMap = mutableMapOf<String, List<String>>()
                resp.headers.names().forEach { name ->
                    headersMap[name] = resp.headers.values(name)
                }
                return Response(resp.code, resp.message, headersMap, respBody, resp.request.url.toString())
            }
        }
    }
}
