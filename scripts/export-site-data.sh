#!/usr/bin/env bash
# Regenerates the website's data from the real Java code and the real database.
#   docs/vectors.json      240 cases computed by the Java engine (the page re-checks its JS port)
#   docs/refusals.jsonl    every refusal MySQL gave during the attack tests
#   docs/audit-sample.json the demo database's audit chain (hashes checked by MySQL on insert)
# Usage: scripts/export-site-data.sh   (MySQL root on socket login, as in reset-db.sh)
set -e
cd "$(dirname "$0")/.."
MYSQL=${MYSQL:-"mysql -u root"}
scripts/build.sh > /dev/null
java -cp "out:lib/*" pathivu.tools.ExportVectors docs/vectors.json
scripts/reset-db.sh > /dev/null
rm -f docs/refusals.jsonl
java -Dpathivu.log=docs/refusals.jsonl -cp "out:lib/*" pathivu.TestRunner > /dev/null
scripts/reset-db.sh > /dev/null
{
  echo '{"note": "Exported from the demo database after scripts/reset-db.sh. Hashes were computed by Java and re-checked by MySQL SHA2() on insert.",'
  echo '"events": ['
  $MYSQL -N -r pathivu -e "SELECT JSON_OBJECT('id', e.event_id, 'incident', e.incident_id, 'actorId', e.actor_id, 'actor', u.full_name,
      'action', e.action, 'details', e.details, 'at', CONCAT(DATE_FORMAT(e.created_at, '%Y-%m-%dT%H:%i:%s.'), LPAD(FLOOR(MICROSECOND(e.created_at)/1000), 3, '0'), 'Z'),
      'prev', e.prev_hash, 'hash', e.hash)
    FROM audit_event e JOIN app_user u ON u.user_id = e.actor_id ORDER BY e.event_id" | sed '$!s/$/,/'
  echo ']}'
} > docs/audit-sample.json
echo "refusals: $(wc -l < docs/refusals.jsonl) · audit events: $(grep -c "\"hash\"" docs/audit-sample.json)"
