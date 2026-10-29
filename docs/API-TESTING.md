# Bible NDI — API Testing Guide

The app serves a local HTTP API on your LAN (default port 8080). This guide
covers the interactive test page, the machine-readable spec, and practical
tips with copy-paste examples.

## Quick start

1. Find the tablet's IP (Setup tab → URLs, or `GET /api/debug` → `localIp`).
2. Open **`http://<tablet-ip>:8080/api-docs`** in any browser on the same network.
3. Expand an endpoint, fill the fields, hit **Execute**, read the response.

No curl, no Postman, no internet — everything runs on your LAN.

## The two URLs

| URL | What it is |
|-----|------------|
| `GET /api-docs` | Interactive test page: help, tips, examples, and a Try-it-out form per endpoint. Single self-contained HTML file, dark theme, zero external dependencies. |
| `GET /api/openapi.json` | OpenAPI 3.0 spec (machine-readable). The test page fetches this and builds its UI from it, so docs never go stale. You can also feed it to external tools (Postman, Insomnia, code generators). |

## Endpoints covered

| Method & Path | What it does |
|---------------|--------------|
| `POST /api/trigger?ref=` | Go live with a verse (`?ref=John+3:16`, or `?book=&chapter=&verse=`, optional `?target=lower\|show\|both`) |
| `POST /api/clear` | Clear the verse (take off air; per-feed Clear Mode applies) |
| `GET /api/sermons` | List saved sermon lists |
| `POST /api/sermons` | Parse text into verses; save as a named sermon (`{name, text, activate}`) |
| `GET /api/sermons/{id}` | Read one saved sermon |
| `POST /api/sermons/{id}/activate` | Load into working list (`?merge=1` merges, default replaces) |
| `POST /api/sermons/{id}/rename` | Rename (`?name=` or JSON body) |
| `POST /api/sermons/{id}/delete` | Delete |
| `GET /api/sermons/{id}.{json\|txt\|md\|csv}` | Export a sermon |
| `GET /api/sermons/current` | Working-list state (`index, total, isLive, entries`) |
| `POST /api/sermons/current/next` | Step forward + go live (stops at end, never wraps) |
| `POST /api/sermons/current/prev` | Step back + go live (stops at start) |
| `GET /api/verse/copy?ref=` | Copy-ready verse text (no ref = live verse) |
| `GET /api/debug` | Diagnostics: version, device, templates, NDI feeds, error log |
| `GET /api/export/all` | Full backup JSON (sermons, templates, theme, recents) |
| `POST /api/import/backup?dryRun=1` | Restore a backup (`dryRun=1` validates only) |

## Tips

- **Reference format:** `?ref=John+3:16` — use `+` for spaces. English *and* Arabic book names work (`?ref=يوحنا+3:16`).
- **Always check `invalidRefs`** after `POST /api/sermons`. Anything listed there was *not* added — fix the spelling and re-submit.
- **Method matters:** creating, triggering, clearing, stepping, and deleting require POST. A "Use POST..." error means switch the method.
- **Sermon IDs:** `GET /api/sermons` first, copy an `id`, paste it into activate/rename/delete/export.
- **Next/prev never wrap** — stopping at the ends is deliberate (no surprise jumps mid-service).
- **Clear is per-feed:** Full Show keeps its card/background (Clear verse only); Lower Third goes fully transparent (Clear all layers).
- **Backup first:** `GET /api/export/all` → save the JSON → test restores with `?dryRun=1`.
- **JSON bodies** must be valid JSON. The test page pre-fills the example — edit values, keep the braces.

## Examples

### 1. Create and save a sermon list
```bash
curl -X POST http://TABLET-IP:8080/api/sermons \
  -H "Content-Type: application/json" \
  -d '{"name":"Passover","text":"Exodus 12:11\nExodus 12:21\nLeviticus 23:5"}'
```
Returns `{ok:true, id, parsedCount, entries[], invalidRefs[]}`.

### 2. Put a verse on air
```bash
curl -X POST "http://TABLET-IP:8080/api/trigger?ref=John+3:16"
```

### 3. Step through a sermon (presenter / foot pedal)
```bash
curl -X POST http://TABLET-IP:8080/api/sermons/current/next
curl -X POST http://TABLET-IP:8080/api/sermons/current/prev
```

### 4. Clear, then back up everything
```bash
curl -X POST http://TABLET-IP:8080/api/clear
curl http://TABLET-IP:8080/api/export/all -o backup.json
```

## For developers

- Spec source: `app/src/main/java/com/arabicchristianmedia/server/ApiDocs.kt`
  (`OPENAPI_JSON` const). The test page (`API_DOCS_HTML`) renders from it.
- Routes in `NdiBroadcastServer.kt`: `/api/openapi.json` → `serveOpenApiSpec()`,
  `/api-docs` → `serveApiDocsPage()`. Both follow the existing serve* patterns.
- Adding an endpoint to the docs = adding it to the `paths` object in the spec.
  The test page picks it up automatically — no HTML changes needed.
- Kotlin raw-string rule: the spec/HTML must not contain `"""` or `$`
  (string templates). The page's JavaScript uses string concatenation only.
