package app.ascend.mobile.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class AscendWindowWidthClassTest {
    @Test
    fun `compact width is below 600 dp`() {
        assertEquals(AscendWindowWidthClass.Compact, AscendWindowWidthClass.fromWidthDp(599f))
    }

    @Test
    fun `medium width begins at 600 dp`() {
        assertEquals(AscendWindowWidthClass.Medium, AscendWindowWidthClass.fromWidthDp(600f))
        assertEquals(AscendWindowWidthClass.Medium, AscendWindowWidthClass.fromWidthDp(839f))
    }

    @Test
    fun `expanded width begins at 840 dp`() {
        assertEquals(AscendWindowWidthClass.Expanded, AscendWindowWidthClass.fromWidthDp(840f))
    }
}
