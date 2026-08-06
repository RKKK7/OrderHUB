#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE product_db;
    CREATE DATABASE order_db;
    CREATE DATABASE notification_db;
EOSQL

echo "Created 3 OMS databases: product_db, order_db, notification_db"
