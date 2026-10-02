package app.ascend.mobile.core.geometry

import app.ascend.mobile.core.model.MeasurementView

object SyntheticGeometryFixtures {
    fun front(
        confidence: Double = 1.0,
        pose: PoseDeviation = PoseDeviation(),
        resolution: PixelResolution = PixelResolution(1080, 1080),
        points: Map<LandmarkId, Point2> = mapOf(
            Landmarks.TRICHION to Point2(0.50, 0.10),
            Landmarks.MENTON to Point2(0.50, 0.90),
            Landmarks.ZYGION_LEFT to Point2(0.25, 0.45),
            Landmarks.ZYGION_RIGHT to Point2(0.75, 0.45),
            Landmarks.SUBNASALE to Point2(0.50, 0.55),
            Landmarks.STOMION to Point2(0.50, 0.65),
            Landmarks.GONION_LEFT to Point2(0.30, 0.75),
            Landmarks.GONION_RIGHT to Point2(0.70, 0.75),
            Landmarks.MEDIAL_CANTHUS_LEFT to Point2(0.45, 0.40),
            Landmarks.LATERAL_CANTHUS_LEFT to Point2(0.35, 0.38),
            Landmarks.MEDIAL_CANTHUS_RIGHT to Point2(0.55, 0.40),
            Landmarks.LATERAL_CANTHUS_RIGHT to Point2(0.65, 0.38),
        ),
    ) = GeometryFrame(
        view = MeasurementView.FRONT,
        landmarks = points.mapValues { LandmarkObservation(it.value, confidence) },
        pose = pose,
        resolution = resolution,
    )

    fun profile(
        confidence: Double = 1.0,
        pose: PoseDeviation = PoseDeviation(),
        resolution: PixelResolution = PixelResolution(1080, 1080),
    ) = GeometryFrame(
        view = MeasurementView.PROFILE,
        landmarks = mapOf(
            Landmarks.GLABELLA to LandmarkObservation(Point2(0.40, 0.25), confidence),
            Landmarks.NASION to LandmarkObservation(Point2(0.45, 0.35), confidence),
            Landmarks.SUPRATIP to LandmarkObservation(Point2(0.58, 0.43), confidence),
            Landmarks.COLUMELLA to LandmarkObservation(Point2(0.61, 0.52), confidence),
            Landmarks.SUBNASALE to LandmarkObservation(Point2(0.55, 0.55), confidence),
            Landmarks.LABRALE_SUPERIUS to LandmarkObservation(Point2(0.59, 0.62), confidence),
            Landmarks.LABRALE_INFERIUS to LandmarkObservation(Point2(0.60, 0.67), confidence),
            Landmarks.SUBLABIALE to LandmarkObservation(Point2(0.55, 0.72), confidence),
            Landmarks.POGONION to LandmarkObservation(Point2(0.59, 0.80), confidence),
            Landmarks.RAMUS_REFERENCE to LandmarkObservation(Point2(0.33, 0.55), confidence),
            Landmarks.GONION_LEFT to LandmarkObservation(Point2(0.38, 0.76), confidence),
            Landmarks.MANDIBULAR_BORDER_REFERENCE to LandmarkObservation(Point2(0.58, 0.84), confidence),
        ),
        pose = pose,
        resolution = resolution,
    )
}
