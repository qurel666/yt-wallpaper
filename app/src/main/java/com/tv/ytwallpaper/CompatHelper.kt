package com.tv.ytwallpaper

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.function.Supplier

object CompatHelper {
    @JvmStatic
    fun urlDecode(s: String, charset: Charset): String = URLDecoder.decode(s, charset.name())

    @JvmStatic
    fun urlEncode(s: String, charset: Charset): String = URLEncoder.encode(s, charset.name())

    @JvmStatic
    fun readAllBytes(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(16384)
        var nRead: Int
        while (input.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return buffer.toByteArray()
    }

    @JvmStatic
    fun transferTo(input: InputStream, out: OutputStream): Long {
        var transferred = 0L
        val buffer = ByteArray(16384)
        var read: Int
        while (input.read(buffer, 0, buffer.size).also { read = it } >= 0) {
            out.write(buffer, 0, read)
            transferred += read
        }
        return transferred
    }

    @JvmStatic
    fun baosToString(baos: ByteArrayOutputStream, charset: Charset): String {
        return baos.toString(charset.name())
    }

    @JvmStatic
    fun <E> listCopyOf(coll: Collection<E>): List<E> {
        return Collections.unmodifiableList(ArrayList(coll))
    }

    @JvmStatic
    fun <E> setCopyOf(coll: Collection<E>): Set<E> {
        return Collections.unmodifiableSet(LinkedHashSet(coll))
    }

    @JvmStatic
    fun <K, V> mapCopyOf(map: Map<K, V>): Map<K, V> {
        return Collections.unmodifiableMap(LinkedHashMap(map))
    }

    @JvmStatic
    fun <T> requireNonNullElse(obj: T?, defaultObj: T): T {
        return obj ?: defaultObj
    }

    @JvmStatic
    fun <T> requireNonNullElseGet(obj: T?, supplier: Supplier<out T>): T {
        return obj ?: supplier.get()
    }

    @JvmStatic
    fun isBlank(s: String): Boolean {
        return s.trim().isEmpty()
    }

    @JvmStatic
    fun strip(s: String): String {
        return s.trim()
    }

    @JvmStatic
    fun stripLeading(s: String): String {
        return s.trimStart()
    }

    @JvmStatic
    fun stripTrailing(s: String): String {
        return s.trimEnd()
    }

    @JvmStatic
    fun repeat(s: String, count: Int): String {
        if (count <= 0) return ""
        return s.repeat(count)
    }
}
