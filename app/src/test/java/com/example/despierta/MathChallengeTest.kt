package com.example.despierta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeTest {
    @Test
    fun onlyCorrectNumericAnswerIsAccepted() {
        val challenge = MathChallenge(18, 27)

        assertTrue(challenge.accepts("45"))
        assertFalse(challenge.accepts("44"))
        assertFalse(challenge.accepts("texto"))
    }
}
