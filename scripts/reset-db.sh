#!/usr/bin/env bash
# Rebuilds the database from scratch and loads the demo.
# Usage: scripts/reset-db.sh            (MySQL root with no password / socket login)
#        MYSQL="mysql -u root -p" scripts/reset-db.sh
set -e
cd "$(dirname "$0")/.."
MYSQL=${MYSQL:-"mysql -u root"}
$MYSQL < db/schema.sql
$MYSQL < db/seed.sql
$MYSQL < db/grants.sql
# lets a non-SUPER admin re-create a trigger (only the tamper test does this)
$MYSQL -e "SET GLOBAL log_bin_trust_function_creators = 1;" || true
# local helper account for Setup (skip if you use root in db.properties)
$MYSQL -e "CREATE USER IF NOT EXISTS 'pathivu_admin'@'localhost' IDENTIFIED BY 'admin-local-2026'; GRANT ALL ON pathivu.* TO 'pathivu_admin'@'localhost';"
scripts/build.sh
java -cp "out:lib/*" pathivu.tools.Setup
java -cp "out:lib/*" pathivu.tools.DemoSeeder
