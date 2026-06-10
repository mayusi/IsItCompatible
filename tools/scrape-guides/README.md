# scrape-guides — offline guide research tool

**This directory is a maintainer tool. None of it ships in the APK.**

## What it is

A script that helps gather candidate setup-guide content from public sources so
the maintainer doesn't have to write every guide from scratch. It produces
**drafts** that a human reviews before any content reaches users.

## The hard rules (v0.8 decision)

1. **The app never scrapes anything at runtime.** Scraping is offline-only,
   here, run by a person.
2. **Nothing auto-commits.** Output lands in `drafts/` (gitignored). A human
   reads each draft, verifies accuracy + currency, and hand-copies vetted
   entries into `app/src/main/assets/seed/guides/` or the IsItCompatible-DB repo.
3. **Provenance is mandatory.** Every committed guide carries a `sourceLabel`,
   optional `sourceUrl` backlink, and a real `dataAsOf` date. EmuReady-derived
   content is tagged tier 3 ("From EmuReady"); hand-authored is tier 2.
4. **`manual` sources are never auto-fetched.** For wikis / Discord pins /
   Reddit, the tool only records the URL + a tier-0 ("UNVETTED — do not ship")
   skeleton. A human opens the page, reads it, and writes the guide by hand.
   tier-0 guides are filtered out by the app loader as a backstop.

## Usage

```bash
# Draft from EmuReady's API (the one legal, dated, stable source):
SCRAPE_STAMP=2026-05-29 SCRAPE_DATE=2026-05-29 \
  python tools/scrape-guides/scrape_guides.py emuready --game "God of War" --platform PS2

# Record a manual source for later human authoring (does NOT scrape):
SCRAPE_STAMP=2026-05-29 \
  python tools/scrape-guides/scrape_guides.py manual --emulator winlator-cmod \
    --url https://github.com/coffincolors/winlator/wiki
```

## Review workflow

1. Run the tool → draft appears in `drafts/`.
2. Open the draft. Read `_raw_emuready` (or the manual source URL).
3. Verify the info is **current and correct** for the emulator + game.
4. If good: set `emulatorId`, fill `steps` (typed: GET_APP/GET_DRIVER/CONTAINER/
   FILES/BIOS/ACTION/TIP), set `dataAsOf` to today, copy the `guide` block into
   the appropriate seed file. Delete the `_draft`/`_review_required` keys.
5. If not good enough: discard. Never ship unverified content.
