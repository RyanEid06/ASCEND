package app.ascend.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.ascend.mobile.BuildConfig
import app.ascend.mobile.ui.adaptive.AscendWindowWidthClass
import app.ascend.mobile.ui.capture.CaptureRoute
import app.ascend.mobile.ui.debug.DebugAccessPolicy
import app.ascend.mobile.ui.debug.DebugMenuScreen
import app.ascend.mobile.ui.debug.DebugMenuViewModel
import app.ascend.mobile.ui.debug.MeasurementDebugEntry
import app.ascend.mobile.ui.foundation.FoundationHomeScreen
import app.ascend.mobile.ui.foundation.FoundationInfoScreen
import app.ascend.mobile.ui.foundation.HomeViewModel

@Composable
fun AscendNavHost(windowWidthClass: AscendWindowWidthClass) {
    val backStack = rememberNavBackStack(FoundationHomeRoute)
    val debugAvailable = DebugAccessPolicy.isAvailable(BuildConfig.DEBUG)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<FoundationHomeRoute> {
                val viewModel: HomeViewModel = viewModel()
                val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                LaunchedEffect(backStack.size) {
                    viewModel.refreshLocalScans()
                }

                FoundationHomeScreen(
                    windowWidthClass = windowWidthClass,
                    uiState = uiState,
                    showDebugEntry = debugAvailable,
                    onStartScan = { backStack.add(CaptureFlowRoute) },
                    onOpenFrontLandmarks = { backStack.add(FrontLandmarkRoute(it)) },
                    onToggleDetails = viewModel::onToggleFoundationDetails,
                    onOpenFoundationInfo = { backStack.add(FoundationInfoRoute) },
                    onOpenDebugMenu = {
                        if (debugAvailable) {
                            backStack.add(DebugMenuRoute)
                        }
                    },
                )
            }

            entry<FoundationInfoRoute> {
                FoundationInfoScreen(
                    onBack = { backStack.removeLastOrNull() },
                )
            }

            entry<CaptureFlowRoute> {
                CaptureRoute(
                    windowWidthClass = windowWidthClass,
                    onExit = { backStack.removeLastOrNull() },
                    onCaptureSaved = { scanId ->
                        backStack.removeLastOrNull()
                        backStack.add(FrontLandmarkRoute(scanId))
                    },
                )
            }

            entry<FrontLandmarkRoute> { route ->
                app.ascend.mobile.ui.vision.FrontLandmarkRoute(route.scanId, onBack = { backStack.removeLastOrNull() })
            }

            if (debugAvailable) {
                entry<DebugMenuRoute> {
                    val viewModel: DebugMenuViewModel = viewModel()
                    val flags = viewModel.flags.collectAsStateWithLifecycle().value

                    DebugMenuScreen(
                        flags = flags,
                        onFlagChanged = viewModel::setFlagOverride,
                        onClearOverrides = viewModel::clearOverrides,
                        onOpenMeasurements = { backStack.add(MeasurementDebugRoute) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<MeasurementDebugRoute> {
                    MeasurementDebugEntry(onBack = { backStack.removeLastOrNull() })
                }
            }
        },
    )
}
