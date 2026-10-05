package app.duenorth.tasks.bench

import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.demo.DemoSeeder
import app.duenorth.tasks.di.ProviderKindKey
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.api.TaskProvider
import app.duenorth.tasks.provider.fake.FakeProvider
import app.duenorth.tasks.ui.account.DemoAccount
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import java.time.Clock
import javax.inject.Singleton

/**
 * Benchmark builds sync against the in-memory fake provider, so no real account is needed, and
 * offer the demo account on the sign-in page like debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
object BenchmarkProviderModule {
    @Provides
    @Singleton
    @IntoMap
    @ProviderKindKey(ProviderKind.FAKE)
    fun fakeProvider(): TaskProvider = FakeProvider()

    @Provides
    fun demoAccount(accounts: AccountRepository, tasks: TaskRepository, clock: Clock): DemoAccount =
        DemoSeeder(accounts, tasks, clock)
}
