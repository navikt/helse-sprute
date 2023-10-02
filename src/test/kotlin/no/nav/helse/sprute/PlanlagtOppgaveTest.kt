package no.nav.helse.sprute

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    fun `hvert 15 minutt`() {
        val idag = LocalDate.now()
        val nå = idag.atTime(13, 14, 20)
        val oppgave = PlanlagtOppgave.hvert15MinuttTime(1, nå) { _, _ -> oppgaveKjørt = true }
        val forventetNesteKøring = idag.atTime(13, 15, 0)
        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusMinutes(15))
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

    @Test
    fun `hvert minutt`() {
        val idag = LocalDate.now()
        val nå = idag.atTime(19, 59, 59)
        val oppgave = PlanlagtOppgave.hvertMinutt(1, nå) { _, _ -> oppgaveKjørt = true }
        val forventetNesteKøring = idag.atTime(20, 0, 0)

        testOppgave(oppgave, nå, forventetNesteKøring, forventetNesteKøring.plusMinutes(1))
    }

    @Test
    fun baz() {
        val idag = LocalDate.now()
        val nå = LocalTime.of(12, 33, 5).atDate(idag)
        val forrigeKøring = LocalTime.of(12, 32, 0).atDate(idag)
        val nesteKjøring = LocalTime.of(12, 33, 3).atDate(idag)

        val oppgave = PlanlagtOppgave(1, forrigeKøring, nesteKjøring, Oppgave { _, _ ->
            oppgaveKjørt = true
        }, Ruteplan.HalveTimer)

        val oppgave2 = oppgave.kjørOppgave(nå)
        assertTrue(oppgaveKjørt)
        assertNotSame(oppgave, oppgave2)

        oppgaveKjørt = false
        oppgave2.kjørOppgave(nå)
        assertFalse(oppgaveKjørt)
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