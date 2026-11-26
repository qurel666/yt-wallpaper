package com.tv.ytwallpaper

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.os.Process
import androidx.core.content.FileProvider
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.io.File

class WallpaperProviderService : Service() {

    private val projectivyPackage = "com.spocky.projengmenu"

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            val uid = Binder.getCallingUid()
            if (uid != Process.myUid() &&
                packageManager.getPackagesForUid(uid)?.contains(projectivyPackage) != true
            ) {
                return emptyList()
            }

            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ProjectivyWallpapers"
            )
            if (!dir.exists()) return emptyList()

            val files = dir.listFiles { f -> f.isFile && f.extension.equals("mp4", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: return emptyList()

            if (files.isEmpty()) return emptyList()

            val prefs = getSharedPreferences("yt_wallpaper_prefs", Context.MODE_PRIVATE)
            val activeName = prefs.getString("active_file", null)

            val selectedFile = if (activeName != null && files.any { it.name == activeName }) {
                files.first { it.name == activeName }
            } else {
                files.first()
            }

            val uri = try {
                FileProvider.getUriForFile(
                    this@WallpaperProviderService,
                    "$packageName.provider",
                    selectedFile
                )
            } catch (_: Throwable) {
                return emptyList()
            }

            if (uid != Process.myUid()) {
                val identity = Binder.clearCallingIdentity()
                try {
                    grantUriPermission(
                        projectivyPackage,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } finally {
                    Binder.restoreCallingIdentity(identity)
                }
            }

            return listOf(
                Wallpaper(
                    uri = uri.toString(),
                    type = WallpaperType.VIDEO,
                    displayMode = WallpaperDisplayMode.DEFAULT,
                    title = selectedFile.nameWithoutExtension,
                    source = "YT Обои ТВ"
                )
            )
        }

        override fun getPreferences(): String = "{}"

        override fun setPreferences(params: String?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
