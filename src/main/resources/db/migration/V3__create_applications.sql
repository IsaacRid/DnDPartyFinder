-- A player's request to join a party table. The DM accepts or rejects it.
CREATE TABLE applications (
    id             bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    party_table_id bigint       NOT NULL REFERENCES party_tables (id),
    applicant_id   bigint       NOT NULL REFERENCES users (id),
    status         varchar(20)  NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    message        varchar(500),
    version        bigint       NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    -- One application per player per table, even after rejection or withdrawal (no re-applying).
    -- Its index also serves "all applications for a table".
    CONSTRAINT applications_one_per_player_per_table UNIQUE (party_table_id, applicant_id)
);

-- Supports "my applications" for a player
CREATE INDEX applications_applicant_id_idx ON applications (applicant_id);
