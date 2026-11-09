import service.UninstallManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UninstallManagerTest {

    @Test
    fun `upgrade code is valid GUID`() {
        assertEquals("89ABC678-DEF0-1234-5678-ABCDEF123457", UninstallManager.UPGRADE_CODE)
    }

    @Test
    fun `estimated user data size returns non negative`() {
        val size = UninstallManager.getEstimatedUserDataSizeMb()
        assertTrue(size >= 0L)
    }

    @Test
    fun `findInstalledProductCode does not crash when app is not installed`() {
        val code = UninstallManager.findInstalledProductCode()
        // Either null (in dev test environment) or a valid GUID format
        if (code != null) {
            assertTrue(code.startsWith("{") && code.endsWith("}"))
        }
    }
}
