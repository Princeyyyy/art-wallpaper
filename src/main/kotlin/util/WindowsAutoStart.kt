/**
 * Cross-platform desktop startup integration singleton.
 * Automatically delegates to the appropriate Windows or macOS auto-start provider.
 */
object WindowsAutoStart {
    private val delegate: AutoStartService by lazy { AutoStartService.create() }

    fun enable() = delegate.enable()

    fun disable() = delegate.disable()

    fun isEnabled(): Boolean = delegate.isEnabled()
}
