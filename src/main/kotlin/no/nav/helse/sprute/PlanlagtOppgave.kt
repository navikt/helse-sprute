package no.nav.helse.sprute

import no.nav.helse.rapids_rivers.MessageContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun interface Ruteplan {
    companion object {
        val HeleTimer = Ruteplan { nå, forrige ->
            (forrige ?: nå).plusHours(1).truncatedTo(ChronoUnit.HOURS)
        }
        val HalveTimer = Ruteplan { nå, forrige ->
            (forrige ?: nå).truncatedTo(ChronoUnit.MINUTES).let {
                it.plusMinutes(30 - (it.minute + 30) % 30L)
            }
        }
        val Midnatt = Ruteplan { nå, forrige ->
            (forrige ?: nå).plusDays(1).truncatedTo(ChronoUnit.DAYS)
        }
    }
    fun nesteKjøring(nå: LocalDateTime, forrigeKjøring: LocalDateTime?): LocalDateTime
}

fun interface Oppgave {
    fun utfør(nå: LocalDateTime, nesteKjøring: LocalDateTime, messageContext: MessageContext)
}

class PlanlagtOppgave(
    private val id: Int,
    private val forrigeKjøring: LocalDateTime?,
    private val nesteKjøring: LocalDateTime,
    private val oppgave: Oppgave,
    private val ruteplan: Ruteplan
) {
    constructor(id: Int, nå: LocalDateTime, oppgave: Oppgave, ruteplan: Ruteplan) : this(id, null, ruteplan.nesteKjøring(nå, null), oppgave, ruteplan)

    fun nesteKjøring(nå: LocalDateTime) = ruteplan.nesteKjøring(nå, forrigeKjøring)

    fun kjørOppgave(nå: LocalDateTime, messageContext: MessageContext): PlanlagtOppgave {
        if (nesteKjøring > nå) return this
        val nyNesteKjøring = nesteKjøring(nå)
        oppgave.utfør(nå, nyNesteKjøring, messageContext)
        return PlanlagtOppgave(id, nå, nyNesteKjøring, oppgave, ruteplan)
    }

    fun memento() = OppgaveMemento(id, forrigeKjøring, nesteKjøring)

    companion object {
        fun ny(id: Int, nå: LocalDateTime, oppgave: Oppgave, ruteplan: Ruteplan) = PlanlagtOppgave(id, nå, oppgave, ruteplan)

        fun hverHeleTime(id: Int, nå: LocalDateTime, oppgave: Oppgave) = ny(id, nå, oppgave, Ruteplan.HeleTimer)
        fun hverHalveTime(id: Int, nå: LocalDateTime, oppgave: Oppgave) = ny(id, nå, oppgave, Ruteplan.HalveTimer)
        fun hverMidnatt(id: Int, nå: LocalDateTime, oppgave: Oppgave) = ny(id, nå, oppgave, Ruteplan.Midnatt)
    }

}