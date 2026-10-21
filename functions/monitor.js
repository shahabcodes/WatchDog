/**
 * Tamper alerts. A parental-control app can't stop every way of switching it off
 * on a child's phone, so the parent must at least be told (each alert can be
 * switched off per child in Watch Dog → Protection → Alerts, settings/alerts):
 *
 *  - watchChildGuard: when Shield's plaintext `device.guard` flags (accessibility
 *    service, usage access, device admin) flip from on to off, push the parents.
 *    It also stamps each heartbeat with SERVER time in childMonitor/{childId}
 *    (server-only), so a child moving the phone's clock can't fake liveness.
 *  - checkStaleChildren: every 15 minutes, alert once when a supervised phone
 *    hasn't checked in for STALE_MS (off, no signal, or Shield removed), and once
 *    when the phone has been on a different time zone than the child's home zone
 *    for TZ_PROMPT_MS (travelling — the parent may want schedules to follow).
 */
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { _internal: pairing } = require("./pairing");

const STALE_MS = 3 * 60 * 60 * 1000;
const TZ_PROMPT_MS = 2 * 60 * 60 * 1000;
// Each flag is true while that protection is intact; a true → false flip alerts.
const GUARD_LABELS = {
  accessibility: "app blocking (accessibility) was turned off",
  usageAccess: "screen-time tracking (usage access) was turned off",
  admin: "uninstall protection (device admin) was turned off",
  clockOk: "the date or time was changed by hand",
  gpsGenuine: "a fake-location (mock GPS) app is being used",
  adbOff: "USB debugging was turned on (a computer can then switch Shield off)",
  parentKeysOk: "a parent's phone key changed and is waiting for approval on the child's phone (if that wasn't you, someone may have your Watch Dog login)",
};

// Protections that stay off until someone turns them back on (the others are
// one-off events). While any is off, parents get a reminder once a day, also
// when it was never on (a true -> false flip is alerted straight away).
const PERSISTENT_GUARDS = {
  admin: "uninstall protection (device admin)",
  accessibility: "app blocking (accessibility)",
  usageAccess: "screen-time tracking (usage access)",
};
const REMIND_MS = 24 * 60 * 60 * 1000;

// Which parent alert switch (settings/alerts) controls each guard flag.
const GUARD_SWITCH = {
  accessibility: "protectionOff",
  usageAccess: "protectionOff",
  admin: "protectionOff",
  clockOk: "clockChanged",
  gpsGenuine: "fakeGps",
  adbOff: "protectionOff",
  parentKeysOk: "protectionOff",
};

/** The child's alert switches (settings/alerts); every alert is on unless switched off. */
async function alertSwitches(childId) {
  const snap = await db().doc(`children/${childId}/settings/alerts`).get().catch(() => null);
  const d = (snap && snap.exists && snap.data()) || {};
  const on = (k) => d[k] !== false;
  return {
    protectionOff: on("protectionOff"),
    clockChanged: on("clockChanged"),
    fakeGps: on("fakeGps"),
    travel: on("travel"),
    noCheckIn: on("noCheckIn"),
  };
}

/** Whether the parent has uninstall protection switched on (settings/protection). */
async function uninstallWanted(childId) {
  const snap = await db().doc(`children/${childId}/settings/protection`).get().catch(() => null);
  return !!(snap && snap.exists && snap.get("uninstallBlocked") === true);
}

/** Current UTC offset of an IANA zone in minutes, or null if unknown. */
function offsetMinutes(zone) {
  try {
    const part = new Intl.DateTimeFormat("en-US", { timeZone: zone, timeZoneName: "longOffset" })
      .formatToParts(new Date()).find((x) => x.type === "timeZoneName");
    const m = /GMT([+-])(\d{2}):(\d{2})/.exec(part ? part.value : "");
    if (!m) return part && part.value === "GMT" ? 0 : null;
    return (m[1] === "-" ? -1 : 1) * (Number(m[2]) * 60 + Number(m[3]));
  } catch (e) {
    return null;
  }
}

/** A short, readable zone name: "Asia/Dubai" → "Dubai". */
function zoneLabel(zone) {
  return String(zone || "").split("/").pop().replace(/_/g, " ");
}

const db = () => getFirestore();

exports.watchChildGuard = onDocumentUpdated("children/{childId}", async (event) => {
  const before = (event.data && event.data.before && event.data.before.data()) || {};
  const after = (event.data && event.data.after && event.data.after.data()) || {};
  const parents = Array.isArray(after.linkedParents) ? after.linkedParents : [];
  if (parents.length === 0) return;
  const childId = event.params.childId;
  const childName = (after.profile && after.profile.name) || "your child";

  const beforeDevice = before.device || {};
  const afterDevice = after.device || {};
  const work = [];
  const beforeGuard = beforeDevice.guard || {};
  const afterGuard = afterDevice.guard || {};
  // Protections switched off since the last report.
  // Shield drops device admin itself when the parent switches uninstall
  // protection off, so "admin" only counts while protection is wanted.
  const wantAdmin = afterGuard.admin === false ? await uninstallWanted(childId) : true;
  const flipped = Object.keys(GUARD_LABELS)
    .filter((k) => beforeGuard[k] === true && afterGuard[k] === false)
    .filter((k) => k !== "admin" || wantAdmin);

  // Liveness by server time, on every heartbeat.
  if (afterDevice.lastSeenMillis !== beforeDevice.lastSeenMillis) {
    const monitorRef = db().doc(`childMonitor/${childId}`);
    work.push((async () => {
      const prev = await monitorRef.get();
      const p = prev.exists ? prev.data() : {};
      if (p.staleAlerted === true && (await alertSwitches(childId)).noCheckIn) {
        await pairing.notifyParents(parents, `${childName}'s phone is back`,
          `Shield on ${childName}'s phone is checking in again.`);
      }
      // Time zone: the phone's zone vs the child's home zone (by current offset,
      // so two names for the same offset don't count as travelling).
      const guard = afterDevice.guard || {};
      const tz = typeof guard.tz === "string" ? guard.tz : "";
      const home = typeof guard.homeTz === "string" ? guard.homeTz : "";
      const tzOff = tz ? offsetMinutes(tz) : null;
      const homeOff = home ? offsetMinutes(home) : null;
      const away = tzOff !== null && homeOff !== null && tzOff !== homeOff;
      const sameAway = away && p.awayZone === tz;
      // Protections still off: remind once a day (straight away the first time).
      // A flip in this same report is already alerted below, so it isn't repeated.
      const missing = Object.keys(PERSISTENT_GUARDS)
        .filter((k) => afterGuard[k] === false)
        .filter((k) => k !== "admin" || wantAdmin);
      let protectionRemindedAt = missing.length > 0 ? (p.protectionRemindedAt || null) : null;
      const lastReminder = protectionRemindedAt ? protectionRemindedAt.toMillis() : 0;
      if (missing.length > 0 && Date.now() - lastReminder >= REMIND_MS) {
        protectionRemindedAt = Timestamp.now();
        const alreadyAlerted = missing.every((k) => flipped.includes(k));
        if (!alreadyAlerted && (await alertSwitches(childId)).protectionOff) {
          await pairing.notifyParents(
            parents,
            `${childName}'s phone isn't fully protected`,
            `On ${childName}'s phone, ${missing.map((k) => PERSISTENT_GUARDS[k]).join(", ")} ` +
              `${missing.length === 1 ? "is" : "are"} off. Open Shield on that phone → Protection setup to turn ` +
              `${missing.length === 1 ? "it" : "them"} on.`,
          );
        }
      }
      await monitorRef.set({
        protectionRemindedAt,
        lastHeartbeatAt: Timestamp.now(),
        staleAlerted: false,
        parents,
        childName,
        awayZone: away ? tz : null,
        homeZone: home || null,
        awaySince: sameAway && p.awaySince ? p.awaySince : (away ? Timestamp.now() : null),
        tzPrompted: sameAway ? p.tzPrompted === true : false,
      });
    })());
  }

  const switches = flipped.length > 0 ? await alertSwitches(childId) : null;
  const turnedOff = flipped
    .filter((k) => switches[GUARD_SWITCH[k]])
    .map((k) => GUARD_LABELS[k]);
  if (turnedOff.length > 0) {
    work.push(pairing.notifyParents(
      parents,
      `Shield protection changed on ${childName}'s phone`,
      `On ${childName}'s phone, ${turnedOff.join("; ")}. Open Watch Dog to check.`,
    ));
  }
  await Promise.all(work.map((p) => p.catch((e) => console.error("watchChildGuard", e))));
});

exports.checkStaleChildren = onSchedule("every 15 minutes", async () => {
  await promptTravellers().catch((e) => console.error("promptTravellers", e));
  const cutoff = Timestamp.fromMillis(Date.now() - STALE_MS);
  // Single-field query (no composite index needed); filter the flag here.
  const stale = await db().collection("childMonitor").where("lastHeartbeatAt", "<", cutoff).get();
  for (const doc of stale.docs) {
    if (doc.get("staleAlerted") === true) continue;
    const childSnap = await db().doc(`children/${doc.id}`).get();
    const parents = (childSnap.exists && childSnap.get("linkedParents")) || [];
    await doc.ref.update({ staleAlerted: true });
    if (parents.length === 0) continue;
    if (!(await alertSwitches(doc.id)).noCheckIn) continue;
    const childName = (childSnap.get("profile") || {}).name || "your child";
    await pairing.notifyParents(
      parents,
      `No contact with ${childName}'s phone`,
      `Shield on ${childName}'s phone hasn't checked in for 3 hours. It may be switched off or out of signal, or Shield may have been removed.`,
    ).catch((e) => console.error("checkStaleChildren", e));
  }
});

/** Once per trip: the phone has been on another zone than home for TZ_PROMPT_MS. */
async function promptTravellers() {
  const cutoff = Timestamp.fromMillis(Date.now() - TZ_PROMPT_MS);
  const away = await db().collection("childMonitor").where("awaySince", "<", cutoff).get();
  for (const doc of away.docs) {
    const d = doc.data();
    if (d.tzPrompted === true || !d.awayZone) continue;
    await doc.ref.update({ tzPrompted: true });
    const childSnap = await db().doc(`children/${doc.id}`).get();
    const parents = (childSnap.exists && childSnap.get("linkedParents")) || [];
    if (parents.length === 0) continue;
    if (!(await alertSwitches(doc.id)).travel) continue;
    const childName = (childSnap.get("profile") || {}).name || "your child";
    await pairing.notifyParents(
      parents,
      `${childName}'s phone is on ${zoneLabel(d.awayZone)} time`,
      `Bedtime and other schedules still follow ${zoneLabel(d.homeZone)} time. ` +
        `If ${childName} is travelling, open Watch Dog → Focus to switch them to ${zoneLabel(d.awayZone)}.`,
    ).catch((e) => console.error("promptTravellers notify", e));
  }
}
