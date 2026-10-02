package app.ascend.mobile.core.geometry

import app.ascend.mobile.core.model.MeasurementView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateFeasibilityMatrixTest {
    @Test fun matrixCoversExactlyTheFrozenThirtyFourCandidates() {
        val entries = CandidateFeasibilityMatrix.entries
        assertEquals(34, entries.size)
        assertEquals(22, entries.count { it.view == MeasurementView.FRONT })
        assertEquals(12, entries.count { it.view == MeasurementView.PROFILE })
        assertEquals(entries.size, entries.map { it.metricId }.toSet().size)
    }

    @Test fun implementedCandidatesResolveToRegisteredFormulas() {
        val registry = RepresentativeFormulaRegistry.registry
        CandidateFeasibilityMatrix.entries.filter { it.formulaOrExtractorId != null }.forEach { candidate ->
            assertTrue(registry.supportedFormulaIds.contains(candidate.formulaOrExtractorId))
            assertEquals(ReliabilityStatus.SYNTHETIC_VERIFIED, candidate.reliabilityStatus)
            assertNull(candidate.repeatedCaptureTolerance)
        }
    }

    @Test fun noCandidatePretendsToHaveRealWorldValidationYet() {
        assertTrue(CandidateFeasibilityMatrix.entries.none { it.reliabilityStatus == ReliabilityStatus.VALIDATED })
    }
}
