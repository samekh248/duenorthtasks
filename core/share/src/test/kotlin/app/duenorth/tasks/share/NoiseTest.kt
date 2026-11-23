package app.duenorth.tasks.share

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoiseTest {
    private val alice = Identity.generate()
    private val bob = Identity.generate()

    private fun handshake(i: NoiseXX, r: NoiseXX) {
        assertArrayEquals("one".toByteArray(), r.readMessage(i.writeMessage("one".toByteArray())))
        assertArrayEquals("two".toByteArray(), i.readMessage(r.writeMessage("two".toByteArray())))
        assertArrayEquals("three".toByteArray(), r.readMessage(i.writeMessage("three".toByteArray())))
    }

    @Test
    fun `both sides agree on keys, statics and the handshake hash`() {
        val i = NoiseXX(true, alice)
        val r = NoiseXX(false, bob)
        handshake(i, r)
        assertArrayEquals(bob.dhPublic, i.remoteStatic)
        assertArrayEquals(alice.dhPublic, r.remoteStatic)
        assertArrayEquals(i.handshakeHash, r.handshakeHash)
        val (iSend, iRecv) = i.split()
        val (rSend, rRecv) = r.split()
        repeat(3) {
            assertArrayEquals("ping $it".toByteArray(), rRecv.decrypt(iSend.encrypt("ping $it".toByteArray())))
            assertArrayEquals("pong $it".toByteArray(), iRecv.decrypt(rSend.encrypt("pong $it".toByteArray())))
        }
    }

    @Test
    fun `fresh ephemeral keys give a different hash every time`() {
        val a = NoiseXX(true, alice).also { i -> NoiseXX(false, bob).also { handshake(i, it) } }
        val b = NoiseXX(true, alice).also { i -> NoiseXX(false, bob).also { handshake(i, it) } }
        assertFalse(a.handshakeHash.contentEquals(b.handshakeHash))
    }

    @Test
    fun `an altered message is refused`() {
        val i = NoiseXX(true, alice)
        val r = NoiseXX(false, bob)
        r.readMessage(i.writeMessage())
        val m2 = r.writeMessage("secret".toByteArray())
        m2[40] = (m2[40] + 1).toByte()
        assertThrows<SecurityException> { i.readMessage(m2) }
    }

    @Test
    fun `transport refuses replays and tampering`() {
        val i = NoiseXX(true, alice)
        val r = NoiseXX(false, bob)
        handshake(i, r)
        val (send, _) = i.split()
        val (_, recv) = r.split()
        val c = send.encrypt("a".toByteArray())
        recv.decrypt(c)
        assertThrows<SecurityException> { recv.decrypt(c) }
    }

    @Test
    fun `a man in the middle ends up with different tiles`() {
        val mallory = Identity.generate()
        // Alice talks to Mallory, Mallory talks to Bob.
        val a = NoiseXX(true, alice)
        val m1 = NoiseXX(false, mallory)
        handshake(a, m1)
        val m2 = NoiseXX(true, mallory)
        val b = NoiseXX(false, bob)
        handshake(m2, b)
        assertNotEquals(SafetyTiles.of(a.handshakeHash), SafetyTiles.of(b.handshakeHash))
    }

    @Test
    fun `identity binding and signatures check out`() {
        assertTrue(Identity.verifyBinding(alice.signPublic, alice.dhPublic, alice.binding()))
        assertFalse(Identity.verifyBinding(alice.signPublic, bob.dhPublic, alice.binding()))
        val back = Identity.decode(alice.encode())
        assertEquals(alice.id, back.id)
        assertArrayEquals(alice.dhPublic, back.dhPublic)
    }

    @Test
    fun `tiles are four faces and stable for a hash`() {
        val h = ByteArray(32) { it.toByte() }
        assertEquals(4, SafetyTiles.of(h).size)
        assertEquals(SafetyTiles.of(h), SafetyTiles.of(h.copyOf()))
    }
}
