#!/usr/bin/env bash
# Resets the database (tests expect the demo data), then runs every test.
set -e
cd "$(dirname "$0")/.."
bash scripts/reset-db.sh > /dev/null
java -cp "out:lib/*" pathivu.TestRunner
