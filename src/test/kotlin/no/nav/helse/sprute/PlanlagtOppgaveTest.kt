package no.nav.helse.sprute

import no.nav.helse.rapids_rivers.MessageContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlanlagtOppgaveTest {
    private var oppgaveKjørt: Boolean = false
    private val messageContext = object : MessageContext {
        override fun publish(message: String) {}
        override fun publish(key: String, message: String) {}
        override fun rapidName(): String {
            return "test"
        }
    }
    @BeforeEach
    fun setup() {
        oppgaveKjørt = false
    }

    @Test
    fun `hver hele time`() {
        val idag = LocalDate.now()
        val nå = idag.atTime(13, 30, 20)
        val oppgave = PlanlagtOppgave.hverHeleTime(1, nå) { _, _ -> oppgaveKjørt = true }
        val forventetNesteKøring = idag.atTime(14, 0, 0)

        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusHours(1))
    }

    @Test
    fun `hver halve time`() {
        val idag = LocalDate.now()
        val nå = idag.atTime(13, 14, 20)
        val oppgave = PlanlagtOppgave.hverHalveTime(1, nå) { _, _ -> oppgaveKjørt = true }
        val forventetNesteKøring = idag.atTime(13, 30, 0)

        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusMinutes(30))
    }

    @Test
    fun `hver midnatt`() {
        val idag = LocalDate.now()
        val iMorgen = idag.plusDays(1)
        val nå = idag.atTime(23, 59, 59)
        val oppgave = PlanlagtOppgave.hverMidnatt(1, nå) { _, _ -> oppgaveKjørt = true }
        val forventetNesteKøring = iMorgen.atTime(0, 0, 0)

        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusDays(1))
    }

    private fun testOppgave(oppgave: PlanlagtOppgave, nå: LocalDateTime, forventetNeste: LocalDateTime, forventetNesteNeste: LocalDateTime) {
        assertEquals(forventetNeste, oppgave.nesteKjøring(nå))

        assertSame(oppgave, oppgave.kjørOppgave(nå, messageContext))
        assertFalse(oppgaveKjørt)

        val nyOppgave = oppgave.kjørOppgave(forventetNeste, messageContext)
        assertTrue(oppgaveKjørt)
        assertNotSame(oppgave, nyOppgave)
        assertEquals(forventetNesteNeste, nyOppgave.nesteKjøring(forventetNeste))
    }
}