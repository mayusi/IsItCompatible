# tools/ingest — build-time community-data ingest

**This directory is a maintainer tool. None of it ships in the APK.**
The app never scrapes or calls any compatibility source at runtime. This tool
runs at dev/build time, pulls **real** community compatibility data, normalises
it to the app's seed DTO JSON shapes, tags honest provenance, dedupes against
the existing seed, and writes curated JSON into `app/src/main/assets/seed/`.
Those emitted seed files **are** committed; the tool itself is never referenced
by the app.

## What it produces

| Output (committed) | Contents | Provenance tag |
| --- | --- | --- |
| `app/src/main/assets/seed/reports/emuready-snapshot.json` | Real Android-device compatibility reports | `source = EMUREADY_SNAPSHOT` (the recommender treats anything ≠ `GENERATED_HEURISTIC` as **real**) |
| `app/src/main/assets/seed/guides/gamenative-community.json` | Tier-3 "From the community" setup guides for GameNative listings, each with a real `sourceUrl` backlink | `tier = 3` (NOT tier 1 — tier 1 = the user's own device-verified data) |

`BundledCompatSource` already walks `assets/seed/**` recursively, so both files
load automatically as bundled (real) data. No app code change is needed to pick
them up.

## How to run

```bash
# Proof-of-real sample (a few dozen popular games), writes real seed files:
python tools/ingest/ingest.py --max-pages 24 --as-of 2026-06-08

# Full ingest (all ~16.5k handheld listings — slow; respects rate limits):
python tools/ingest/ingest.py --max-pages 0 --as-of 2026-06-08
```

Flags: `--max-pages N` (0 = all), `--limit` (EmuReady max 50/page),
`--as-of YYYY-MM-DD` (stamped as `dataAsOf`/`generatedAt`), `--sleep` (seconds
between pages — rate-limit politeness). Stdlib only, no pip deps. Re-running
regenerates both files cleanly (the tool excludes its own outputs when computing
the existing-seed dedup set).

## Researched sources (verified live 2026-06-08)

### EmuReady — public mobile API (the real, ingestable source)
- Repo: [Producdevity/EmuReady](https://github.com/Producdevity/EmuReady) (Next.js + tRPC + Clerk).
- Base: `https://www.emuready.com/api/mobile/trpc`
- Endpoints used:
  - `listings.get` — **handheld / Android-device** reports. **No auth.** This is
    the primary source (real SoC + GPU + device + performance label + notes +
    `createdAt` + author). ~16,533 listings, paginated (`pagination.pages`,
    `hasNextPage`).
  - `pcListings.get` — PC/Winlator reports (desktop CPU/GPU/OS). Requires the
    superjson wrapper `?input={"json":{...}}`. **Not ingested as reports**: the
    device fingerprint is desktop hardware, which doesn't map to the app's
    Android SoC/GPU model honestly.
- tRPC GET format: `?input=` + URL-encoded `{"json": {page, limit, search, ...}}`.
- Performance scale (confirmed live): rank 1 `Perfect`, 2 `Great`, 3 `Playable`,
  5 `Ingame`, 8 `Nothing` → mapped to the app's `PERFECT/PLAYABLE/GLITCHY/CRASHES`.
- Rate limits: docs say "generous rate limits for public endpoints"; the tool
  paginates politely with `--sleep`.
- Docs: <https://www.emuready.com/docs/api>, OpenAPI:
  <https://www.emuready.com/api-docs/mobile-openapi.json>.

### GameNative compatibility data — NOT ingested (honesty finding)
- [gamenative.app/compatibility](https://gamenative.app/compatibility/) is the
  official community list, but has no documented public JSON/API for bulk export.
- [andreisugu/gamenative-config-tools](https://github.com/andreisugu/gamenative-config-tools)
  (MIT) ships `public/cached-configs.json` — but on inspection (2026-06-08) this
  is **placeholder/example data** ("Example Game 1", 3 rows, ~1.8 KB) with a
  stubbed `configs` object, **not** real community `<Game>_config.json` payloads.
  Its `games.json`/`devices.json`/`filters.json` are autocomplete name lists, not
  compatibility reports.
- **Decision:** do NOT commit that data — it would violate the honesty rule (no
  fabricated/placeholder data). We therefore attach **no** `gameNativeConfig`
  blob to the community guides. GameNative community signal is instead captured
  honestly via real EmuReady GameNative-emulator listings (tier-3 guides with a
  real EmuReady backlink). A real flat config can be supplied later (see the
  reference schema at `reference/gamenative/REAL_gamenative_config_schema_*.json`)
  and attached to a guide's `gameNativeConfig` once a genuine source exists.

## Licensing / attribution

- **EmuReady** code is **GPL-3.0-or-later**; community compatibility data is
  user-contributed on EmuReady. The API has no stated redistribution license, so
  we keep the snapshot **curated + attributed**: every report carries
  `sourceRef = https://www.emuready.com/listings/<id>` and every community guide
  carries `sourceUrl` to the same. The app's README/About already credits
  [EmuReady](https://emuready.com). For a **full** bulk-redistribution snapshot,
  confirm terms with the EmuReady maintainer (Discord/GitHub) first; the small
  curated proof sample committed here is attributed and links back per listing.
- **andreisugu/gamenative-config-tools** is MIT — but we ingest nothing from it
  (placeholder data, see above), so no attribution is owed.

## Honesty rules (do not remove)

1. The app never scrapes at runtime. This tool is offline/build-time only.
2. Only vetted data with real provenance is committed; every report/guide carries
   a real source link + `dataAsOf`/`generatedAt`.
3. EmuReady reports → `EMUREADY_SNAPSHOT` (real). Community guides → tier 3, never
   tier 1.
4. If a field is unknown, **omit it** — never fabricate (e.g. no `avgFps` because
   EmuReady listings have no numeric fps field; no `ramMb` because handheld
   listings don't expose it; no `gameNativeConfig` because no real source exists).
5. Reports only attach to games that already exist in the seed (title match), so
   we never invent games and every imported report actually surfaces in the app.
