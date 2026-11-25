-- ---------------------------------------------------------------------------
-- Channels, and which key asked for each code.
--
-- An app's channel is chosen by the operator: SMS, WHATSAPP, or RANDOM (one of
-- the two, picked per send). A challenge records the channel it actually went
-- out on, so RANDOM is visible after the fact.
-- ---------------------------------------------------------------------------
alter table client_app
    add column channel varchar(10) not null default 'SMS'
        check (channel in ('SMS', 'WHATSAPP', 'RANDOM'));

alter table otp_challenge
    add column channel varchar(10) not null default 'SMS'
        check (channel in ('SMS', 'WHATSAPP'));

-- Null only for rows written before this migration.
alter table otp_challenge
    add column api_key_id uuid null references api_key (id);

-- The console's log: newest first, across apps or within one, optionally by phone.
create index ix_otp_challenge_created on otp_challenge (created_at desc);
create index ix_otp_challenge_app_created on otp_challenge (app_id, created_at desc);
create index ix_otp_challenge_phone on otp_challenge (phone, created_at desc);
