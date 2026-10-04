package app.duenorth.tasks.di

import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.fake.FakeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/** Debug builds offer an in-memory demo account (T035) backed by the fake provider. */
@Module
@InstallIn(SingletonComponent::class)
object DemoProviderModule {
    @Provides
    @Singleton
    @IntoMap
    @ProviderKindKey(ProviderKind.FAKE)
    fun fakeProvider(): TaskProvider = FakeProvider()
}
