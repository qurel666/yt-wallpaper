package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable

enum class WallpaperType(val value: Int) {
    IMAGE(0),
    VIDEO(1),
    ANIMATED_DRAWABLE(2);

    companion object {
        fun fromInt(v: Int): WallpaperType = values().firstOrNull { it.value == v } ?: VIDEO
    }
}

enum class WallpaperDisplayMode(val value: Int) {
    DEFAULT(0),
    CROP(1),
    STRETCH(2),
    BLUR(3);

    companion object {
        fun fromInt(v: Int): WallpaperDisplayMode = values().firstOrNull { it.value == v } ?: DEFAULT
    }
}

data class Wallpaper(
    val uri: String,
    val type: WallpaperType = WallpaperType.VIDEO,
    val displayMode: WallpaperDisplayMode = WallpaperDisplayMode.DEFAULT,
    val title: String? = null,
    val author: String? = null,
    val source: String? = null,
    val actionUri: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        uri = parcel.readString() ?: "",
        type = WallpaperType.fromInt(parcel.readInt()),
        displayMode = WallpaperDisplayMode.fromInt(parcel.readInt()),
        title = parcel.readString(),
        author = parcel.readString(),
        source = parcel.readString(),
        actionUri = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uri)
        parcel.writeInt(type.value)
        parcel.writeInt(displayMode.value)
        parcel.writeString(title)
        parcel.writeString(author)
        parcel.writeString(source)
        parcel.writeString(actionUri)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Wallpaper> {
        override fun createFromParcel(parcel: Parcel): Wallpaper = Wallpaper(parcel)
        override fun newArray(size: Int): Array<Wallpaper?> = arrayOfNulls(size)
    }
}
