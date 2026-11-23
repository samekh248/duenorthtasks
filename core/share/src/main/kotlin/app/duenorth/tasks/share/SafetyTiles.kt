package app.duenorth.tasks.share

/** The 16 tile faces. The app draws [glyph]; [word] is printed under it and read out by TalkBack. */
enum class TileFace(val word: String) {
    FERN("fern"),
    COMET("comet"),
    HARBOR("harbor"),
    DUSK("dusk"),
    MAPLE("maple"),
    RIVER("river"),
    EMBER("ember"),
    FROST("frost"),
    ORBIT("orbit"),
    PEBBLE("pebble"),
    AURORA("aurora"),
    CANYON("canyon"),
    MEADOW("meadow"),
    LANTERN("lantern"),
    THISTLE("thistle"),
    SUMMIT("summit")
}

/**
 * Four safety tiles from a finished handshake (FR-803): 16 bits of a hash of the Noise handshake
 * hash. A man in the middle ends up with two different handshakes, so the two phones show
 * different tiles unless he guesses right, 1 time in 65,536. He can't search for a match: the
 * initiator commits to its key and nonce in message 1 (see [TapSession]).
 */
object SafetyTiles {
    fun of(handshakeHash: ByteArray): List<TileFace> {
        val d = Crypto.sha256("due-north tiles".toByteArray(), handshakeHash)
        val faces = TileFace.entries
        return listOf(
            faces[(d[0].toInt() ushr 4) and 15],
            faces[d[0].toInt() and 15],
            faces[(d[1].toInt() ushr 4) and 15],
            faces[d[1].toInt() and 15]
        )
    }
}
