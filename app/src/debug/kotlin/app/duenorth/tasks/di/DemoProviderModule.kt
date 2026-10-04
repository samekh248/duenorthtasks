package app.duenorth.tasks.di

import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.demo.DemoSeeder
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

/** Debug builds offer an in-memory demo account (T035) backed by the fake provider. */
@Module
@InstallIn(SingletonComponent::class)
object DemoProviderModule {
    @Provides
    @Singleton
    @IntoMap
    @ProviderKindKey(ProviderKind.FAKE)
    fun fakeProvider(): TaskProvider = FakeProvider()

    @Provides
    fun demoAccount(accounts: AccountRepository, tasks: TaskRepository, clock: Clock): DemoAccount =
        DemoSeeder(accounts, tasks, clock)
}
