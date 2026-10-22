#!/usr/bin/env node
// Reads app diagnostics (diagEvents) for debugging, using your `firebase login`.
//
//   node tools/diagnostics/read-diagnostics.js                 last 6 hours, all apps
//   node tools/diagnostics/read-diagnostics.js --since 2d      last 2 days (m, h, d)
//   node tools/diagnostics/read-diagnostics.js --level WARN    WARN and ERROR only
//   node tools/diagnostics/read-diagnostics.js --app shield    one app (shield / watchdog)
//   node tools/diagnostics/read-diagnostics.js --uid <uid>     one account
//   node tools/diagnostics/read-diagnostics.js --tz Asia/Qatar times in that zone (default: this PC's)
//   node tools/diagnostics/read-diagnostics.js --json          raw rows
const fs = require("fs"), os = require("os"), path = require("path");

const PROJECT = "watch-dog-13d09";
const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback;
};
const has = (name) => args.includes(`--${name}`);

function parseSince(s) {
  const m = /^(\d+)([mhd])$/.exec(s);
  if (!m) throw new Error(`--since takes e.g. 30m, 6h or 2d (got ${s})`);
  return Number(m[1]) * { m: 60e3, h: 3600e3, d: 86400e3 }[m[2]];
}

async function token() {
  const cfg = JSON.parse(fs.readFileSync(path.join(os.homedir(), ".config/configstore/firebase-tools.json"), "utf8"));
  const body = new URLSearchParams({
    refresh_token: cfg.tokens.refresh_token,
    // The Firebase CLI's public OAuth client.
    client_id: "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com",
    client_secret: "j9iVZfS8kkCEFUPaAeJV0sAi",
    grant_type: "refresh_token",
  });
  const j = await (await fetch("https://oauth2.googleapis.com/token", { method: "POST", body })).json();
  if (!j.access_token) throw new Error("Not logged in: run `firebase login` first.");
  return j.access_token;
}

const plain = (v) => v == null ? v : "stringValue" in v ? v.stringValue : "integerValue" in v ? Number(v.integerValue)
  : "timestampValue" in v ? v.timestampValue : "booleanValue" in v ? v.booleanValue : null;

async function main() {
  const since = Date.now() - parseSince(opt("since", "6h"));
  const level = (opt("level", "INFO") || "INFO").toUpperCase();
  const levels = { INFO: ["INFO", "WARN", "ERROR"], WARN: ["WARN", "ERROR"], ERROR: ["ERROR"] }[level];
  const app = opt("app", "");
  const uid = opt("uid", "");
  const tz = opt("tz", Intl.DateTimeFormat().resolvedOptions().timeZone);

  const filters = [{ fieldFilter: { field: { fieldPath: "atMillis" }, op: "GREATER_THAN_OR_EQUAL", value: { integerValue: String(since) } } }];
  const query = {
    structuredQuery: {
      from: [{ collectionId: "diagEvents" }],
      where: filters.length === 1 ? filters[0] : { compositeFilter: { op: "AND", filters } },
      orderBy: [{ field: { fieldPath: "atMillis" }, direction: "ASCENDING" }],
      limit: 2000,
    },
  };
  const res = await fetch(`https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents:runQuery`, {
    method: "POST",
    headers: { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" },
    body: JSON.stringify(query),
  });
  const out = await res.json();
  if (!Array.isArray(out)) throw new Error(JSON.stringify(out).slice(0, 400));
  const rows = out.filter((r) => r.document).map((r) => {
    const f = r.document.fields || {};
    return Object.fromEntries(Object.entries(f).map(([k, v]) => [k, plain(v)]));
  }).filter((r) => levels.includes(r.level))
    .filter((r) => !app || String(r.app).includes(app))
    .filter((r) => !uid || r.uid === uid);

  if (has("json")) { console.log(JSON.stringify(rows, null, 1)); return; }
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: tz, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
  });
  console.log(`${rows.length} event(s) since ${fmt.format(since)} (${tz})`);
  for (const r of rows) {
    const appName = String(r.app || "").split(".").pop();
    console.log(
      `${fmt.format(r.atMillis)}  ${String(r.level).padEnd(5)}  ${appName} ${r.version} ${r.model}  ` +
      `uid=${String(r.uid).slice(0, 6)}  [${r.tag}] ${r.message}${r.exception ? `  !! ${r.exception}` : ""}`,
    );
  }
}

main().catch((e) => { console.error(e.message); process.exit(1); });
