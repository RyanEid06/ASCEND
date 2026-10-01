package app.ascend.mobile.ui.debug

import androidx.lifecycle.ViewModel
import app.ascend.mobile.foundation.flags.FeatureFlag
import app.ascend.mobile.foundation.flags.FeatureFlagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val featureFlagRepository: FeatureFlagRepository,
) : ViewModel() {
    val flags: StateFlow<Map<FeatureFlag, Boolean>> = featureFlagRepository.flags

    fun setFlagOverride(flag: FeatureFlag, enabled: Boolean) {
        featureFlagRepository.setOverride(flag, enabled)
    }

    fun clearOverrides() {
        featureFlagRepository.clearOverrides()
    }
}
