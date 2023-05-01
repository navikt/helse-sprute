package no.nav.helse.sprute

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.*
import org.flywaydb.core.Flyway
import org.intellij.lang.annotations.Language
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

fun main() {
    val env = System.getenv()
    App(env).run()
}

val oppgaver = listOf(
    PersistertOppgave(1, { nå, nesteKjøring, context, logger ->
        val melding = datobegivenhet(nå, "hel_time", nesteKjøring)
        logger.info("hele timer kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.HeleTimer),
    PersistertOppgave(2, { nå, nesteKjøring, context, logger ->
        val melding = datobegivenhet(nå, "halv_time", nesteKjøring)
        logger.info("halve timer kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.HalveTimer),
    PersistertOppgave(3, { nå, nesteKjøring, context, logger ->
        val melding = datobegivenhet(nå, "midnatt", nesteKjøring)
        logger.info("midnatt kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.Midnatt),
    PersistertOppgave(4, { nå, nesteKjøring, context, logger ->
        val melding = datobegivenhet(nå, "minutt", nesteKjøring)
        logger.info("minutt kjører, sender:\n$melding")
        context.publish(melding)
    }, Ruteplan.HeleMinutt)
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

private class App(private val env: Map<String, String>) : RapidsConnection.StatusListener {
    private val logger = Logg.ny(this::class)

    private val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", env.getValue("DATABASE_HOST"), env.getValue("DATABASE_PORT"), env.getValue("DATABASE_DATABASE"))
        username = env.getValue("DATABASE_USERNAME")
        password = env.getValue("DATABASE_PASSWORD")
        maximumPoolSize = 3
        initializationFailTimeout = Duration.ofMinutes(30).toMillis()
    }

    private val dataSource by lazy { HikariDataSource(hikariConfig) }

    private var forfallJob: Job? = null
    private val rapidsConnection = RapidApplication.create(env)

    init {
        rapidsConnection.register(this)
    }

    fun run() {
        rapidsConnection.start()
    }

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
        forfallJob = GlobalScope.launch { kjørOppgaverTilForfall(rapidsConnection) }
    }

    override fun onShutdownSignal(rapidsConnection: RapidsConnection) {
        logger.info("stopper oppgave-jobben")
        forfallJob?.cancel()
    }

    private suspend fun CoroutineScope.kjørOppgaverTilForfall(rapidsConnection: RapidsConnection) {
        @Language("PostgreSQL")
        val statement = "SELECT * FROM oppgave FOR UPDATE;"
        while (isActive) {
            logger.info("pulserer, henter oppgaver og sjekker om de trenger å kjøre")
            val nå = LocalDateTime.now()
            sessionOf(dataSource).use { session ->
                session.transaction { txSession ->
                    txSession.run(queryOf(statement).asExecute)
                    oppgaver
                        .map { it.tilPlanlagtOppgave(txSession, rapidsConnection, logger) }
                        .map { it.kjørOppgave(nå) }
                        .map { it.memento() }
                        .forEach { it.tilDatabase(txSession) }
                }
            }

            logger.info("pulsering ferdig")
            delay(Duration.ofSeconds(5))
        }
    }
}


fun interface OppgaveMedContext {
    fun utfør(nå: LocalDateTime, nesteKjøring: LocalDateTime, messageContext: MessageContext, logger: Logg)
}
class PersistertOppgave(
    private val id: Int,
    private val oppgave: OppgaveMedContext,
    private val ruteplan: Ruteplan
) {

    fun opprett(session: Session, nå: LocalDateTime) {
        @Language("PostgreSQL")
        val statement = "INSERT INTO oppgave (id, neste_kjoring) VALUES (?, ?) ON CONFLICT DO NOTHING;"
        session.run(queryOf(statement, id, ruteplan.nesteKjøring(nå, null)).asUpdate)
    }

    fun tilPlanlagtOppgave(session: Session, messageContext: MessageContext, logger: Logg): PlanlagtOppgave {
        @Language("PostgreSQL")
        val statement = "SELECT forrige_kjoring, neste_kjoring FROM oppgave WHERE id=?"
        val (forrige, neste) = session.run(queryOf(statement, id).map {
            it.localDateTimeOrNull("forrige_kjoring") to it.localDateTime("neste_kjoring")
        }.asList).single()

        return PlanlagtOppgave(id, forrige, neste, { nå, nesteKjøring ->
            oppgave.utfør(nå, nesteKjøring, messageContext, logger)
        }, ruteplan)
    }
}

class OppgaveMemento(private val id: Int, private val forrigeKjøring: LocalDateTime?, private val nesteKjøring: LocalDateTime) {
    fun tilDatabase(session: Session) {
        @Language("PostgreSQL")
        val statement = "UPDATE oppgave SET forrige_kjoring=?, neste_kjoring=? WHERE id=?"
        session.run(queryOf(statement, forrigeKjøring, nesteKjøring, id).asUpdate)
    }
}