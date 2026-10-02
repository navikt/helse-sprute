plugins {
    alias(libs.plugins.sykepenger.deployable)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.sprute.AppKt"
}

dependencies {
    implementation(libs.rapidsAndRivers)
    implementation(libs.postgresql)
    implementation(libs.kotliquery)
    implementation(libs.hikariCP)
    implementation(libs.flyway.postgresql)
}
