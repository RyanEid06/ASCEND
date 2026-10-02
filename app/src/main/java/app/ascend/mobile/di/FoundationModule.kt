package app.ascend.mobile.di

import app.ascend.mobile.foundation.flags.FeatureFlagRepository
import app.ascend.mobile.foundation.flags.InMemoryFeatureFlagRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FoundationModule {
    @Binds
    @Singleton
    abstract fun bindFeatureFlagRepository(
        implementation: InMemoryFeatureFlagRepository,
    ): FeatureFlagRepository
}
