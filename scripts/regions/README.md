# Administrative region seed preparation

Source: Ministry of the Interior and Safety, `jscode20261026.zip`, published 2026-10-30.
See `source.json` for the source URL, ZIP SHA-256, record layout, and extraction criteria.
The CSV contains 3,630 unique administrative codes with a nonempty 읍면동 name and no deletion date. Province/city summary rows are excluded; branch offices are retained for explicit reconciliation.

The source does not contain coordinates. Run from the repository root on Windows:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\regions\resolve-regions.ps1 -Limit 20
```

After inspecting the first results, run without `-Limit 20` to process all remaining rows. The script reads `KAKAO_REST_API_KEY` from the environment or `infra/.env`, never prints it, and calls only `https://dapi.kakao.com`. Successful records are appended immediately to `build/region-seed/resolved.jsonl`, so restarting resumes from them. HTTP authorization failures stop immediately; other request failures stop after three attempts, preserving previous results.

Address lookup and community-center keyword lookups are tried, including 주민센터/행정복지센터 and removing 제 before digits in query text. Official names/codes remain unchanged. Every candidate is reverse-geocoded: only a `region_type=H` result with the exact official 10-digit code is accepted. Stored coordinates are the matching H record's coordinates, not the searched place's coordinates. No fallback to legal-dong (B), name-only match, or fabricated coordinates is allowed.

Outputs:
- `resolved.jsonl`: matched official codes and Kakao H coordinates; no credentials.
- `remaining.csv`: unmatched or not-yet-attempted codes after a completed run.
- `unresolved.csv`: failed matches encountered in that run.

This script does not connect to MySQL, alter application behavior, or create a Flyway migration. Keep generated output local until it is checked. Resolve unmatched codes (including recent administrative changes and branch offices) before declaring national coverage. V9 has now been generated for 3,565 verified codes; the 65 unresolved branch offices and complete provenance are documented in docs/data/regions/README.md. Once data is finalized, generate the reviewed V9 SQL with code-based upserts that preserve existing region IDs. Never rewrite an already applied versioned migration.

PowerShell and live Kakao execution must be checked on the Windows workstation; the authoring environment has neither PowerShell nor the API credential.
