#!/usr/bin/env bash
# Compiles everything into out/. Put the MySQL JDBC driver jar in lib/ first.
set -e
cd "$(dirname "$0")/.."
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out -cp "lib/*" $(find src test -name "*.java")
echo "Built into out/"
