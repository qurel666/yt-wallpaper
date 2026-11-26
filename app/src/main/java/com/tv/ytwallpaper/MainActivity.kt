package com.tv.ytwallpaper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
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
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var startInput: EditText
    private lateinit var endInput: EditText
    private lateinit var res1080Radio: RadioButton
    private lateinit var res1440Radio: RadioButton
    private lateinit var runButton: Button
    private lateinit var statusText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NewPipe.init(OkHttpDownloader(httpClient), Localization("ru", "RU"))

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
        }

        buildTvUi()
    }

    private fun buildTvUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121417"))
            setPadding(64, 48, 64, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "YouTube Видеообои для Projectivy (H.264 + Звук)"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        urlInput = createInput("Ссылка на YouTube (например https://youtu.be/...)")
        root.addView(urlInput)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
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
            setPadding(0, 8, 0, 24)
        }
        res1080Radio = RadioButton(this).apply {
            text = "1080p (Full HD, плавно на 2 ГБ ОЗУ)"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = true
            setPadding(0, 0, 32, 0)
        }
        res1440Radio = RadioButton(this).apply {
            text = "1440p (2K)"
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        radioGroup.addView(res1080Radio)
        radioGroup.addView(res1440Radio)
        root.addView(radioGroup)

        runButton = Button(this).apply {
            text = "СКАЧАТЬ, ОБРЕЗАТЬ И УСТАНОВИТЬ"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#2563EB"))
            setTextColor(Color.WHITE)
            setPadding(32, 20, 32, 20)
            isFocusable = true
            setOnClickListener { startProcess() }
        }
        root.addView(runButton)

        statusText = TextView(this).apply {
            text = "Готово к работе. Файл сохраняется в /Movies/ProjectivyWallpapers/wallpaper.mp4"
            textSize = 15f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 28, 0, 0)
        }
        root.addView(statusText)

        setContentView(root)
        urlInput.requestFocus()
    }

    private fun createInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1F2937"))
            setPadding(24, 20, 24, 20)
            textSize = 16f
            isSingleLine = true
            isFocusable = true
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
                val (videoUrl, audioUrl, actualRes) = withContext(Dispatchers.IO) {
                    extractStreams(url, targetHeight)
                }

                updateStatus("2/4 Скачиваю видео ($actualRes H.264)...")
                withContext(Dispatchers.IO) { downloadFile(videoUrl, tempVideo) }

                updateStatus("3/4 Скачиваю звук (AAC)...")
                withContext(Dispatchers.IO) { downloadFile(audioUrl, tempAudio) }

                val outDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ProjectivyWallpapers"
                ).apply { mkdirs() }
                val outFile = File(outDir, "wallpaper.mp4")
                if (outFile.exists()) outFile.delete()

                updateStatus("4/4 Обрезаю (${startSec}с — ${endSec}с) и склеиваю со звуком...")
                withContext(Dispatchers.IO) {
                    trimAndMux(tempVideo.absolutePath, tempAudio.absolutePath, outFile.absolutePath, startSec * 1_000_000L, endSec * 1_000_000L)
                }

                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(outFile.absolutePath), arrayOf("video/mp4"), null)
                updateStatus("Готово! Применяю обои в Projectivy Launcher...")
                applyToProjectivy(outFile)
            } catch (e: Exception) {
                updateStatus("Ошибка: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                tempVideo.delete()
                tempAudio.delete()
                runButton.isEnabled = true
            }
        }
    }

    private fun extractStreams(url: String, maxHeight: Int): Triple<String, String, String> {
        val info = StreamInfo.getInfo(NewPipe.getService(0), url)

        // Выбираем строго MP4/H.264 (avc1) для аппаратного декодера ТВ
        val mp4Videos = info.videoOnlyStreams.filter {
            it.format?.mimeType?.contains("mp4", ignoreCase = true) == true &&
                    (it.codec?.contains("avc", ignoreCase = true) == true || it.codec == null)
        }
        val bestVideo = mp4Videos
            .filter { (it.height) <= maxHeight }
            .maxWithOrNull(compareBy({ it.height }, { it.fps }, { it.bitrate }))
            ?: mp4Videos.maxByOrNull { it.height }
            ?: info.videoStreams.maxByOrNull { it.height }
            ?: throw IllegalStateException("Не найден подходящий MP4 видеопоток")

        val bestAudio = info.audioStreams
            .filter { it.format?.mimeType?.contains("mp4", ignoreCase = true) == true || it.format?.suffix == "m4a" }
            .maxByOrNull { it.averageBitrate }
            ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("Не найден аудиопоток")

        return Triple(bestVideo.content, bestAudio.content, "${bestVideo.height}p")
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

            // Смещаемся к ближайшему ключевому кадру перед startUs
            videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = videoExtractor.sampleTime.coerceAtLeast(0L)
            audioExtractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            // Копируем видео
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

            // Копируем аудио с выравниванием по времени
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
            try { muxer.stop() } catch (_: Exception) {}
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
        val projectivyIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, "video/mp4")
            setPackage("com.spocky.projengmenu")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(projectivyIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(fallback, "Выбери Projectivy Launcher"))
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
