"""Validate Stage 16 schema coverage and exported artifacts without Docker or npm.

Run from repo root: python docs/design/m10/fixtures/verify-stage16.py
Use export-analytics-schema.py --check separately for a read-only live catalog comparison.
"""
import hashlib
import json
from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
DOCS = ROOT / 'docs/design/m10'


def main():
    schema = json.loads((DOCS / 'analytics-schema.json').read_text(encoding='utf-8'))
    migrations = ROOT / 'backend/analytics-ai-service/src/main/resources/db/migration'
    hashes = {p.name: hashlib.sha256(p.read_bytes().replace(b'\r\n', b'\n')).hexdigest()
              for p in sorted(migrations.glob('V*__*.sql'))}
    assert hashes == schema['migrationSha256'], 'Migrations changed; refresh and review the schema documentation'
    assert schema['migrationVersions'] == ['1', '2', '3', '4', '5']
    source = (DOCS / 'analytics-erd.mmd').read_text(encoding='utf-8')
    entities = re.findall(r'^\s*\w+\["([\w_]+)"\]\s*\{\n(.*?)^\s*\}', source, re.M | re.S)
    expected = {t['name']: t for t in schema['tables']}
    assert set(name for name, _ in entities) == set(expected), 'ERD table coverage differs from the catalog'
    total = 0
    for name, content in entities:
        attributes = {}
        for line in content.splitlines():
            match = re.match(r'\s*(\w+)\s+(\w+)\s*(.*?)\s*"([^"]*)"\s*$', line)
            assert match, f'Unrecognized ERD attribute in {name}: {line}'
            attributes[match[2]] = (match[3], match[4])
        table = expected[name]
        assert set(attributes) == {c['name'] for c in table['columns']}, f'Missing or extra columns in {name}'
        for column in table['columns']:
            badges, comment = attributes[column['name']]
            assert ('NOT NULL' not in comment) == column['nullable'], f'Nullability mismatch: {name}.{column["name"]}'
            for kind, key in [('p', 'PK'), ('u', 'UK')]:
                needed = any(c['type'] == kind and column['name'] in c['columns'] for c in table['constraints'])
                assert (key in badges) == needed, f'Key mismatch: {name}.{column["name"]}'
        assert all(c['type'] != 'f' for c in table['constraints']), 'Unexpected SQL FK'
        total += len(attributes)
    assert len(entities) == 11 and total == 97
    print('PASS schema: all 11 tables, 97 columns, nullability and PK/UK markers match; no SQL foreign keys')
    artifacts = []
    manifest = json.loads((ROOT / 'docs/test-reports/m10/stage-16/render-manifest.json').read_text(encoding='utf-8'))
    for name, checksum in manifest['sourcesSha256'].items():
        assert hashlib.sha256((DOCS / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest() == checksum, f'Rerender changed source: {name}'
    for artifact in manifest['outputs']:
        assert hashlib.sha256((DOCS / artifact['file']).read_bytes()).hexdigest() == artifact['sha256'], f'Changed export: {artifact["file"]}'
    for name in ['ai-tool-calling-sequence', 'analytics-erd']:
        svg = ET.parse(DOCS / f'{name}.svg').getroot()
        assert svg.tag.endswith('svg') and svg.get('viewBox'), f'Invalid SVG: {name}'
        png = (DOCS / f'{name}.png').read_bytes()
        assert png.startswith(b'\x89PNG\r\n\x1a\n'), f'Invalid PNG: {name}'
        width, height = struct.unpack('>II', png[16:24])
        assert min(width, height) > 1000, f'Export too small: {name}'
        pdf = (DOCS / f'{name}.pdf').read_bytes()
        assert pdf.startswith(b'%PDF-') and b'%%EOF' in pdf[-1024:], f'Invalid PDF: {name}'
        assert len(re.findall(rb'/Type\s*/Page\b', pdf)) >= 1, f'No PDF pages: {name}'
        artifacts.append({'diagram': name, 'pngWidth': width, 'pngHeight': height,
                          'files': [f'{name}.{extension}' for extension in ['mmd', 'svg', 'png', 'pdf']]})
        print(f'PASS exports: {name} - parseable SVG, {width}x{height} PNG, complete PDF')
    result = {'tables': len(entities), 'columns': total, 'sqlForeignKeys': 0, 'cliVersion': manifest['cliVersion'],
              'migrationVersions': schema['migrationVersions'], 'artifacts': artifacts}
    evidence = ROOT / 'docs/test-reports/m10/stage-16'
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / 'diagram-validation.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
