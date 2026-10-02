package app.ascend.mobile.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val EPSILON = 1e-9

data class Point2(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite())
    }
}

data class Vector2(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite())
    }

    val magnitude: Double get() = hypot(x, y)
    fun dot(other: Vector2): Double = x * other.x + y * other.y
    fun cross(other: Vector2): Double = x * other.y - y * other.x
}

fun vector(from: Point2, to: Point2): Vector2 = Vector2(to.x - from.x, to.y - from.y)

fun distance(a: Point2, b: Point2): Double = hypot(b.x - a.x, b.y - a.y)
fun horizontalDistance(a: Point2, b: Point2): Double = abs(b.x - a.x)
fun verticalDistance(a: Point2, b: Point2): Double = abs(b.y - a.y)

fun safeRatio(numerator: Double, denominator: Double): Double? {
    if (!numerator.isFinite() || !denominator.isFinite() || abs(denominator) <= EPSILON) return null
    return (numerator / denominator).takeIf(Double::isFinite)
}

/** Unsigned interior angle in [0, 180], or null when either ray is degenerate. */
fun angleDegrees(a: Point2, vertex: Point2, c: Point2): Double? {
    val first = vector(vertex, a)
    val second = vector(vertex, c)
    val denominator = first.magnitude * second.magnitude
    if (denominator <= EPSILON) return null
    val cosine = (first.dot(second) / denominator).coerceIn(-1.0, 1.0)
    return acos(cosine) * 180.0 / PI
}

/** Signed rotation from [from] to [to] in degrees, or null for a zero-length vector. */
fun signedAngleDegrees(from: Vector2, to: Vector2): Double? {
    if (from.magnitude <= EPSILON || to.magnitude <= EPSILON) return null
    return atan2(from.cross(to), from.dot(to)) * 180.0 / PI
}

/**
 * Inclination of an inner-to-outer feature relative to horizontal.
 * Image coordinates use y-down, so a physically higher outer point returns a positive angle.
 * abs(dx) makes the sign convention identical for left and right facial sides.
 */
fun outwardInclinationDegrees(inner: Point2, outer: Point2): Double? {
    val dx = abs(outer.x - inner.x)
    val dy = outer.y - inner.y
    if (dx <= EPSILON) return null
    return -atan2(dy, dx) * 180.0 / PI
}

fun centroid(points: Collection<Point2>): Point2? {
    if (points.isEmpty()) return null
    return Point2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
}

/** Rotate in mathematical degrees around [pivot]. Works consistently for normalized image coordinates. */
fun rotate(point: Point2, degrees: Double, pivot: Point2): Point2 {
    require(degrees.isFinite())
    val radians = degrees * PI / 180.0
    val cosTheta = cos(radians)
    val sinTheta = sin(radians)
    val translatedX = point.x - pivot.x
    val translatedY = point.y - pivot.y
    return Point2(
        x = translatedX * cosTheta - translatedY * sinTheta + pivot.x,
        y = translatedX * sinTheta + translatedY * cosTheta + pivot.y,
    )
}
