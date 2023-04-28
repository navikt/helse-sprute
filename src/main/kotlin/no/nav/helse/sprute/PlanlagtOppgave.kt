package no.nav.helse.sprute

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun interface Ruteplan {
    companion object {
        val HeleTimer = Ruteplan { nå, forrige ->
            (forrige ?: nå).plusHours(1).truncatedTo(ChronoUnit.HOURS)
        }
        val Midnatt = Ruteplan { nå, forrige ->
            (forrige ?: nå).plusDays(1).truncatedTo(ChronoUnit.DAYS)
        }
    }
    fun nesteKjøring(nå: LocalDateTime, forrigeKjøring: LocalDateTime?): LocalDateTime
}

fun interface Oppgave {
    fun utfør()
}

class PlanlagtOppgave(
    private val forrigeKjøring: LocalDateTime?,
    private val nesteKjøring: LocalDateTime,
    private val oppgave: Oppgave,
    private val ruteplan: Ruteplan
) {
    constructor(nå: LocalDateTime, oppgave: Oppgave, ruteplan: Ruteplan) : this(null, ruteplan.nesteKjøring(nå, null), oppgave, ruteplan)

    fun nesteKjøring(nå: LocalDateTime) = ruteplan.nesteKjøring(nå, forrigeKjøring)

    fun kjørOppgave(nå: LocalDateTime): PlanlagtOppgave {
        if (nesteKjøring > nå) return this
        oppgave.utfør()
        return PlanlagtOppgave(nå, nesteKjøring(nå), oppgave, ruteplan)
    }

    companion object {
        fun ny(nå: LocalDateTime, oppgave: Oppgave, ruteplan: Ruteplan) = PlanlagtOppgave(nå, oppgave, ruteplan)

        fun hverHeleTime(nå: LocalDateTime, oppgave: Oppgave) = ny(nå, oppgave, Ruteplan.HeleTimer)
        fun hverMidnatt(nå: LocalDateTime, oppgave: Oppgave) = ny(nå, oppgave, Ruteplan.Midnatt)
    }

}