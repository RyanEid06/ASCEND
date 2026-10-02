package app.ascend.mobile.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaRegistryTest {
    @Test fun supportContractMatchesModeAndCalibrationExactly() {
        val registry = RepresentativeFormulaRegistry.registry
        assertTrue(registry.supports(FormulaIds.FACIAL_ELONGATION, "AUTOMATIC", false))
        assertFalse(registry.supports(FormulaIds.FACIAL_ELONGATION, "ASSISTED", false))
        assertFalse(registry.supports(FormulaIds.FACIAL_ELONGATION, "AUTOMATIC", true))
        assertFalse(registry.supports("missing", "AUTOMATIC", false))
    }

    @Test fun unknownFormulaReturnsExplicitUnavailableResult() {
        val result = RepresentativeFormulaRegistry.registry.measure("metric", "missing", SyntheticGeometryFixtures.front())
        result as GeometryMeasurementResult.Unavailable
        assertEquals(GeometryFailureCode.UNSUPPORTED_FORMULA, result.failureCode)
    }

    @Test fun duplicateFormulaIdsAreRejected() {
        val formula = RepresentativeFormulaRegistry.formulas.first()
        assertThrows(IllegalArgumentException::class.java) { GeometryFormulaRegistry(listOf(formula, formula)) }
    }
}
