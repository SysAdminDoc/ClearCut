package android.net

import android.os.Parcel

/**
 * Second test-only Uri sibling to [FakeUri] for tests that need two
 * distinct sources — StreamCopyExportEngineTest uses this to exercise the
 * "multiple source files" disqualifier path.
 */
object SecondFakeUri : Uri() {
    override fun buildUpon(): Uri.Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = "clip2"
    override fun getPath(): String? = "/clip2"
    override fun getPathSegments(): List<String> = listOf("clip2")
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = "test"
    override fun getSchemeSpecificPart(): String? = "clip2"
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = false
    override fun toString(): String = "test://clip2"
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
