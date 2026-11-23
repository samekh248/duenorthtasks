package app.duenorth.tasks.share

import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.RoomShareStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShareModule {
    @Provides
    @Singleton
    fun shareStore(db: DueNorthDatabase): RoomShareStore = RoomShareStore(db)
}
