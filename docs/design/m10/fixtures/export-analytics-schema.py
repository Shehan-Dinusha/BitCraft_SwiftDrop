"""Export analytics schema metadata only; never read application records.

Run from repo root: python docs/design/m10/fixtures/export-analytics-schema.py
Use --check to compare the running schema and migration hashes with the saved snapshot.
Requires the local analytics PostgreSQL container and Docker CLI.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
OUTPUT = ROOT / 'docs/design/m10'
MIGRATIONS = ROOT / 'backend/analytics-ai-service/src/main/resources/db/migration'
SQL = """
WITH tables AS (
 SELECT c.oid, c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind='r'
)
SELECT jsonb_build_object('database', current_database(), 'schema', 'public', 'tables',
 (SELECT jsonb_agg(jsonb_build_object('name', t.relname,
  'columns', (SELECT jsonb_agg(jsonb_build_object('name', a.attname,
    'type', format_type(a.atttypid,a.atttypmod), 'nullable', NOT a.attnotnull,
    'default', pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum)
   FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
   WHERE a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped),
  'constraints', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', co.conname,
    'type', co.contype, 'definition', pg_get_constraintdef(co.oid),
    'columns', (SELECT coalesce(jsonb_agg(a.attname ORDER BY k.pos),'[]'::jsonb)
      FROM unnest(co.conkey) WITH ORDINALITY k(num,pos)
      JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num)) ORDER BY co.conname),'[]'::jsonb)
   FROM pg_constraint co WHERE co.conrelid=t.oid),
  'indexes', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', indexname,
    'definition', indexdef) ORDER BY indexname),'[]'::jsonb)
    FROM pg_indexes WHERE schemaname='public' AND tablename=t.relname)) ORDER BY t.relname) FROM tables t),
 'migrationVersions', (SELECT jsonb_agg(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success));
"""
PURPOSE = {
    'processed_telemetry_events': 'Idempotent event inbox and retained canonical payloads for projection replay.',
    'analytics_order_projection': 'Reduced per-order state, assignment, final outcome and conflict marker.',
    'daily_delivery_stats': 'Daily creation and outcome counts in Asia/Colombo event time.',
    'analytics_couriers': 'Known assigned couriers; not an online availability registry.',
    'analytics_order_couriers': 'Distinct observed order/courier assignment pairs.',
    'courier_daily_stats': 'Daily outcomes plus CURRENT active workload bucketed by assignment date.',
    'operational_sla_snapshots': 'Legacy retained SLA snapshots; current reporting reads projections instead.',
    'courier_performance_metrics': 'Legacy retained courier metrics; current reporting reads projections instead.',
    'ai_delivery_insights': 'Persisted operational delay-risk predictions; separate from customer chat.',
    'ai_tool_invocation_audit': 'Metadata-only audits. Prompts and arguments remain null; output_result may contain a safe outcome code, never raw tool output.',
    'flyway_schema_history': 'Flyway-managed migration metadata; not a business entity.'
}
RELATIONSHIPS = [
    '    analyticsOrderProjection ||..o{ processedTelemetryEvents : "order_id logical"',
    '    analyticsOrderProjection ||..o{ analyticsOrderCouriers : "order_id logical"',
    '    analyticsCouriers ||..o{ analyticsOrderCouriers : "courier_id logical"',
    '    analyticsCouriers ||..o{ courierDailyStats : "courier_id logical"',
    '    analyticsCouriers o|..o{ analyticsOrderProjection : "current courier_id logical"'
]


def identifier(name):
    first, *parts = name.split('_')
    return first + ''.join(part.title() for part in parts)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    result = subprocess.run(['docker', 'exec', 'swiftdrop-analytics-postgres', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                             '-U', 'swiftdrop', '-d', 'swiftdrop_analytics', '-At', '-c', SQL],
                            capture_output=True, text=True, check=True)
    schema = json.loads(result.stdout)
    schema['migrationSha256'] = {p.name: hashlib.sha256(p.read_bytes().replace(b'\r\n', b'\n')).hexdigest()
                                 for p in sorted(MIGRATIONS.glob('V*__*.sql'))}
    snapshot = OUTPUT / 'analytics-schema.json'
    assert len(schema['tables']) == 11, 'Review new/removed tables before publishing the ERD'
    assert all(c['type'] != 'f' for t in schema['tables'] for c in t['constraints']), 'Review newly introduced foreign keys'
    if args.check:
        assert schema == json.loads(snapshot.read_text(encoding='utf-8')), 'Schema or migrations differ from the snapshot'
        print('PASS: running analytics schema and migration hashes match the Stage 16 snapshot')
        return
    snapshot.write_text(json.dumps(schema, indent=2) + '\n', encoding='utf-8')
    erd = ['---', 'title: SwiftDrop analytics database - all dashed relationships are logical, no SQL foreign keys',
           '---', 'erDiagram', '    direction TB']
    dictionary = ['# Analytics schema dictionary', '',
                  'Generated from the local analytics PostgreSQL catalog by `fixtures/export-analytics-schema.py`.',
                  'Schema metadata only: no application records, credentials or prompts are exported.', '',
                  '**Scope:** 10 application tables plus Flyway history, after V1–V5. No SQL foreign keys exist.',
                  'Dashed ERD relationships describe application-maintained identifier matches, not database constraints.', '',
                  'The exact constraints, defaults and indexes are also in [analytics-schema.json](analytics-schema.json).', '']
    for table in schema['tables']:
        name = table['name']
        keys = {col['name']: [] for col in table['columns']}
        for constraint in table['constraints']:
            if constraint['type'] in ('p', 'u'):
                for col in constraint['columns']:
                    keys[col].append('PK' if constraint['type'] == 'p' else 'UK')
        erd.append(f'    {identifier(name)}["{name}"] {{')
        dictionary += [f'## `{name}`', '', PURPOSE[name], '',
                       '| Column | PostgreSQL type | Nullable | Key | Default |', '|---|---|---|---|---|']
        for col in table['columns']:
            datatype = col['type']
            short_type = ('varchar' if datatype.startswith('character varying') else
                          'timestamptz' if datatype == 'timestamp with time zone' else
                          'timestamp' if datatype == 'timestamp without time zone' else datatype.split('(')[0])
            badges = ', '.join(keys[col['name']])
            comment = ('NULL' if col['nullable'] else 'NOT NULL')
            if datatype.startswith(('numeric(', 'character varying(')):
                comment += '; ' + datatype.replace('character varying', 'varchar')
            if col['name'] in ('prompt_or_query', 'input_parameters'):
                comment += '; raw data not stored'
            if col['name'] == 'output_result':
                comment += '; safe outcome code only'
            erd.append(f'        {short_type} {col["name"]} {badges} "{comment}"')
            default = '`' + col['default'] + '`' if col['default'] is not None else '—'
            dictionary.append(f'| `{col["name"]}` | `{datatype}` | {"Yes" if col["nullable"] else "No"} | {badges or "—"} | {default} |')
        erd.append('    }')
        dictionary += ['', 'Constraints:', '']
        dictionary += [f'- `{c["name"]}`: `{c["definition"]}`' for c in table['constraints']]
        dictionary += ['', 'Indexes:', '']
        dictionary += [f'- `{index["definition"]}`' for index in table['indexes']]
        dictionary.append('')
    erd += ['', '    %% Logical associations only. Daily aggregates and standalone tables have no FK edges.', *RELATIONSHIPS]
    (OUTPUT / 'analytics-erd.mmd').write_text('\n'.join(erd) + '\n', encoding='utf-8')
    (OUTPUT / 'analytics-schema.md').write_text('\n'.join(dictionary).rstrip() + '\n', encoding='utf-8')
    print(f'Exported {len(schema["tables"])} tables and {sum(len(t["columns"]) for t in schema["tables"])} columns; no foreign keys')


if __name__ == '__main__':
    main()
