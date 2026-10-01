package app.ascend.mobile.foundation.flags

import kotlinx.coroutines.flow.StateFlow

interface FeatureFlagRepository {
    val flags: StateFlow<Map<FeatureFlag, Boolean>>

    fun setOverride(flag: FeatureFlag, enabled: Boolean?)

    fun clearOverrides()
}
