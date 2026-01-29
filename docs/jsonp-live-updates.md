# JSONP Live Updates (file://)

This document explains the JSONP-based live update mechanism used by the
execution-counter HTML report when opened directly from disk (file://).

Status: Reference
Last updated: 2026-01-30

---

## Why JSONP is needed

Browsers block `fetch()` from `file://` origins due to CORS/security. However,
they still allow loading scripts from local files. The report uses this
loophole to update live without any server.

---

## How it works

1. **The agent writes three files** in the same folder:
   - `report.html`: UI + initial snapshot
   - `report.json`: latest snapshot payload (for http/https or IDE servers)
   - `report.js`: JSONP wrapper for `file://`

2. **JSONP wrapper**:
   - The JS file is written as:
     ```
     window.loadExecutionData && window.loadExecutionData({ ...payload... });
     ```
   - This executes immediately when the browser loads the script.

3. **Polling loop in the HTML**:
   - Every `POLL_INTERVAL` (2s), the report:
     - injects a `<script src="report.js">` tag
     - removes the previous tag
     - calls `window.loadExecutionData(...)` when the script loads

4. **Payload format**:
   ```json
   {
     "generatedAt": 1700000000000,
     "files": [
       { "path": "com/example/Foo.java", "counts": { "12": 3 }, "content": "..." }
     ]
   }
   ```

5. **Stale update protection**:
   - Each payload includes `generatedAt`.
   - The page tracks `lastUpdate` and ignores any payload older than that.
   - This prevents cached JSONP from overwriting newer counts.

---

## Gotchas

1. **`file://` cannot use fetch**
   - `fetch(report.json)` fails under `file://` due to access control.
   - The report skips `fetch` when opened as a file.

2. **Local file caching**
   - Some browsers cache `file://` scripts aggressively.
   - The report appends a `?t=...` cache-buster for JSONP (including `file://`)
     and ignores stale payloads using `generatedAt`.
   - If a browser refuses query strings for local scripts, remove the cache-bust
     for `file://` and rely on `generatedAt` staleness guards.

3. **File naming**
   - JSON/JSONP filenames are derived from the HTML filename.
   - If you output `my-report.html`, the files are:
     - `my-report.json`
     - `my-report.js`

4. **Live/Offline indicator**
   - The indicator is time-based, not request-based.
   - It stays Live as long as updates arrive within `OFFLINE_TIMEOUT`
     (default: max(6000ms, 3×poll interval)).
   - It flips to Stale if no update arrives before the timeout.

5. **Refresh behavior**
   - The HTML includes an embedded snapshot.
   - On refresh, that snapshot is treated as “fresh at page load” to prevent
     a quick Offline flash before the first JSONP update arrives.

---

## Observed runtime glitches

- When opening `execution-report.html` from `radio-metadata-fr-scraper/target/site`, DevTools reported:
  ```
  Fetch API cannot load .../execution-report.json?t=1769781213235 due to access control checks.
  Cross origin requests are only supported for HTTP.
  ```
  That proves `fetch()` is blocked for `file://`, so the JSONP path must be the primary mechanism for live refresh.
- During a refresh the counts briefly reflected the latest data and then snapped back to the original snapshot. The current `window.loadExecutionData` ignores payloads whose `generatedAt` is not greater than the last recorded timestamp, so stale snapshots (like the embedded HTML payload) sometimes overwrite newer JSONP data until `lastUpdate` is reseeded. Guard against this by updating the heartbeat timestamp on every successful poll, even if the payload’s `generatedAt` did not advance.
- The Online/Offline pill was flashing green during the polling script load and red immediately afterward because `updateLiveStatus` only checks the most recent `generatedAt`. If a new poll finishes before the next `setInterval`, the indicator still sees two stale windows (the previous snapshot and the interim gap). The fix is to track the wall-clock time of the last successful poll and use that for offline detection rather than the embedded payload timestamps alone.

## Recommendations for future refactors

- When modularizing the UI (see `docs/TODO.md`) keep the JSONP polling logic inside a composable such as `useLivePolling`. Include both `lastGeneratedAt` (payload timestamp) and `lastPollAt` (wall clock) so the offline indicator only reports stale once both values are old.
- Under `file://`, avoid adding cache-busting query strings because some browsers reject `foo.js?t=123` when loading local files. Only append `?t=` for HTTP(S) fetches.
- Continue to expose `window.loadExecutionData` so that the Vite bundle can hydrate the initial snapshot and respond to injected JSONP scripts. The HTML template should still create `<script src="report.js">` tags every 2 seconds, but the script tag can now load the compiled bundle’s helper method (e.g., the bundle can call `window.loadExecutionData(wrapperPayload)` internally).
- Document any new helper layers in the README so future engineers know where to find the Vite source and how it maps back to the generated `report-app.js` used by the agent.

## If live updates don’t work

Checklist:
1. Confirm `report.js` is updating (mtime changes).
2. Confirm `report.js` and `report.html` are siblings.
3. Open DevTools and check for script load errors.
4. Verify the agent is running and `flushInterval` > 0.
