package app.duenorth.tasks.demo

import app.duenorth.tasks.data.repo.AccountRepository
import app.duenorth.tasks.data.repo.TaskRepository
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.account.DemoAccount
import java.time.Clock
import java.time.LocalDate

/** Connects the demo account and fills it through the repositories, as a real first sync would. */
class DemoSeeder(private val accounts: AccountRepository, private val tasks: TaskRepository, private val clock: Clock) :
    DemoAccount {
    override suspend fun connect() {
        if (accounts.current() != null) return
        accounts.connect(ProviderKind.FAKE, displayName = "demo", email = null)
        val today = LocalDate.now(clock)
        val inbox = tasks.createList("Inbox", isDefault = true)
        val errands = tasks.createList("Errands")
        val home = tasks.createList("Home")
        val work = tasks.createList("Work")

        tasks.createTask(
            errands,
            "Return library books",
            notes = "Due back Tuesday. The two in the hall, plus the cookbook on the counter.",
            dueDate = today
        )
        tasks.createTask(home, "Pay water bill", notes = "https://example.com/pay", dueDate = today.minusDays(2))
        val vet = tasks.createTask(inbox, "Call the vet", notes = "Ask about the booster.", dueDate = today.plusDays(1))
        tasks.addStep(vet, "Find the vaccination card")
        tasks.addStep(vet, "Check Thursday afternoon")
        tasks.createTask(errands, "Pick up dry cleaning", dueDate = today.plusDays(1))
        val deck = tasks.createTask(
            work,
            "Draft the quarterly update",
            notes = "Numbers from the shared sheet.\nKeep it to one page.",
            dueDate = today.plusDays(4)
        )
        tasks.addStep(deck, "Collect numbers")
        tasks.addStep(deck, "Write the summary")
        tasks.createTask(work, "Book travel for the offsite")
        tasks.createTask(home, "Replace the hallway bulb")
        tasks.createTask(inbox, "Look up bread recipes")

        val done = tasks.createTask(home, "Water the plants", dueDate = today)
        tasks.setCompleted(done, true)
        val mailed = tasks.createTask(errands, "Mail the birthday card")
        tasks.setCompleted(mailed, true)
    }
}
