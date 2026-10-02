package app.ascend.mobile.core.geometry

class GeometryFormulaRegistry(
    formulas: Collection<GeometryFormula>,
) {
    private val byFormulaId: Map<String, GeometryFormula>

    init {
        require(formulas.isNotEmpty()) { "At least one geometry formula is required" }
        require(formulas.all { it.formulaId.isNotBlank() })
        require(formulas.map { it.formulaId }.distinct().size == formulas.size) { "Duplicate formulaId" }
        byFormulaId = formulas.associateBy { it.formulaId }
    }

    val supportedFormulaIds: Set<String> get() = byFormulaId.keys

    fun formula(formulaId: String): GeometryFormula? = byFormulaId[formulaId]

    /** Deliberately mirrors WP04's integration contract without depending on the scoring package. */
    fun supports(formulaOrExtractorId: String, mode: String, calibrationRequired: Boolean): Boolean {
        val formula = byFormulaId[formulaOrExtractorId] ?: return false
        return formula.mode.name == mode && formula.calibrationRequired == calibrationRequired
    }

    fun measure(metricId: String, formulaId: String, frame: GeometryFrame): GeometryMeasurementResult {
        val formula = byFormulaId[formulaId]
            ?: return GeometryMeasurementResult.Unavailable(
                metricId = metricId,
                formulaId = formulaId,
                failureCode = GeometryFailureCode.UNSUPPORTED_FORMULA,
                detail = "Formula is not registered",
                sourceLandmarks = emptySet(),
            )
        return formula.evaluate(metricId, frame)
    }
}
