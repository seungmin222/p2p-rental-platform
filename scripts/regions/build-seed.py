"""Validate collected coordinates against official codes and render a NEW migration.
Never overwrite a migration that may already have been applied.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent


def render(resolved_path, unresolved_path):
    with (HERE / 'administrative-regions-20261026.csv').open(encoding='utf-8-sig', newline='') as f:
        official = {r['code']: r for r in csv.DictReader(f)}
    records = {}
    for line in resolved_path.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        code = row['code']
        if code in records or code not in official:
            raise ValueError(f'Duplicate or unknown code: {code}')
        if any(row[k] != official[code][k] for k in ('code', 'name', 'sido', 'sigungu')):
            raise ValueError(f'Official label mismatch: {code}')
        if row['source'] != 'Kakao H center':
            raise ValueError(f'Unverified source: {code}')
        lat, lng = row['centerLat'], row['centerLng']
        if not (math.isfinite(lat) and math.isfinite(lng) and 33 <= lat <= 39 and 124 <= lng <= 132):
            raise ValueError(f'Invalid coordinates: {code}')
        records[code] = row
    with unresolved_path.open(encoding='utf-8-sig', newline='') as f:
        missing_rows = list(csv.DictReader(f))
    missing = {r['code']: r for r in missing_rows}
    if len(missing) != len(missing_rows) or set(records) & set(missing):
        raise ValueError('Duplicate/overlapping unresolved codes')
    if set(records) | set(missing) != set(official):
        raise ValueError('Input files do not partition the official code list')
    for code, row in missing.items():
        if any(row[k] != official[code][k] for k in ('name', 'sido', 'sigungu')) or not row['name'].endswith('출장소'):
            raise ValueError(f'Unresolved ordinary region: {code}')
    def quote(value):
        if '\\' in value or any(ord(c) < 32 for c in value):
            raise ValueError('Unexpected control character')
        return "'" + value.replace("'", "''") + "'"
    lines = [
        '-- MOIS jscode20261026 + Kakao H centers collected 2026-11-02.',
        f'-- {len(records)} exact-code matches; {len(missing)} unresolved branch offices are NOT seeded.',
        '-- Coverage and sources: docs/data/regions/README.md.',
        '-- Preserve existing region IDs and created_at so member/item references remain valid.',
        '-- Resolved JSONL SHA-256: ' + hashlib.sha256(resolved_path.read_bytes().replace(b'\r\n', b'\n')).hexdigest(),
        '-- Further additions/corrections MUST use a new Flyway version.', '']
    ordered = sorted(records.values(), key=lambda r: r['code'])
    for start in range(0, len(ordered), 250):
        lines.append('INSERT INTO regions (code, name, sido, sigungu, center_lat, center_lng, created_at, updated_at) VALUES')
        values = []
        for r in ordered[start:start + 250]:
            labels = ', '.join(quote(r[k]) for k in ('code', 'name', 'sido', 'sigungu'))
            values.append(f"({labels}, {r['centerLat']:.12f}, {r['centerLng']:.12f}, NOW(6), NOW(6))")
        lines.append(',\n'.join(values))
        lines.extend(['AS incoming ON DUPLICATE KEY UPDATE',
                      'name = incoming.name, sido = incoming.sido, sigungu = incoming.sigungu,',
                      'center_lat = incoming.center_lat, center_lng = incoming.center_lng, updated_at = incoming.updated_at;', ''])
    return '\n'.join(lines), len(records), len(missing)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('resolved', type=Path)
    parser.add_argument('unresolved', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    sql, count, missing = render(args.resolved, args.unresolved)
    # Exclusive create: never silently alter an applied migration's checksum.
    with args.output.open('x', encoding='utf-8', newline='\n') as f:
        f.write(sql)
    print(f'Created {args.output}: {count} rows; {missing} unresolved branch offices documented separately')
