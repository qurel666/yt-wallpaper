package com.tv.ytwallpaper

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import androidx.core.content.FileProvider
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.io.File

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): MutableList<Wallpaper> {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ProjectivyWallpapers"
            )
            if (!dir.exists()) return mutableListOf()

            val files = dir.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: return mutableListOf()

            if (files.isEmpty()) return mutableListOf()

            val prefs = getSharedPreferences("yt_wallpaper_prefs", Context.MODE_PRIVATE)
            val activeName = prefs.getString("active_file", null)

            // Если выбрано конкретное видео — ставим его первым, иначе берём самое свежее
            val ordered = if (activeName != null && files.any { it.name == activeName }) {
                val selected = files.first { it.name == activeName }
                listOf(selected)
            } else {
                listOf(files.first())
            }

            val result = mutableListOf<Wallpaper>()
            for (file in ordered) {
                try {
                    val uri = FileProvider.getUriForFile(
                        this@WallpaperProviderService,
                        "$packageName.provider",
                        file
                    )
                    grantUriPermission(
                        "com.spocky.projengmenu",
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    result.add(
                        Wallpaper(
                            uri = uri.toString(),
                            type = WallpaperType.VIDEO,
                            displayMode = WallpaperDisplayMode.CROP,
                            title = file.nameWithoutExtension,
                            source = "YT Обои ТВ"
                        )
                    )
                } catch (_: Throwable) {
                }
            }
            return result
        }

        override fun getPreferencesActivity(): String {
            return "$packageName/.MainActivity"
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
