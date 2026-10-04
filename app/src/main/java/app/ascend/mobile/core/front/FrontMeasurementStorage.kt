package app.ascend.mobile.core.front

import app.ascend.mobile.core.model.ScanOwner
import app.ascend.mobile.core.model.AnalysisOutcome

/** Additive WP07/WP08 seam; implementations use the existing encrypted, owner-scoped local store. */
interface FrontMeasurementStorage {
    suspend fun frontSourceRevision(owner: ScanOwner, scanId: String): String
    suspend fun installFrontInput(owner: ScanOwner, scanId: String, input: FrontInput, atEpochMillis: Long)
    suspend fun readFrontRevision(owner: ScanOwner, scanId: String): FrontRevision?
    suspend fun correctFront(owner: ScanOwner, scanId: String, expectedRevision: Long, policy: CorrectionPolicy,
        landmarkId: String, x: Double, y: Double, atEpochMillis: Long): FrontRevision
    suspend fun completeFrontAnalysis(owner: ScanOwner, outcome: AnalysisOutcome.Complete,
        expectedRevision: Long, policy: FrontPolicy, atEpochMillis: Long)
    suspend fun readCompletedFrontProvenance(owner: ScanOwner, scanId: String): FrontCompletedProvenance?
}
