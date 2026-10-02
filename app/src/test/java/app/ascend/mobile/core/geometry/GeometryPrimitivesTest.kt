package app.ascend.mobile.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryPrimitivesTest {
    @Test fun angleAndRatioPrimitivesAreDeterministic() {
        assertEquals(90.0, angleDegrees(Point2(1.0, 0.0), Point2(0.0, 0.0), Point2(0.0, 1.0))!!, 1e-9)
        assertEquals(2.0, safeRatio(4.0, 2.0)!!, 0.0)
        assertNull(safeRatio(1.0, 0.0))
    }

    @Test fun outwardInclinationUsesSameSignOnBothSides() {
        val left = outwardInclinationDegrees(Point2(0.45, 0.40), Point2(0.35, 0.38))!!
        val right = outwardInclinationDegrees(Point2(0.55, 0.40), Point2(0.65, 0.38))!!
        assertEquals(left, right, 1e-9)
        assertEquals(11.309932474, left, 1e-6)
    }
}
