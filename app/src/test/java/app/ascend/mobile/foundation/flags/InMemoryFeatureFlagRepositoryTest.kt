package app.ascend.mobile.foundation.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryFeatureFlagRepositoryTest {
    @Test
    fun `flags start from declared defaults`() {
        val repository = InMemoryFeatureFlagRepository()

        FeatureFlag.entries.forEach { flag ->
            assertEquals(flag.enabledByDefault, repository.flags.value[flag])
        }
    }

    @Test
    fun `override and reset are deterministic`() {
        val repository = InMemoryFeatureFlagRepository()
        val flag = FeatureFlag.AdaptiveLayoutDiagnostics

        assertFalse(repository.flags.value.getValue(flag))
        repository.setOverride(flag, true)
        assertTrue(repository.flags.value.getValue(flag))
        repository.clearOverrides()
        assertFalse(repository.flags.value.getValue(flag))
    }
}
