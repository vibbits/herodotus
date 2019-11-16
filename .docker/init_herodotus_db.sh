#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 <<-EOSQL
     CREATE USER jacol;
     CREATE USER herodotus;
     CREATE DATABASE herodotus;
     GRANT ALL PRIVILEGES ON DATABASE herodotus TO PUBLIC;
EOSQL

psql -v ON_ERROR_STOP=1 --dbname herodotus <<-EOSQL
     CREATE TABLE messages(
         id serial PRIMARY KEY,
         ts TIMESTAMP NOT NULL,
         slackts VARCHAR (30) NOT NULL,
         threadts VARCHAR (30),
         uid VARCHAR (12),
         channel VARCHAR (12) NOT NULL,
         message TEXT NOT NULL
    );
    GRANT ALL PRIVILEGES ON TABLE messages TO PUBLIC;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO PUBLIC;
EOSQL
