package app.duenorth.tasks.di

import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.provider.AccountProviderRegistry
import app.duenorth.tasks.data.provider.ProviderRegistry
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Provider
import javax.inject.Singleton

/** Keys a [TaskProvider] binding in the provider map by the service it talks to. */
@MapKey
annotation class ProviderKindKey(val value: ProviderKind)

/**
 * The only place the app sees concrete providers (constitution Principle III). Each provider
 * module contributes `@IntoMap @ProviderKindKey(...)` bindings here: Google and Microsoft arrive
 * with US2 and US3; the fake demo provider is bound in debug builds only.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {
    @Multibinds
    abstract fun providers(): Map<ProviderKind, TaskProvider>

    companion object {
        @Provides
        @Singleton
        fun providerRegistry(
            db: DueNorthDatabase,
            providers: Map<ProviderKind, @JvmSuppressWildcards Provider<TaskProvider>>
        ): ProviderRegistry = AccountProviderRegistry(
            accounts = db.accountDao(),
            providers = providers.mapValues { (_, provider) -> { provider.get() } }
        )
    }
}
