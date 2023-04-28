package no.nav.helse.sprute

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

fun main() {
    val env = System.getenv()
    App(env).run()
}

private class App(private val env: Map<String, String>) {
    private val logger = Logg.ny(this::class)

    private val rapidsConnection = RapidApplication.create(env).apply {
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                // todo: migrate db
            }
        })
    }

    fun run() {
        rapidsConnection.start()
    }
}
