-- ---------------------------------------------------------------------------
-- An application that sends OTPs through this service. Each has its own
-- settings and its own API keys; one app can never see another's codes.
-- ---------------------------------------------------------------------------
create table client_app (
    id                    uuid         primary key,
    name                  varchar(100) not null,
    otp_length            smallint     not null default 6  check (otp_length between 4 and 8),
    ttl_seconds           integer      not null default 300 check (ttl_seconds between 60 and 1800),
    max_attempts          smallint     not null default 5  check (max_attempts between 1 and 10),
    max_sends_per_hour    smallint     not null default 5  check (max_sends_per_hour between 1 and 100),
    resend_after_seconds  integer      not null default 30 check (resend_after_seconds between 0 and 600),
    -- SMS settings passed to the provider. India: the DLT header and template.
    sender_id             varchar(20)  null,
    template_id           varchar(64)  null,
    -- {code} and {minutes} are replaced. Must match the DLT template word for word.
    message_template      varchar(300) not null
        default 'Your verification code is {code}. It expires in {minutes} minutes. Do not share it.',
    active                boolean      not null default true,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now()
);

create unique index ux_client_app_name on client_app (lower(name));

-- ---------------------------------------------------------------------------
-- API keys. The secret half is stored only as a SHA-256 hash; the plain key is
-- shown once, when it is created. The prefix is public and is how a presented
-- key is found.
-- ---------------------------------------------------------------------------
create table api_key (
    id            uuid         primary key,
    app_id        uuid         not null references client_app (id),
    prefix        varchar(16)  not null,
    secret_hash   varchar(64)  not null,
    label         varchar(100) null,
    created_at    timestamptz  not null default now(),
    last_used_at  timestamptz  null,
    revoked_at    timestamptz  null
);

create unique index ux_api_key_prefix on api_key (prefix);
create index ix_api_key_app on api_key (app_id);

-- ---------------------------------------------------------------------------
-- One code sent. The code itself is never stored: only an HMAC of it, keyed by
-- a server-side pepper and bound to this challenge's id.
-- ---------------------------------------------------------------------------
create table otp_challenge (
    id            uuid         primary key,
    app_id        uuid         not null references client_app (id),
    phone         varchar(20)  not null,
    purpose       varchar(40)  not null,
    code_hash     varchar(64)  not null,
    expires_at    timestamptz  not null,
    attempts      smallint     not null default 0,
    max_attempts  smallint     not null,
    verified_at   timestamptz  null,
    request_ip    varchar(64)  null,
    created_at    timestamptz  not null default now(),
    check (attempts >= 0 and attempts <= max_attempts)
);

-- Rate limits: sends to one phone for one app, and sends from one IP.
create index ix_otp_challenge_app_phone on otp_challenge (app_id, phone, created_at desc);
create index ix_otp_challenge_ip on otp_challenge (request_ip, created_at desc);
