package no.nav.helse.sprute

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.*
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

fun main() {
    val env = System.getenv()
    App(env).run()
}

val oppgaver = listOf(
    PersistertOppgave(1, { nå, nesteKjøring, context ->
        val melding = datobegivenhet(nå, "hel_time", nesteKjøring)
        Logg.ny(Oppgave::class).info("hele timer kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.HeleTimer),
    PersistertOppgave(2, { nå, nesteKjøring, context ->
        val melding = datobegivenhet(nå, "halv_time", nesteKjøring)
        Logg.ny(Oppgave::class).info("halve timer kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.HalveTimer),
    PersistertOppgave(3, { nå, nesteKjøring, context ->
        val melding = datobegivenhet(nå, "midnatt", nesteKjøring)
        Logg.ny(Oppgave::class).info("midnatt kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.Midnatt)
)

private fun datobegivenhet(nå: LocalDateTime, navn: String, nesteKjøring: LocalDateTime) = JsonMessage.newMessage(navn, mapOf(
    "time" to nå.hour,
    "minutt" to nå.minute,
    "klokkeslett" to nå.toLocalTime(),
    "dagen" to nå.toLocalDate(),
    "ukedag" to nå.dayOfWeek,
    "dagIMåned" to nå.dayOfMonth,
    "måned" to nå.monthValue,
    "nesteKjøring" to nesteKjøring
)).toJson()

private class App(private val env: Map<String, String>) {
    private val logger = Logg.ny(this::class)

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", env.getValue("DATABASE_HOST"), env.getValue("DATABASE_PORT"), env.getValue("DATABASE_DATABASE"))
        username = env.getValue("DATABASE_USERNAME")
        password = env.getValue("DATABASE_PASSWORD")
        maximumPoolSize = 3
        initializationFailTimeout = Duration.ofMinutes(30).toMillis()
    }

    private val dataSource by lazy { HikariDataSource(hikariConfig) }

    private val rapidsConnection = RapidApplication.create(env).apply {
        River(this).register(Puls(::dataSource))
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                logger.info("Migrerer database")
                Flyway.configure()
                    .dataSource(dataSource)
                    .lockRetryCount(-1)
                    .load()
                    .migrate()
                logger.info("Migrering ferdig!")
                // todo: slett oppgaver i db som ikke finnes i kode
                sessionOf(dataSource).use { session ->
                    oppgaver.forEach { it.opprett(session, LocalDateTime.now()) }
                }
                logger.info("oppgaver syncet mot db, klar til pulsering")
            }
        })
    }

    fun run() {
        rapidsConnection.start()
    }
}

private class Puls(private val dataSource: () -> DataSource) : River.PacketListener {
    private val logg = Logg.ny(this::class)
    private var forrigePuls = LocalDateTime.MIN
    private val pulsering = Duration.ofSeconds(30)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val nå = LocalDateTime.now()
        if (nå.minus(pulsering) < forrigePuls) return
        forrigePuls = nå
        logg.info("pulserer, henter oppgaver og sjekker om de trenger å kjøre")

        sessionOf(dataSource()).use { session ->
            oppgaver
                .map { it.tilPlanlagtOppgave(session) }
                .map { it.kjørOppgave(nå, context) }
                .map { it.memento() }
                .forEach { it.tilDatabase(session) }
        }

        logg.info("pulsering ferdig")
    }
}


class PersistertOppgave(
    private val id: Int,
    private val oppgave: Oppgave,
    private val ruteplan: Ruteplan
) {

    fun opprett(session: Session, nå: LocalDateTime) {
        @Language("PostgreSQL")
        val statement = "INSERT INTO oppgave (id, neste_kjoring) VALUES (?, ?) ON CONFLICT DO NOTHING;"
        session.run(queryOf(statement, id, ruteplan.nesteKjøring(nå, null)).asUpdate)
    }

    fun tilPlanlagtOppgave(session: Session): PlanlagtOppgave {
        @Language("PostgreSQL")
        val statement = "SELECT forrige_kjoring, neste_kjoring FROM oppgave WHERE id=?"
        val (forrige, neste) = session.run(queryOf(statement, id).map {
            it.localDateTimeOrNull("forrige_kjoring") to it.localDateTime("neste_kjoring")
        }.asList).single()

        return PlanlagtOppgave(id, forrige, neste, oppgave, ruteplan)
    }
}

class OppgaveMemento(private val id: Int, private val forrigeKjøring: LocalDateTime?, private val nesteKjøring: LocalDateTime) {
    fun tilDatabase(session: Session) {
        @Language("PostgreSQL")
        val statement = "UPDATE oppgave SET forrige_kjoring=?, neste_kjoring=? WHERE id=?"
        session.run(queryOf(statement, forrigeKjøring, nesteKjøring, id).asUpdate)
    }
}