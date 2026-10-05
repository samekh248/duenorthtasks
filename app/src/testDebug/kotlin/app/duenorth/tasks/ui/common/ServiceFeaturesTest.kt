package app.duenorth.tasks.ui.common

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.duenorth.tasks.data.db.DueNorthDatabase
import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.provider.api.ProviderCapabilities
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.provider.fake.FakeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T054: the star follows what the connected service can store; Google Tasks hides it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ServiceFeaturesTest {
    private lateinit var db: DueNorthDatabase
    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, DueNorthDatabase::class.java).allowMainThreadQueries().build()
        accounts = AccountRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun noAccountHasNoStar() = runBlocking {
        assertFalse(features(importance = true).importance.first())
    }

    @Test
    fun serviceWithImportanceShowsTheStar() = runBlocking {
        accounts.connect(ProviderKind.FAKE, "demo", null)
        assertTrue(features(importance = true).importance.first())
    }

    @Test
    fun serviceWithoutImportanceHidesTheStar() = runBlocking {
        accounts.connect(ProviderKind.FAKE, "demo", null)
        assertFalse(features(importance = false).importance.first())
    }

    private fun features(importance: Boolean) = ServiceFeatures(accounts) {
        FakeProvider(capabilities = ProviderCapabilities(importance = importance, manualOrder = true, dueTime = false))
    }
}
