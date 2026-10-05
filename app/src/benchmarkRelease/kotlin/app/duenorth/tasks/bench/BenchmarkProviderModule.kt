package app.duenorth.tasks.bench

import app.duenorth.tasks.di.ProviderKindKey
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.fake.FakeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/** Benchmark builds sync against the in-memory fake provider, so no real account is needed. */
@Module
@InstallIn(SingletonComponent::class)
object BenchmarkProviderModule {
    @Provides
    @Singleton
    @IntoMap
    @ProviderKindKey(ProviderKind.FAKE)
    fun fakeProvider(): TaskProvider = FakeProvider()
}
