/**
 * Tamper alerts. A parental-control app can't stop every way of switching it off
 * on a child's phone, so the parent must at least be told:
 *
 *  - watchChildGuard: when Shield's plaintext `device.guard` flags (accessibility
 *    service, usage access, device admin) flip from on to off, push the parents.
 *    It also stamps each heartbeat with SERVER time in childMonitor/{childId}
 *    (server-only), so a child moving the phone's clock can't fake liveness.
 *  - checkStaleChildren: every 15 minutes, alert once when a supervised phone
 *    hasn't checked in for STALE_MS (off, no signal, or Shield removed).
 */
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { _internal: pairing } = require("./pairing");

const STALE_MS = 3 * 60 * 60 * 1000;
const GUARD_LABELS = {
  accessibility: "app blocking (accessibility) was turned off",
  usageAccess: "screen-time tracking (usage access) was turned off",
  admin: "uninstall protection (device admin) was turned off",
};

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

  // Liveness by server time, on every heartbeat.
  if (afterDevice.lastSeenMillis !== beforeDevice.lastSeenMillis) {
    const monitorRef = db().doc(`childMonitor/${childId}`);
    work.push((async () => {
      const prev = await monitorRef.get();
      if (prev.exists && prev.get("staleAlerted") === true) {
        await pairing.notifyParents(parents, `${childName}'s phone is back`,
          `Shield on ${childName}'s phone is checking in again.`);
      }
      await monitorRef.set({ lastHeartbeatAt: Timestamp.now(), staleAlerted: false, parents, childName });
    })());
  }

  // Protections switched off since the last report.
  const beforeGuard = beforeDevice.guard || {};
  const afterGuard = afterDevice.guard || {};
  const turnedOff = Object.keys(GUARD_LABELS)
    .filter((k) => beforeGuard[k] === true && afterGuard[k] === false)
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
  const cutoff = Timestamp.fromMillis(Date.now() - STALE_MS);
  // Single-field query (no composite index needed); filter the flag here.
  const stale = await db().collection("childMonitor").where("lastHeartbeatAt", "<", cutoff).get();
  for (const doc of stale.docs) {
    if (doc.get("staleAlerted") === true) continue;
    const childSnap = await db().doc(`children/${doc.id}`).get();
    const parents = (childSnap.exists && childSnap.get("linkedParents")) || [];
    await doc.ref.update({ staleAlerted: true });
    if (parents.length === 0) continue;
    const childName = (childSnap.get("profile") || {}).name || "your child";
    await pairing.notifyParents(
      parents,
      `No contact with ${childName}'s phone`,
      `Shield on ${childName}'s phone hasn't checked in for 3 hours. It may be switched off or out of signal, or Shield may have been removed.`,
    ).catch((e) => console.error("checkStaleChildren", e));
  }
});
