package app.duenorth.tasks.share

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * The tapped phone's side of share by tap (spec 011 FR-802): Android routes APDUs for Due North's
 * AID here. Each one is handled on a worker thread (the database and crypto never run on the main
 * thread) and answered with [sendResponseApdu].
 */
@AndroidEntryPoint
class ShareCardService : HostApduService() {
    @Inject lateinit var controller: TapController

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "share-card") }
    private val card by lazy { ApduCard { controller.newCardSession() } }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        worker.execute { sendResponseApdu(card.process(commandApdu)) }
        return null
    }

    override fun onDeactivated(reason: Int) {
        worker.execute {
            card.deactivated()
            controller.cardParted()
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
