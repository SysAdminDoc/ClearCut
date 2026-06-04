package android.net

import android.os.Parcel

class TestUri(
    private val raw: String,
    private val schemeValue: String,
    private val segment: String,
) : Uri() {
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String? = "sender"
    override fun getEncodedAuthority(): String? = authority
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = "/$segment"
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = raw.substringAfter("://")
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = authority
    override fun getLastPathSegment(): String? = segment
    override fun getPath(): String? = "/$segment"
    override fun getPathSegments(): List<String> = listOf(segment)
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = schemeValue
    override fun getSchemeSpecificPart(): String? = raw.substringAfter("://")
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun toString(): String = raw
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
