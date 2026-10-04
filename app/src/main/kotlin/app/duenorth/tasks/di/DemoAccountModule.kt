package app.duenorth.tasks.di

import app.duenorth.tasks.ui.account.DemoAccount
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds bind no demo account; debug builds add one in `DemoProviderModule`. */
@Module
@InstallIn(SingletonComponent::class)
interface DemoAccountModule {
    @BindsOptionalOf
    fun demoAccount(): DemoAccount
}
