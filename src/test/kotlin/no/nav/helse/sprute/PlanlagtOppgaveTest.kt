package no.nav.helse.sprute

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlanlagtOppgaveTest {
    private var oppgaveKjørt: Boolean = false
    @BeforeEach
    fun setup() {
        oppgaveKjørt = false
    }

    @Test
    fun `hver hele time`() {
        val idag = LocalDate.now()
        val nå = idag.atTime(13, 30, 20)
        val oppgave = PlanlagtOppgave.hverHeleTime(nå) { oppgaveKjørt = true }
        val forventetNesteKøring = idag.atTime(14, 0, 0)

        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusHours(1))
    }

    @Test
    fun `hver midnatt`() {
        val idag = LocalDate.now()
        val iMorgen = idag.plusDays(1)
        val nå = idag.atTime(23, 59, 59)
        val oppgave = PlanlagtOppgave.hverMidnatt(nå) { oppgaveKjørt = true }
        val forventetNesteKøring = iMorgen.atTime(0, 0, 0)

        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusDays(1))
    }

    private fun testOppgave(oppgave: PlanlagtOppgave, nå: LocalDateTime, forventetNeste: LocalDateTime, forventetNesteNeste: LocalDateTime) {
        assertEquals(forventetNeste, oppgave.nesteKjøring(nå))

        assertSame(oppgave, oppgave.kjørOppgave(nå))
        assertFalse(oppgaveKjørt)

        val nyOppgave = oppgave.kjørOppgave(forventetNeste)
        assertTrue(oppgaveKjørt)
        assertNotSame(oppgave, nyOppgave)
        assertEquals(forventetNesteNeste, nyOppgave.nesteKjøring(forventetNeste))
    }
}