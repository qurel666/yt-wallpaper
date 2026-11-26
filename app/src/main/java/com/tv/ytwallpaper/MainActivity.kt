package com.tv.ytwallpaper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
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

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var res1080Radio: RadioButton
    private lateinit var res1440Radio: RadioButton
    private lateinit var runButton: Button
    private lateinit var statusText: TextView
    private lateinit var historyContainer: LinearLayout

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
        refreshHistoryList()
    }

    private fun getWallpapersDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "ProjectivyWallpapers"
        ).apply { mkdirs() }
    }

    private fun buildTvUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121417"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 36)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "YouTube Видеообои для Projectivy (H.264 + Звук)"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 18)
        }
        root.addView(title)

        urlInput = createInput("Вставь ссылку на YouTube (https://youtu.be/...)")
        root.addView(urlInput)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }
        startInput = createInput("Начало (00:00)").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            }
        }
        endInput = createInput("Конец (00:30)").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        timeRow.addView(startInput)
        timeRow.addView(endInput)
        root.addView(timeRow)

        val radioGroup = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 16)
        }
        res1080Radio = RadioButton(this).apply {
            text = "1080p (Full HD, плавно на 2 ГБ ОЗУ)"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = true
            setPadding(0, 0, 32, 0)
        }
        res1440Radio = RadioButton(this).apply {
            text = "1440p (2K)"
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        radioGroup.addView(res1080Radio)
        radioGroup.addView(res1440Radio)
        root.addView(radioGroup)

        runButton = Button(this).apply {
            text = "СКАЧАТЬ, ОБРЕЗАТЬ И УСТАНОВИТЬ"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(36, 18, 36, 18)
            isFocusable = true
            applyFocusStyle(this, "#2563EB", "#3B82F6")
            setOnClickListener { startProcess() }
        }
        root.addView(runButton)

        statusText = TextView(this).apply {
            text = "Готово к работе. Папка: /Movies/ProjectivyWallpapers/"
            textSize = 14f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 18, 0, 20)
        }
        root.addView(statusText)

        // Разделитель и заголовок истории
        val historyTitle = TextView(this).apply {
            text = "Последние 5 скачанных видео (нажми пультом для установки):"
            textSize = 16f
            setTextColor(Color.parseColor("#E5E7EB"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
                bottomMargin = 10
            }
        }
        root.addView(historyTitle)

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(historyContainer)

        scroll.addView(root)
        setContentView(scroll)
        urlInput.requestFocus()
    }

    private fun applyFocusStyle(view: View, normalHex: String, focusedHex: String) {
        val normalBg = GradientDrawable().apply {
            setColor(Color.parseColor(normalHex))
            cornerRadius = 12f
            setStroke(2, Color.parseColor("#374151"))
        }
        val focusedBg = GradientDrawable().apply {
            setColor(Color.parseColor(focusedHex))
            cornerRadius = 12f
            setStroke(4, Color.parseColor("#60A5FA"))
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
            setPadding(22, 16, 22, 16)
            textSize = 15f
            isSingleLine = true
            isFocusable = true
            applyFocusStyle(this, "#1F2937", "#263244")
        }
    }

    private fun refreshHistoryList() {
        historyContainer.removeAllViews()
        val files = getWallpapersDir()
            .listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        // Оставляем только 5 самых свежих, остальные удаляем
        if (files.size > 5) {
            files.drop(5).forEach { try { it.delete() } catch (_: Throwable) {} }
        }

        val top5 = files.take(5)
        if (top5.isEmpty()) {
            val emptyLabel = TextView(this).apply {
                text = "Пока нет скачанных видео"
                textSize = 14f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(16, 12, 16, 12)
            }
            historyContainer.addView(emptyLabel)
            return
        }

        val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        top5.forEachIndexed { index, file ->
            val sizeMb = String.format(Locale.US, "%.1f МБ", file.length() / (1024.0 * 1024.0))
            val dateStr = dateFormat.format(Date(file.lastModified()))

            val itemBtn = TextView(this).apply {
                text = "${index + 1}.  ${file.nameWithoutExtension}   •   $sizeMb   •   $dateStr"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(24, 18, 24, 18)
                isFocusable = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                applyFocusStyle(this, "#1F2937", "#1D4ED8")
                setOnClickListener { showVideoActionDialog(file) }
            }
            historyContainer.addView(itemBtn)
        }
    }

    private fun showVideoActionDialog(file: File) {
        AlertDialog.Builder(this)
            .setTitle(file.nameWithoutExtension)
            .setMessage("Установить это видео как обои в Projectivy Launcher?")
            .setPositiveButton("Установить как обои") { _, _ ->
                applyToProjectivy(file)
            }
            .setNegativeButton("Отмена", null)
            .show()
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
                    .take(28)
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
                refreshHistoryList()
                updateStatus("Готово! Открываю установку обоев в Projectivy...")
                applyToProjectivy(outFile)
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

    private fun applyToProjectivy(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)

        // Если приложение открыли из самого Projectivy для выбора файла — возвращаем результат напрямую
        if (intent?.action == Intent.ACTION_GET_CONTENT || intent?.action == Intent.ACTION_PICK) {
            val resultIntent = Intent().apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            return
        }

        val projectivyIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, "video/mp4")
            setPackage("com.spocky.projengmenu")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(projectivyIntent)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(fallback, "Выбери Projectivy Launcher"))
            } catch (_: Throwable) {
                updateStatus("Файл сохранён: ${file.absolutePath}. Выбери его в настройках обоев Projectivy.")
            }
        }
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
