package app.ascend.mobile.core.front

import app.ascend.mobile.core.geometry.Point2
import app.ascend.mobile.core.geometry.rotate

/** Inverts exactly the geometry normalization, for drawing on the original oriented standardized image. */
fun FrontInput.overlayImagePoints(result: FrontMetricResult): Map<String, Point2> {
    if (result.sourcePoints.isEmpty()) return emptyMap()
    val roll = residualPose?.roll ?: return emptyMap()
    val shortEdge = minOf(width, height).toDouble()
    val origin = Point2(rollOrigin.x * width / shortEdge, rollOrigin.y * height / shortEdge)
    return result.sourcePoints.mapValues { (_, point) ->
        val image = rotate(point, roll, origin)
        Point2(image.x * shortEdge / width, image.y * shortEdge / height)
    }
}
