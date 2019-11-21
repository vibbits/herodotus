#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 <<-EOSQL
     CREATE USER jacol;
     CREATE USER herodotus;
     CREATE DATABASE herodotus;
     GRANT ALL PRIVILEGES ON DATABASE herodotus TO PUBLIC;
EOSQL

psql -v ON_ERROR_STOP=1 --dbname herodotus <<-EOSQL
     CREATE TABLE users(
         id serial PRIMARY KEY,
         identifier TEXT UNIQUE NOT NULL,
         team TEXT,
         name TEXT NOT NULL,
         realname TEXT,
         tz TEXT,
         tzlabel TEXT,
         tzoffset INTEGER
     );
     GRANT ALL PRIVILEGES ON TABLE users TO PUBLIC;

     CREATE TABLE channels(
         id serial PRIMARY KEY,
         identifier TEXT UNIQUE NOT NULL,
         name TEXT NOT NULL,
         namenormalized TEXT,
         created TIMESTAMP NOT NULL,
         creator INTEGER REFERENCES users(id)
     );
     GRANT ALL PRIVILEGES ON TABLE channels TO PUBLIC;

     CREATE TABLE messages(
         id serial PRIMARY KEY,
         ts TIMESTAMP NOT NULL,
         type VARCHAR (30) NOT NULL,
         subtype VARCHAR (30),
         attachments INTEGER,
         reactions INTEGER,
         slackts VARCHAR (30) NOT NULL,
         threadts VARCHAR (30),
         sender INTEGER REFERENCES users(id),
         team TEXT,
         channel INTEGER REFERENCES channels(id),
         message TEXT NOT NULL,
         botid TEXT,
         botlink TEXT
    );
    GRANT ALL PRIVILEGES ON TABLE messages TO PUBLIC;

    CREATE TABLE attachments(
         id serial PRIMARY KEY,
         message_id INTEGER REFERENCES messages(id),
         identifier INTEGER NOT NULL,
         fallback TEXT NOT NULL,
         serviceurl TEXT,
         servicename TEXT,
         serviceicon TEXT,
         authorname TEXT,
         authorlink TEXT,
         authoricon TEXT,
         fromurl TEXT,
         originalurl TEXT,
         title TEXT,
         titlelink TEXT,
         text TEXT,
         imageurl TEXT,
         videohtml TEXT,
         footer TEXT,
         ts TIMESTAMP,
         filename TEXT,
         mimetype TEXT,
         url TEXT
    );
    GRANT ALL PRIVILEGES ON TABLE attachments TO PUBLIC;

    CREATE TABLE reactions(
        id serial PRIMARY KEY,
        message_id INTEGER REFERENCES messages(id),
        user_id INTEGER REFERENCES users(id),
        name TEXT NOT NULL,
        url TEXT
    );
    GRANT ALL PRIVILEGES ON TABLE reactions TO PUBLIC;

    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO PUBLIC;
EOSQL
