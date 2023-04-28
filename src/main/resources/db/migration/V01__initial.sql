create table if not exists oppgave
(
    id              int primary key,
    forrige_kjoring timestamp,
    neste_kjoring   timestamp not null
);