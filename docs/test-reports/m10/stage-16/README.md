# Stage 16 diagram verification

Scope: documentation, schema metadata export and diagram rendering only. No Java,
frontend, migration, infrastructure or public API behavior changed.

The diagram sources were checked against the current chat/tool implementation,
analytics repositories and Flyway V1–V5. A read-only PostgreSQL catalog query
confirmed 10 application tables plus Flyway history, 97 columns and no SQL
foreign keys. It reads metadata and successful migration version numbers only;
no application records or credentials are exported.

Commands:

```powershell
python docs/design/m10/fixtures/export-analytics-schema.py --check
python docs/design/m10/fixtures/render-diagrams.py --chrome '<installed-Chrome-executable>'
python docs/design/m10/fixtures/verify-stage16.py
```

Results:

- Live schema and migration SHA-256 hashes match `analytics-schema.json`.
- All 11 tables and 97 columns are present in the ERD.
- PK/UK markers, composite key membership and nullability match the snapshot.
- V1–V5 migration hashes match the repository.
- Both sources render with Mermaid CLI 12.2.0 and installed Chrome.
- Both SVGs parse as XML, PNGs have valid headers and high-resolution dimensions,
  and PDFs contain complete PDF envelopes and page objects.
- PNG previews were visually inspected for readable labels and complete diagrams.

`diagram-validation.json` records the automated counts, output names and PNG
dimensions. `render-manifest.json` fingerprints the two sources, rendering config
and six exports so stale or altered artifacts are detected. The exported files
are in [docs/design/m10](../../../design/m10/).
SVG/PDF copies preserve vector zoom for detailed reading.

Application tests were not repeated for this documentation-only change. Prior
backend/frontend evidence remains in [Stages 12–15](../stages-12-to-15/README.md).
Live Azure verification is still pending, and the separately recorded current
gateway source packaging limitation is unchanged.
