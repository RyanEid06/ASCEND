package app.ascend.mobile.ui.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAccessPolicyTest {
    @Test
    fun `debug tooling is available only for debug builds`() {
        assertTrue(DebugAccessPolicy.isAvailable(isDebugBuild = true))
        assertFalse(DebugAccessPolicy.isAvailable(isDebugBuild = false))
    }
}
