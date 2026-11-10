CREATE TABLE users (
    id           bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        varchar(255) NOT NULL,
    display_name varchar(50)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: 'Player@x.com' and 'player@x.com' are the same user
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));
