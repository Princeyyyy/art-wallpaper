import java.util.Properties

/**
 * Dynamic application and build metadata loaded from version.properties.
 * Single source of truth for runtime version, branding, and bundle identity.
 */
object AppInfo {
    const val APP_NAME = "Art Wallpaper"
    const val BUNDLE_ID = "com.prince.artwallpaper"
    const val DEFAULT_VERSION = "4.1.1"

    val version: String by lazy {
        try {
            val stream = AppInfo::class.java.getResourceAsStream("/version.properties")
            if (stream != null) {
                val props = Properties()
                stream.use { props.load(it) }
                props.getProperty("version", DEFAULT_VERSION)
            } else {
                DEFAULT_VERSION
            }
        } catch (_: Exception) {
            DEFAULT_VERSION
        }
    }
}
