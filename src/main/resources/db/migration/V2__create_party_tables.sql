-- A party table: a DM's group that players apply to join. The DM does not take a seat.
CREATE TABLE party_tables (
    id               bigint           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dm_id            bigint           NOT NULL REFERENCES users (id),
    title            varchar(100)     NOT NULL,
    description      text,
    first_session_at timestamptz      NOT NULL,
    location_name    varchar(200)     NOT NULL,
    latitude         double precision NOT NULL CHECK (latitude  BETWEEN -90  AND 90),
    longitude        double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    seat_limit       smallint         NOT NULL CHECK (seat_limit BETWEEN 1 AND 4),
    accepted_count   smallint         NOT NULL DEFAULT 0,
    status           varchar(20)      NOT NULL DEFAULT 'OPEN'
                                      CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    version          bigint           NOT NULL DEFAULT 0,
    created_at       timestamptz      NOT NULL DEFAULT now(),
    updated_at       timestamptz      NOT NULL DEFAULT now(),
    -- Seat-limit backstop: compares two columns, so it's written as a table-level check
    CONSTRAINT party_tables_accepted_within_seat_limit
        CHECK (accepted_count BETWEEN 0 AND seat_limit)
);

-- Postgres doesn't index foreign keys automatically: supports "tables I run as DM"
CREATE INDEX party_tables_dm_id_idx ON party_tables (dm_id);

-- Partial index: browsing open tables by start date only ever looks at OPEN rows
CREATE INDEX party_tables_open_first_session_idx ON party_tables (first_session_at) WHERE status = 'OPEN';
