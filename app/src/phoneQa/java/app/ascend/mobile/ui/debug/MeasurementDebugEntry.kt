package app.ascend.mobile.ui.debug

import androidx.compose.runtime.Composable

/**
 * phoneQa is a production-signed release-derived build used on physical ARM64 devices.
 * It must never expose the debug measurement inspector.
 */
@Composable
fun MeasurementDebugEntry(onBack: () -> Unit) = Unit
