package com.arabicchristianmedia.server

/**
 * API documentation for the Bible NDI HTTP server.
 *
 * Two routes are wired in NdiBroadcastServer:
 *   GET /api/openapi.json -> the OpenAPI 3.0 spec below (OPENAPI_JSON)
 *   GET /api-docs         -> the self-contained test page (API_DOCS_HTML)
 *
 * The test page fetches the spec and renders a "Try it out" UI dynamically,
 * so the spec is the single source of truth. Everything is offline-first:
 * no CDN, no external dependencies.
 *
 * NOTE (Kotlin raw-string safety): the JSON and HTML below must not contain
 * triple-quotes (""") and must avoid "$" (string templates). The JavaScript
 * in the HTML uses string concatenation only — no template literals.
 */
object ApiDocs {

    const val OPENAPI_JSON = """
{
  "openapi": "3.0.0",
  "info": {
    "title": "Bible NDI - HTTP API",
    "version": "1.9.2",
    "description": "Local HTTP API served by the Bible NDI Android app. All endpoints are LAN-only. Control endpoints (trigger, clear, sermon step) change what is on air."
  },
  "servers": [
    { "url": "/", "description": "This tablet" }
  ],
  "paths": {
    "/api/trigger": {
      "post": {
        "summary": "Go live with a verse",
        "description": "Puts a verse on air immediately on both feeds (or one target feed).",
        "parameters": [
          { "name": "ref", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Free-form reference, e.g. John 3:16 or 1Jn 4:8. English or Arabic names.", "example": "John 3:16" },
          { "name": "book", "in": "query", "required": false, "schema": { "type": "integer" }, "description": "1-indexed book number (use instead of ref)." },
          { "name": "chapter", "in": "query", "required": false, "schema": { "type": "integer" } },
          { "name": "verse", "in": "query", "required": false, "schema": { "type": "integer" } },
          { "name": "target", "in": "query", "required": false, "schema": { "type": "string", "enum": ["both", "lower", "show"] }, "description": "Which feed. Default both." }
        ],
        "responses": {
          "200": { "description": "Verse is live. Returns {ok:true}." },
          "400": { "description": "Reference not understood." }
        }
      }
    },
    "/api/clear": {
      "post": {
        "summary": "Clear the verse (take off air)",
        "description": "Clears the live verse. Per-feed Clear Mode decides what remains: Full Show keeps card/background (Clear verse only), Lower Third goes fully transparent (Clear all layers).",
        "responses": {
          "200": { "description": "Returns {ok:true, cleared:true}." }
        }
      }
    },
    "/api/sermons": {
      "get": {
        "summary": "List saved sermons",
        "responses": {
          "200": { "description": "Returns {ok:true, sermons:[{id,name,updatedAt,count}]}." }
        }
      },
      "post": {
        "summary": "Create and save a sermon list",
        "description": "Parses free text for Bible references (same scanner as the share flow) and optionally saves the result as a named sermon. Check invalidRefs — references listed there were NOT added.",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "name": { "type": "string", "description": "Save under this name. Omit to stage without saving." },
                  "text": { "type": "string", "description": "Free text with references, or newline-separated refs." },
                  "refs": { "type": "string", "description": "Alias for text." },
                  "activate": { "type": "boolean", "description": "Load into the working list immediately." }
                }
              },
              "example": { "name": "Passover", "text": "Exodus 12:11\nExodus 12:21\nLeviticus 23:5" }
            }
          }
        },
        "responses": {
          "200": { "description": "Returns {ok:true, id, name, parsedCount, entries[], invalidRefs[]}." },
          "400": { "description": "No text provided." }
        }
      }
    },
    "/api/sermons/{id}": {
      "get": {
        "summary": "Read a saved sermon",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" }, "description": "Sermon id from GET /api/sermons." }
        ],
        "responses": {
          "200": { "description": "Returns {ok:true, sermon:{id,name,updatedAt,count,entries[]}}." },
          "404": { "description": "Sermon not found." }
        }
      }
    },
    "/api/sermons/{id}/activate": {
      "post": {
        "summary": "Load a saved sermon into the working list",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "merge", "in": "query", "required": false, "schema": { "type": "boolean" }, "description": "true = merge into current list; default replaces it." }
        ],
        "responses": {
          "200": { "description": "Returns {ok:true, id, merge}." },
          "404": { "description": "Sermon not found." }
        }
      }
    },
    "/api/sermons/{id}/rename": {
      "post": {
        "summary": "Rename a saved sermon",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "name", "in": "query", "required": true, "schema": { "type": "string" }, "description": "New name (or JSON body {name})." }
        ],
        "responses": {
          "200": { "description": "Returns {ok:true, id, name}." },
          "404": { "description": "Sermon not found." }
        }
      }
    },
    "/api/sermons/{id}/delete": {
      "post": {
        "summary": "Delete a saved sermon",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }
        ],
        "responses": {
          "200": { "description": "Returns {ok:true, deleted:id}." },
          "404": { "description": "Sermon not found." }
        }
      }
    },
    "/api/sermons/{id}.{format}": {
      "get": {
        "summary": "Export a sermon",
        "parameters": [
          { "name": "id", "in": "path", "required": true, "schema": { "type": "string" } },
          { "name": "format", "in": "path", "required": true, "schema": { "type": "string", "enum": ["json", "txt", "md", "csv"] } }
        ],
        "responses": {
          "200": { "description": "The sermon in the requested format." },
          "404": { "description": "Sermon not found." }
        }
      }
    },
    "/api/sermons/current": {
      "get": {
        "summary": "Working sermon list state",
        "description": "The list the presenter steps through (next/prev).",
        "responses": {
          "200": { "description": "Returns {ok:true, index, total, isLive, entries[]}." }
        }
      }
    },
    "/api/sermons/current/next": {
      "post": {
        "summary": "Step to next sermon verse (goes live)",
        "description": "Advances the working list and puts the verse on air. Stops at the end — never wraps.",
        "responses": {
          "200": { "description": "Returns {ok, index, total, isLive, entry}." }
        }
      }
    },
    "/api/sermons/current/prev": {
      "post": {
        "summary": "Step to previous sermon verse (goes live)",
        "description": "Steps back in the working list and puts the verse on air. Stops at the start.",
        "responses": {
          "200": { "description": "Returns {ok, index, total, isLive, entry}." }
        }
      }
    },
    "/api/recents": {
      "get": {
        "summary": "Session recent verses",
        "description": "The verses recently shown, newest first, for stage monitors and other LAN consumers. Same shape as the recents the /remote page receives, plus English text.",
        "responses": {
          "200": { "description": "Returns {ok:true, count, recents:[{citation, arabic, english, ref, bookId, chapterNum, verseNum}]}." }
        }
      }
    },
    "/stage": {
      "get": {
        "summary": "Stage monitor page",
        "description": "ProPresenter-style read-only stage monitor (HTML). Dark display-only page: LIVE/OFF AIR pill, sermon progress + clock header, huge Arabic verse (RTL) + yellow citation + English, sermon sidebar with live row highlighted and auto-scroll, recents footer strip. Polls /api/verse, /api/sermons/current and /api/recents every 5s; /events SSE for instant verse updates. No controls — directing stays on /remote.",
        "responses": {
          "200": { "description": "Returns the stage monitor HTML page (text/html)." }
        }
      }
    },
    "/api/verse/copy": {
      "get": {
        "summary": "Copy-ready verse text",
        "description": "Returns the verse formatted for copying. No ref = current live verse.",
        "parameters": [
          { "name": "ref", "in": "query", "required": false, "schema": { "type": "string" }, "description": "e.g. John 3:16", "example": "John 3:16" }
        ],
        "responses": {
          "200": { "description": "Copy-ready text with citations." }
        }
      }
    },
    "/api/debug": {
      "get": {
        "summary": "Diagnostics snapshot",
        "description": "App version, device, server state, active templates, NDI feeds, error log. The first stop when something looks wrong.",
        "responses": {
          "200": { "description": "Full diagnostics JSON." }
        }
      }
    },
    "/api/export/all": {
      "get": {
        "summary": "Full backup export",
        "description": "Saved sermons, working list, templates, theme, recents — everything needed to restore. Feed the output to POST /api/import/backup.",
        "responses": {
          "200": { "description": "Backup JSON {ok, version, savedSermons[], working[], templates[], theme, recents[]}." }
        }
      }
    },
    "/api/import/backup": {
      "post": {
        "summary": "Restore from a backup",
        "parameters": [
          { "name": "dryRun", "in": "query", "required": false, "schema": { "type": "boolean" }, "description": "true = validate only, change nothing." }
        ],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "type": "object", "description": "Backup JSON from GET /api/export/all." }
            }
          }
        },
        "responses": {
          "200": { "description": "Returns {ok:true, applied, sermons, templates, recents, themeId, invalid[]}." },
          "400": { "description": "Empty body." }
        }
      }
    }
  }
}
"""

    const val API_DOCS_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="dark">
<title>Bible NDI — API Test Page</title>
<style>
  :root {
    --bg: #0f1117;
    --card: #1a1e2c;
    --card2: #222738;
    --border: #2e3650;
    --text: #e8eaf2;
    --muted: #9aa3c0;
    --accent: #d4af37;
    --accent-dim: #8a742a;
    --green: #34d399;
    --blue: #60a5fa;
    --red: #f87171;
    --orange: #fb923c;
  }
  * { box-sizing: border-box; }
  body {
    font-family: system-ui, -apple-system, sans-serif;
    margin: 0; padding: 0;
    background: var(--bg); color: var(--text);
    line-height: 1.5;
  }
  header {
    background: linear-gradient(135deg, #0b132b 0%, #1a1e2c 100%);
    border-bottom: 2px solid var(--accent-dim);
    padding: 20px 24px;
  }
  header h1 { margin: 0 0 4px; font-size: 22px; }
  header h1 .gold { color: var(--accent); }
  header .sub { color: var(--muted); font-size: 13px; }
  header .baseurl { margin-top: 8px; font-size: 12px; font-family: ui-monospace, monospace; color: var(--green); }
  main { max-width: 960px; margin: 0 auto; padding: 20px 16px 60px; }
  details.section {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 12px;
    margin-bottom: 12px;
    overflow: hidden;
  }
  details.section > summary {
    padding: 14px 18px;
    cursor: pointer;
    font-weight: 700;
    font-size: 15px;
    list-style: none;
    display: flex;
    align-items: center;
    gap: 10px;
  }
  details.section > summary::-webkit-details-marker { display: none; }
  details.section > summary .chev { color: var(--accent); font-size: 12px; }
  details.section > .body { padding: 0 18px 18px; font-size: 14px; color: var(--muted); }
  details.section > .body p { margin: 8px 0; }
  details.section > .body code {
    background: rgba(212,175,55,0.12);
    color: var(--accent);
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 13px;
  }
  details.section > .body ul { margin: 8px 0; padding-left: 20px; }
  details.section > .body li { margin: 6px 0; }
  .endpoint {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 12px;
    margin-bottom: 10px;
    overflow: hidden;
  }
  .endpoint > .head {
    padding: 12px 16px;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .endpoint > .head:hover { background: var(--card2); }
  .method {
    font-size: 11px; font-weight: 800;
    padding: 4px 10px; border-radius: 6px;
    text-transform: uppercase; letter-spacing: 0.5px;
    min-width: 52px; text-align: center;
  }
  .method.get { background: rgba(96,165,250,0.18); color: var(--blue); }
  .method.post { background: rgba(52,211,153,0.15); color: var(--green); }
  .endpoint .path { font-family: ui-monospace, monospace; font-size: 14px; flex: 1; }
  .endpoint .summary { font-size: 13px; color: var(--muted); }
  .endpoint > .detail { display: none; padding: 0 16px 16px; border-top: 1px solid var(--border); }
  .endpoint.open > .detail { display: block; }
  .endpoint .desc { font-size: 13px; color: var(--muted); margin: 12px 0; }
  .param { margin: 10px 0; }
  .param label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 4px; }
  .param label .req { color: var(--red); font-size: 11px; }
  .param label .in { color: var(--muted); font-weight: 400; font-size: 11px; }
  .param .pdesc { font-size: 12px; color: var(--muted); margin: 2px 0 4px; }
  .param input[type=text], .param textarea {
    width: 100%; padding: 8px 10px;
    background: var(--bg); color: var(--text);
    border: 1px solid var(--border); border-radius: 8px;
    font-size: 13px; font-family: ui-monospace, monospace;
  }
  .param textarea { min-height: 90px; resize: vertical; }
  .btn {
    display: inline-block;
    padding: 10px 22px; margin: 12px 0 0;
    background: var(--accent); color: #0b132b;
    border: none; border-radius: 8px;
    font-size: 14px; font-weight: 700; cursor: pointer;
  }
  .btn:hover { filter: brightness(1.1); }
  .btn:active { transform: scale(0.98); }
  .btn.ghost {
    background: transparent; color: var(--accent);
    border: 1px solid var(--accent-dim);
    padding: 6px 14px; font-size: 12px; margin: 4px 0;
  }
  .resp {
    margin-top: 12px;
    background: #0a0d14;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px;
    font-family: ui-monospace, monospace;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 400px;
    overflow: auto;
    display: none;
  }
  .resp.show { display: block; }
  .resp .status { color: var(--green); font-weight: 700; margin-bottom: 6px; }
  .resp .status.err { color: var(--red); }
  .example { margin: 12px 0; }
  .example h4 { margin: 0 0 6px; font-size: 14px; color: var(--text); }
  .example pre {
    background: #0a0d14;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px;
    font-size: 12px;
    overflow-x: auto;
    margin: 6px 0;
  }
  .loading { text-align: center; padding: 40px; color: var(--muted); }
  .err-box {
    background: rgba(248,113,113,0.1);
    border: 1px solid var(--red);
    border-radius: 8px;
    padding: 12px;
    margin: 12px 0;
    font-size: 13px;
  }
  h2.sec-title { font-size: 18px; margin: 28px 0 12px; color: var(--text); }
  h2.sec-title .gold { color: var(--accent); }
</style>
</head>
<body>
<header>
  <h1><span class="gold">◆</span> Bible NDI — API Test Page</h1>
  <div class="sub">Try every HTTP endpoint live from your browser. No app install, no curl, no Postman.</div>
  <div class="baseurl" id="baseurl"></div>
</header>
<main>
  <details class="section" open>
    <summary><span class="chev">▼</span> ❓ Help — what is this page?</summary>
    <div class="body">
      <p>This page talks directly to the Bible NDI app running on your tablet. Every section below is a real API endpoint — expand it, fill in the fields, hit <b>Execute</b>, and see the live response.</p>
      <p>The endpoint list comes from <code>/api/openapi.json</code> (a machine-readable spec). This page reads that spec and builds the forms automatically, so the docs never go stale.</p>
      <ul>
        <li><b>GET</b> endpoints only read data — safe to try anytime.</li>
        <li><b>POST</b> endpoints change things (go live, clear, save, delete) — they act on the real app.</li>
        <li>Responses appear in the dark box under each endpoint. <code>ok:true</code> means success.</li>
      </ul>
    </div>
  </details>

  <details class="section">
    <summary><span class="chev">▼</span> 💡 Tips</summary>
    <div class="body">
      <ul>
        <li><b>Reference format:</b> use <code>?ref=John+3:16</code> (plus for space). Both English and Arabic book names work: <code>?ref=يوحنا+3:16</code>.</li>
        <li><b>Always check <code>invalidRefs</code></b> after creating a sermon — any reference listed there was <i>not</i> added. Fix the spelling and re-submit.</li>
        <li><b>POST vs GET:</b> creating, triggering, clearing, and deleting need POST. If you get "Use POST...", switch the method.</li>
        <li><b>Sermon IDs:</b> run <code>GET /api/sermons</code> first, copy an <code>id</code>, then paste it into the activate/rename/delete/export endpoints.</li>
        <li><b>Next/prev stop at the ends</b> — they never wrap around. That is deliberate.</li>
        <li><b>Clear is per-feed:</b> after <code>POST /api/clear</code>, Full Show keeps its card (Clear verse only) while Lower Third goes transparent (Clear all layers).</li>
        <li><b>Backup before big changes:</b> <code>GET /api/export/all</code> → save the JSON → restore later with <code>POST /api/import/backup?dryRun=1</code> first to validate.</li>
        <li><b>JSON bodies:</b> the request-body box must be valid JSON. The example is pre-filled — edit it, don't delete the braces.</li>
      </ul>
    </div>
  </details>

  <details class="section">
    <summary><span class="chev">▼</span> 📋 Examples — copy, paste, run</summary>
    <div class="body">
      <div class="example">
        <h4>1. Create a sermon list (and save it)</h4>
        <pre id="ex1">curl -X POST http://TABLET-IP:8080/api/sermons \
  -H "Content-Type: application/json" \
  -d '{"name":"Passover","text":"Exodus 12:11\nExodus 12:21\nLeviticus 23:5"}'</pre>
        <button class="btn ghost" onclick="copyEx('ex1')">Copy</button>
      </div>
      <div class="example">
        <h4>2. Put a verse on air</h4>
        <pre id="ex2">curl -X POST "http://TABLET-IP:8080/api/trigger?ref=John+3:16"</pre>
        <button class="btn ghost" onclick="copyEx('ex2')">Copy</button>
      </div>
      <div class="example">
        <h4>3. Clear the verse</h4>
        <pre id="ex3">curl -X POST http://TABLET-IP:8080/api/clear</pre>
        <button class="btn ghost" onclick="copyEx('ex3')">Copy</button>
      </div>
      <div class="example">
        <h4>4. Full backup (save the output!)</h4>
        <pre id="ex4">curl http://TABLET-IP:8080/api/export/all -o backup.json</pre>
        <button class="btn ghost" onclick="copyEx('ex4')">Copy</button>
      </div>
      <p style="margin-top:10px">Replace <code>TABLET-IP</code> with the tablet's address (shown at the top of this page).</p>
    </div>
  </details>

  <h2 class="sec-title"><span class="gold">◆</span> Endpoints</h2>
  <div id="endpoints"><div class="loading">Loading API spec…</div></div>
</main>

<script>
(function() {
  'use strict';

  // Show the base URL at the top
  document.getElementById('baseurl').textContent = 'Server: ' + location.protocol + '//' + location.host;

  window.copyEx = function(id) {
    var t = document.getElementById(id).textContent;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(t);
    }
  };

  var cardSeq = 0;

  fetch('/api/openapi.json')
    .then(function(r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    })
    .then(function(spec) { renderEndpoints(spec); })
    .catch(function(e) {
      document.getElementById('endpoints').innerHTML =
        '<div class="err-box">Could not load /api/openapi.json: ' + escHtml(String(e)) + '</div>';
    });

  function escHtml(s) {
    return String(s)
      .split('&').join('&amp;')
      .split('<').join('&lt;')
      .split('>').join('&gt;')
      .split('"').join('&quot;');
  }

  function renderEndpoints(spec) {
    var box = document.getElementById('endpoints');
    box.innerHTML = '';
    var paths = spec.paths || {};
    Object.keys(paths).forEach(function(path) {
      var methods = paths[path];
      Object.keys(methods).forEach(function(method) {
        box.appendChild(buildCard(path, method, methods[method]));
      });
    });
  }

  function buildCard(path, method, op) {
    var id = 'ep' + (cardSeq++);
    var card = document.createElement('div');
    card.className = 'endpoint';

    var head = document.createElement('div');
    head.className = 'head';
    head.innerHTML =
      '<span class="method ' + method + '">' + method + '</span>' +
      '<span class="path">' + escHtml(path) + '</span>' +
      '<span class="summary">' + escHtml(op.summary || '') + '</span>';
    head.onclick = function() { card.classList.toggle('open'); };

    var detail = document.createElement('div');
    detail.className = 'detail';

    var html = '';
    if (op.description) {
      html += '<div class="desc">' + escHtml(op.description) + '</div>';
    }

    var params = op.parameters || [];
    params.forEach(function(p) {
      var req = p.required ? ' <span class="req">required</span>' : '';
      html += '<div class="param">' +
        '<label>' + escHtml(p.name) + req + ' <span class="in">(' + escHtml(p.in) + ')</span></label>';
      if (p.description) html += '<div class="pdesc">' + escHtml(p.description) + '</div>';
      var val = '';
      if (p.example !== undefined) val = p.example;
      else if (p.schema && p.schema.example !== undefined) val = p.schema.example;
      else if (p.schema && p.schema.default !== undefined) val = p.schema.default;
      html += '<input type="text" id="' + id + '-p-' + escHtml(p.name) + '" value="' + escHtml(val) + '"' +
        (p.in === 'path' ? '' : ' placeholder="(optional)"') + '>';
      html += '</div>';
    });

    if (op.requestBody) {
      var example = '';
      try {
        var content = op.requestBody.content || {};
        var json = content['application/json'] || {};
        if (json.example) example = JSON.stringify(json.example, null, 2);
      } catch (e) {}
      html += '<div class="param">' +
        '<label>Request body <span class="in">(JSON)</span></label>' +
        '<textarea id="' + id + '-body">' + escHtml(example) + '</textarea></div>';
    }

    html += '<button class="btn" id="' + id + '-go">Execute</button>';
    html += '<div class="resp" id="' + id + '-resp"></div>';
    detail.innerHTML = html;

    card.appendChild(head);
    card.appendChild(detail);

    detail.querySelector('#' + id + '-go').onclick = function() {
      doExecute(id, path, method, op);
    };

    return card;
  }

  function doExecute(id, path, method, op) {
    var url = path;
    var params = op.parameters || [];

    // Path params: /api/sermons/{id} -> /api/sermons/abc123
    params.forEach(function(p) {
      if (p.in === 'path') {
        var el = document.getElementById(id + '-p-' + p.name);
        var v = el ? el.value : '';
        url = url.split('{' + p.name + '}').join(encodeURIComponent(v));
      }
    });

    // Query params
    var qs = [];
    params.forEach(function(p) {
      if (p.in === 'query') {
        var el = document.getElementById(id + '-p-' + p.name);
        var v = el ? el.value : '';
        if (v !== '') qs.push(encodeURIComponent(p.name) + '=' + encodeURIComponent(v));
      }
    });
    if (qs.length) url += '?' + qs.join('&');

    // Body for POST/PUT
    var body = null;
    var bodyEl = document.getElementById(id + '-body');
    if (bodyEl && (method === 'post' || method === 'put' || method === 'patch')) {
      body = bodyEl.value;
    }

    var respBox = document.getElementById(id + '-resp');
    respBox.className = 'resp show';
    respBox.innerHTML = '<div class="status">… sending ' + method.toUpperCase() + ' ' + escHtml(url) + '</div>';

    var opts = { method: method.toUpperCase() };
    if (body !== null && body !== '') {
      opts.headers = { 'Content-Type': 'application/json' };
      opts.body = body;
    }

    fetch(url, opts).then(function(r) {
      return r.text().then(function(t) {
        var pretty = t;
        try { pretty = JSON.stringify(JSON.parse(t), null, 2); } catch (e) {}
        var cls = (r.status >= 200 && r.status < 300) ? 'status' : 'status err';
        respBox.innerHTML = '<div class="' + cls + '">HTTP ' + r.status + '</div>' + escHtml(pretty);
      });
    }).catch(function(e) {
      respBox.innerHTML = '<div class="status err">Request failed</div>' + escHtml(String(e));
    });
  }
})();
</script>
</body>
</html>

"""
}
