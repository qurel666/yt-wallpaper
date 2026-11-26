package com.tv.ytwallpaper

import java.net.URLDecoder
import java.nio.charset.Charset

object CompatHelper {
    @JvmStatic
    fun urlDecode(s: String, charset: Charset): String {
        return URLDecoder.decode(s, charset.name())
    }
}
