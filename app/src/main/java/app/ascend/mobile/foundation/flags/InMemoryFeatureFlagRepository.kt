package app.ascend.mobile.foundation.flags

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class InMemoryFeatureFlagRepository @Inject constructor() : FeatureFlagRepository {
    private val overrides = mutableMapOf<FeatureFlag, Boolean>()
    private val _flags = MutableStateFlow(resolveFlags())

    override val flags: StateFlow<Map<FeatureFlag, Boolean>> = _flags.asStateFlow()

    override fun setOverride(flag: FeatureFlag, enabled: Boolean?) {
        if (enabled == null) {
            overrides.remove(flag)
        } else {
            overrides[flag] = enabled
        }
        _flags.value = resolveFlags()
    }

    override fun clearOverrides() {
        overrides.clear()
        _flags.value = resolveFlags()
    }

    private fun resolveFlags(): Map<FeatureFlag, Boolean> =
        FeatureFlag.entries.associateWith { flag ->
            overrides[flag] ?: flag.enabledByDefault
        }
}
