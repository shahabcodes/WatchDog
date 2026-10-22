const { onDocumentCreated, onDocumentDeleted, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
// onCall with a diagnostics record per call (trace.js).
const { onCall } = require("./trace");
const { setGlobalOptions } = require("firebase-functions/v2");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getStorage } = require("firebase-admin/storage");

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

const db = getFirestore();
const pairing = require("./pairing");
const pairingInternal = pairing._internal;

// Links and web addresses in child-supplied text: a push that says "sign in at
// <link>" would look like it came from Watch Dog.
const LINK_RE = /(https?:\/\/|www\.)\S+|\S+\.(com|net|org|io|app|link|xyz|me|co|info|site|online|ly|gl|page|dev|example)(\/\S*)?(?=\s|$)/gi;

/** Child-supplied text going into a push: one line, no links, at most [max] characters. */
function clip(value, max, fallback) {
  const text = String(value || "").replace(LINK_RE, "").replace(/\s+/g, " ").trim();
  if (!text) return fallback;
  return text.length > max ? text.slice(0, max - 1) + "…" : text;
}
/**
 * Whether one more push of [kind] for [childId] fits this hour's budget. Pushes
 * are caused by the child's own writes, so a tampered phone can't flood parents.
 */
async function pushAllowed(kind, childId, max) {
  try {
    await pairingInternal.rateLimit(`push_${kind}_${childId}`, max);
    return true;
  } catch (e) {
    console.warn(`push budget used up: ${kind} ${childId}`);
    return false;
  }
}
const STORAGE_BUCKET = "watch-dog-13d09.firebasestorage.app";
// Reconnect is for a phone where Shield was reinstalled, so the old install must
// have gone quiet: a phone that checked in this recently is still running Shield.
const RECONNECT_QUIET_MS = 10 * 60 * 1000;
// Events older than this (e.g. recorded offline, synced late) are recorded in
// history but don't trigger a push — the alert would be stale/misleading.
const STALE_EVENT_MS = 3 * 60 * 1000;

/**
 * When a screenshot doc is deleted (the parent downloaded it locally), delete the
 * backing Storage object too, so nothing is kept server-side. Runs with admin
 * privileges, independent of client Storage rules.
 *
 * Firestore trigger: children/{childId}/screenshots/{shotId}
 */
exports.cleanupScreenshot = onDocumentDeleted(
  "children/{childId}/screenshots/{shotId}",
  async (event) => {
    const data = (event.data && event.data.data()) || {};
    // The doc is client-written, so only ever delete inside this child's own
    // screenshots folder: a crafted storagePath must not reach other objects.
    const prefix = `screenshots/${event.params.childId}/`;
    let path = String(data.storagePath || "").replace(/^\/+/, "");
    if (!path.startsWith(prefix) || path.includes("..") || path.slice(prefix.length).includes("/")) {
      const millis = Number(data.capturedAtMillis);
      if (!Number.isSafeInteger(millis) || millis <= 0) return;
      path = `${prefix}${millis}.png`;
    }
    try {
      await getStorage().bucket(STORAGE_BUCKET).file(path).delete({ ignoreNotFound: true });
    } catch (e) {
      // Best-effort; object may already be gone.
    }
  },
);

/**
 * When the child reports a geofence enter/exit event, push a notification to
 * every linked parent's devices — honouring each place's enter/exit setting.
 *
 * Firestore trigger: children/{childId}/geofenceEvents/{eventId}
 */
exports.notifyGeofenceEvent = onDocumentCreated(
  "children/{childId}/geofenceEvents/{eventId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data() || {};
    const childId = event.params.childId;
    const transition = data.transition === "EXIT" ? "EXIT" : "ENTER";
    const geofenceId = data.geofenceId || "";
    const eventId = event.params.eventId;

    // Skip the push for a stale event — e.g. a transition the child recorded
    // while offline that only synced later. It still lands in the in-app history
    // (this function doesn't touch the doc) and the trail shows on the map; we
    // just don't send a late, misleading "arrived/left" notification now.
    const occurredMs = Number(data.timestampMillis) || 0;
    if (occurredMs && Date.now() - occurredMs > STALE_EVENT_MS) return;
    if (!(await pushAllowed("geofence", childId, 60))) return;

    // Child doc → linked parents + child name.
    const childSnap = await db.doc(`children/${childId}`).get();
    if (!childSnap.exists) return;
    const child = childSnap.data() || {};
    const parents = Array.isArray(child.linkedParents) ? child.linkedParents : [];
    if (parents.length === 0) return;
    const childName =
      clip(child.profile && child.profile.name, 60, "Your child");

    // Geofence → honour its trigger (ENTER / EXIT / ENTER_EXIT) and active flag.
    // These stay plaintext; the place name/coordinates are E2E-encrypted, so this
    // function can't read them — the push is sent DATA-ONLY and the parent app
    // decrypts the event to build the notification text on-device.
    if (geofenceId) {
      const gfSnap = await db.doc(`children/${childId}/geofences/${geofenceId}`).get();
      if (gfSnap.exists) {
        const gf = gfSnap.data() || {};
        if (gf.active === false) return;
        const trig = gf.trigger || "ENTER_EXIT";
        if (trig === "ENTER" && transition !== "ENTER") return;
        if (trig === "EXIT" && transition !== "EXIT") return;
      }
    }

    // Gather FCM tokens across all linked parents.
    const tokenDocs = [];
    for (const parentId of parents) {
      const toks = await db.collection(`users/${parentId}/fcmTokens`).get();
      toks.forEach((d) => tokenDocs.push({ parentId, token: d.id }));
    }
    if (tokenDocs.length === 0) return;

    const message = {
      tokens: tokenDocs.map((t) => t.token),
      // Data-only (no `notification`) so the app's handler always runs — including
      // in the background — and can decrypt the place name before showing it.
      data: {
        type: "geofence",
        childId,
        geofenceId,
        eventId,
        transition,
        childName,
      },
      android: { priority: "high" },
    };

    const resp = await getMessaging().sendEachForMulticast(message);
    await require("./trace").serverDiag(resp.failureCount ? "WARN" : "INFO", "push",
      `${process.env.FUNCTION_TARGET || "push"} sent=${resp.successCount} failed=${resp.failureCount}`, event.params.childId);

    // Prune tokens that are no longer valid.
    const deletions = [];
    resp.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument"
        ) {
          const t = tokenDocs[i];
          deletions.push(db.doc(`users/${t.parentId}/fcmTokens/${t.token}`).delete());
        }
      }
    });
    await Promise.all(deletions);
  }
);

/**
 * When a parent requests a screenshot, send the child device a silent,
 * high-priority FCM data message so it captures immediately — even when dozing.
 * No notification is shown on the child; the data message wakes the app's
 * messaging service, which triggers the accessibility capture. The child's
 * Firestore listener remains as a fallback if the push is delayed/undelivered.
 *
 * Firestore trigger: children/{childId}/commands/screenshot
 */
exports.triggerScreenshot = onDocumentWritten(
  "children/{childId}/commands/screenshot",
  async (event) => {
    const after = event.data && event.data.after && event.data.after.data();
    if (!after) return;
    const nonce = Number(after.nonce) || 0;
    if (!nonce) return;
    // Only act on fresh requests (ignore backfills / replays).
    if (Date.now() - nonce > 2 * 60 * 1000) return;

    const childId = event.params.childId;
    // The child registers its token under its own user id (uid === childId).
    const toks = await db.collection(`users/${childId}/fcmTokens`).get();
    const tokens = toks.docs.map((d) => d.id);
    if (tokens.length === 0) return;

    const message = {
      tokens,
      // Data-only (no `notification`) → silent; the app handles it.
      data: { type: "screenshot", nonce: String(nonce) },
      android: { priority: "high" },
    };
    const resp = await getMessaging().sendEachForMulticast(message);
    await require("./trace").serverDiag(resp.failureCount ? "WARN" : "INFO", "push",
      `${process.env.FUNCTION_TARGET || "push"} sent=${resp.successCount} failed=${resp.failureCount}`, event.params.childId);

    // Prune tokens that are no longer valid.
    const deletions = [];
    resp.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument"
        ) {
          deletions.push(db.doc(`users/${childId}/fcmTokens/${tokens[i]}`).delete());
        }
      }
    });
    await Promise.all(deletions);
  },
);

/**
 * When a parent requests a missing permission, push a tap-to-fix notification to
 * the child so they can open the right settings screen and grant it.
 *
 * Firestore trigger: children/{childId}/commands/permissionRequest
 */
exports.triggerPermissionRequest = onDocumentWritten(
  "children/{childId}/commands/permissionRequest",
  async (event) => {
    const after = event.data && event.data.after && event.data.after.data();
    if (!after) return;
    const nonce = Number(after.nonce) || 0;
    if (!nonce || Date.now() - nonce > 10 * 60 * 1000) return;
    const permission = String(after.permission || "");
    if (!permission) return;

    const childId = event.params.childId;
    const toks = await db.collection(`users/${childId}/fcmTokens`).get();
    const tokens = toks.docs.map((d) => d.id);
    if (tokens.length === 0) return;

    const labels = {
      location: "location access",
      accessibility: "the accessibility service",
      admin: "uninstall protection",
      usageAccess: "usage access",
      notifications: "notifications",
      battery: "unrestricted battery",
      fullScreenAlarm: "the full-screen alarm",
      storage: "file access (to block app installs)",
    };
    const label = labels[permission] || "a permission";

    const message = {
      tokens,
      notification: {
        title: "Action needed",
        body: `Your family asked you to turn on ${label}. Tap to fix it.`,
      },
      data: { type: "permission", permission },
      android: { priority: "high", notification: { channelId: "permission_requests" } },
    };
    const resp = await getMessaging().sendEachForMulticast(message);
    await require("./trace").serverDiag(resp.failureCount ? "WARN" : "INFO", "push",
      `${process.env.FUNCTION_TARGET || "push"} sent=${resp.successCount} failed=${resp.failureCount}`, event.params.childId);

    const deletions = [];
    resp.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument"
        ) {
          deletions.push(db.doc(`users/${childId}/fcmTokens/${tokens[i]}`).delete());
        }
      }
    });
    await Promise.all(deletions);
  },
);

/**
 * When the child auto-blocks a newly installed app, push a review prompt to the
 * linked parents.
 *
 * Firestore trigger: children/{childId}/pendingApps/{pkg}
 */
exports.notifyNewApp = onDocumentCreated(
  "children/{childId}/pendingApps/{pkg}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data() || {};
    const childId = event.params.childId;
    const label = clip(data.label, 60, "An app");
    if (!(await pushAllowed("newApp", childId, 30))) return;

    const childSnap = await db.doc(`children/${childId}`).get();
    if (!childSnap.exists) return;
    const child = childSnap.data() || {};
    const parents = Array.isArray(child.linkedParents) ? child.linkedParents : [];
    if (parents.length === 0) return;
    const childName =
      clip(child.profile && child.profile.name, 60, "your child");

    const title = `New app on ${childName}'s phone`;
    const body = `${label} was just installed and we've blocked it. Tap to allow it or keep it blocked.`;

    const tokenDocs = [];
    for (const parentId of parents) {
      const toks = await db.collection(`users/${parentId}/fcmTokens`).get();
      toks.forEach((d) => tokenDocs.push({ parentId, token: d.id }));
    }
    if (tokenDocs.length === 0) return;

    const message = {
      tokens: tokenDocs.map((t) => t.token),
      notification: { title, body },
      data: { type: "newApp", childId, package: event.params.pkg },
      android: { priority: "high", notification: { channelId: "geofence_alerts" } },
    };
    const resp = await getMessaging().sendEachForMulticast(message);
    await require("./trace").serverDiag(resp.failureCount ? "WARN" : "INFO", "push",
      `${process.env.FUNCTION_TARGET || "push"} sent=${resp.successCount} failed=${resp.failureCount}`, event.params.childId);

    const deletions = [];
    resp.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument"
        ) {
          const t = tokenDocs[i];
          deletions.push(db.doc(`users/${t.parentId}/fcmTokens/${t.token}`).delete());
        }
      }
    });
    await Promise.all(deletions);
  }
);

/**
 * When the child sends an "ask" (more time, unlock an app, use in school, extend
 * bedtime), push every linked parent so they can approve/deny.
 *
 * Firestore trigger: children/{childId}/requests/{requestId}
 */
exports.notifyChildRequest = onDocumentCreated(
  "children/{childId}/requests/{requestId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data() || {};
    if ((data.status || "PENDING") !== "PENDING") return;
    const childId = event.params.childId;
    const appLabel = clip(data.appLabel, 60, "an app");
    const minutes = Number(data.minutes) || 0;
    if (!(await pushAllowed("request", childId, 20))) return;

    const childSnap = await db.doc(`children/${childId}`).get();
    if (!childSnap.exists) return;
    const child = childSnap.data() || {};
    const parents = Array.isArray(child.linkedParents) ? child.linkedParents : [];
    if (parents.length === 0) return;
    const childName = clip(child.profile && child.profile.name, 60, "Your child");

    let title;
    switch (data.kind) {
      case "UNLOCK_APP": title = `${childName} wants to unlock ${appLabel}`; break;
      case "USE_IN_SCHOOL": title = `${childName} wants ${appLabel} during school`; break;
      case "EXTEND_BEDTIME":
        title = minutes > 0 ? `${childName} wants ${minutes} more min before bedtime` : `${childName} wants to extend bedtime`;
        break;
      default:
        title = minutes > 0 ? `${childName} is asking for ${minutes} more minutes` : `${childName} sent you a request`;
    }
    const body = "Tap to approve or deny.";

    const tokenDocs = [];
    for (const parentId of parents) {
      const toks = await db.collection(`users/${parentId}/fcmTokens`).get();
      toks.forEach((d) => tokenDocs.push({ parentId, token: d.id }));
    }
    if (tokenDocs.length === 0) return;

    // Data-only so the parent app always builds the notification itself (with
    // Approve/Reject action buttons). A top-level `notification` payload would
    // make the system tray show it silently while backgrounded, skipping our
    // onMessageReceived and dropping the action buttons.
    const message = {
      tokens: tokenDocs.map((t) => t.token),
      data: {
        type: "request",
        childId,
        requestId: event.params.requestId,
        title,
        body,
        childName,
        kind: data.kind || "MORE_TIME",
        appLabel,
        minutes: String(minutes),
      },
      android: { priority: "high" },
    };
    const resp = await getMessaging().sendEachForMulticast(message);
    await require("./trace").serverDiag(resp.failureCount ? "WARN" : "INFO", "push",
      `${process.env.FUNCTION_TARGET || "push"} sent=${resp.successCount} failed=${resp.failureCount}`, event.params.childId);

    const deletions = [];
    resp.responses.forEach((r, i) => {
      if (!r.success) {
        const code = r.error && r.error.code;
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-argument"
        ) {
          const t = tokenDocs[i];
          deletions.push(db.doc(`users/${t.parentId}/fcmTokens/${t.token}`).delete());
        }
      }
    });
    await Promise.all(deletions);
  }
);

// Two-factor backup codes (generate / status / redeem). See mfaBackupCodes.js.
const mfaBackupCodes = require("./mfaBackupCodes");
exports.generateMfaBackupCodes = mfaBackupCodes.generateMfaBackupCodes;
exports.getMfaBackupCodeStatus = mfaBackupCodes.getMfaBackupCodeStatus;
exports.redeemMfaBackupCode = mfaBackupCodes.redeemMfaBackupCode;

// Pairing: server-issued single-use codes + approval on the child's phone.
exports.startPairing = pairing.startPairing;
exports.linkChild = pairing.linkChild;
exports.respondToParentRequest = pairing.respondToParentRequest;
exports.unlinkChild = pairing.unlinkChild;

// Tamper alerts: protections switched off, or the phone stops checking in.
const monitor = require("./monitor");
exports.watchChildGuard = monitor.watchChildGuard;
exports.checkStaleChildren = monitor.checkStaleChildren;

// Removal PIN checked on the server (hash in server-only pinSecrets).
const pin = require("./pin");
exports.setRemovalPin = pin.setRemovalPin;
exports.migratePin = pin.migratePin;
exports.verifyRemovalPin = pin.verifyRemovalPin;
exports.linkedParentInfo = pairing.linkedParentInfo;
exports.requestParentRemoval = pairing.requestParentRemoval;
exports.respondToParentRemoval = pairing.respondToParentRemoval;

// ---- Restore a reinstalled Shield as the SAME child ----------------------------
//
// Encrypted data is bound to the child's id and key, so a reinstalled phone takes
// over the old child record instead of moving the data: a linked parent scans the
// new install's code, the server issues a one-time sign-in for the old child id,
// and the parent's Watch Dog hands the old data key to the new install.

const RESTORE_TTL_MS = 55 * 60 * 1000;
const RESTORE_WAIT_MS = 30 * 60 * 1000;

/**
 * Data: { oldChildId, newChildId, code }. Caller: a parent linked to oldChildId,
 * holding the fresh code shown on the reinstalled phone (newChildId, unsupervised).
 * Returns { ok }.
 */
exports.restoreChild = onCall(async (request) => {
  const parentId = request.auth && request.auth.uid;
  if (!parentId) throw new HttpsError("unauthenticated", "Sign in required.");
  if (pairingInternal.isAnonymous(request.auth)) {
    throw new HttpsError("permission-denied", "Sign in with your parent account.");
  }
  const data = request.data || {};
  const oldChildId = pairingInternal.requireId(data.oldChildId, "child");
  const newChildId = pairingInternal.requireId(data.newChildId, "device");
  if (oldChildId === newChildId) throw new HttpsError("invalid-argument", "This phone is already that child.");
  await pairingInternal.rateLimit(`restore_${parentId}`, 10);

  const [oldSnap, newSnap] = await Promise.all([
    db.doc(`children/${oldChildId}`).get(), db.doc(`children/${newChildId}`).get(),
  ]);
  if (!oldSnap.exists) throw new HttpsError("not-found", "The original child was not found.");
  if (!newSnap.exists) throw new HttpsError("not-found", "The reinstalled phone was not found.");
  const parents = oldSnap.get("linkedParents") || [];
  if (!parents.includes(parentId)) throw new HttpsError("permission-denied", "You are not linked to this child.");
  if ((newSnap.get("linkedParents") || []).length > 0) {
    throw new HttpsError("failed-precondition", "That phone is already set up with a family and can't be restored.");
  }
  // The old install must have stopped checking in (Shield was removed from it).
  const monitor = await db.doc(`childMonitor/${oldChildId}`).get();
  const lastBeat = monitor.exists && monitor.get("lastHeartbeatAt");
  if (lastBeat && Date.now() - lastBeat.toMillis() < RECONNECT_QUIET_MS) {
    throw new HttpsError(
      "failed-precondition",
      "Shield is still running on the original phone. Restore is only for a phone where Shield was " +
        "reinstalled. If you just reinstalled it, wait 10 minutes and try again.",
    );
  }
  if (!(await pairingInternal.consumeInviteFor(newChildId, data.code))) {
    throw new HttpsError("permission-denied", "That code is wrong or has expired. Scan the code on the phone again.");
  }

  const childName = (oldSnap.get("profile") || {}).name || "your child";
  const email = request.auth.token.email || "A parent";
  const others = parents.filter((p) => p !== parentId);
  if (others.length === 0) {
    await issueRestore(oldChildId, newChildId, parentId);
    await pairingInternal.notifyParents(
      parents,
      `${childName}'s Shield was restored`,
      `${email} restored ${childName} on a phone where Shield was reinstalled.`,
    ).catch((e) => console.error("restore notify", e));
    return { ok: true };
  }
  // Another parent supervises this child too: the restore waits, so they can stop
  // it if they didn't expect it (it hands the child's account to a new phone).
  const readyAt = Date.now() + RESTORE_WAIT_MS;
  await db.doc(`pendingRestores/${oldChildId}`).set({
    newChildId, parentId, email, readyAt: Timestamp.fromMillis(readyAt), createdAt: Timestamp.now(),
  });
  await pairingInternal.notifyParents(
    parents,
    `${childName}'s Shield will be restored in 30 minutes`,
    `${email} is restoring ${childName} on a phone where Shield was reinstalled. ` +
      "If you didn't expect this, cancel it in Watch Dog → Manage children.",
  ).catch((e) => console.error("restore notify", e));
  return { ok: true, pendingUntilMillis: readyAt };
});

/**
 * Hands the child's account to the reinstalled phone: a one-time sign-in only that
 * install can read, and a mark on the child so supervision changes are held back
 * for a day (a restored phone can't at once remove or add parents).
 */
async function issueRestore(oldChildId, newChildId, parentId) {
  const { getAuth } = require("firebase-admin/auth");
  const token = await getAuth().createCustomToken(oldChildId, { restoredFrom: newChildId });
  await db.doc(`restoreTokens/${newChildId}`).set({
    token,
    oldChildId,
    parentId,
    // A custom token works for an hour.
    expiresAt: Timestamp.fromMillis(Date.now() + RESTORE_TTL_MS),
  });
  await db.doc(`children/${oldChildId}`).update({ restoredAt: Timestamp.now() });
}

/** Every 5 minutes: restores whose waiting time is over and nobody cancelled. */
exports.issuePendingRestores = onSchedule("every 5 minutes", async () => {
  const due = await db.collection("pendingRestores").where("readyAt", "<=", Timestamp.now()).get();
  for (const doc of due.docs) {
    const { newChildId, parentId } = doc.data();
    const oldChildId = doc.id;
    await doc.ref.delete();
    try {
      const [o, n] = await Promise.all([db.doc(`children/${oldChildId}`).get(), db.doc(`children/${newChildId}`).get()]);
      const parents = (o.exists && o.get("linkedParents")) || [];
      if (!parents.includes(parentId) || !n.exists || ((n.get("linkedParents") || []).length > 0)) continue;
      await issueRestore(oldChildId, newChildId, parentId);
      const childName = (o.get("profile") || {}).name || "your child";
      await pairingInternal.notifyParents(parents, `${childName}'s Shield was restored`,
        `${childName} was restored on the reinstalled phone.`).catch(() => {});
    } catch (e) {
      console.error("issuePendingRestores", oldChildId, e);
    }
  }
});

/** Any parent of the child stops a waiting restore. Data: { childId }. */
exports.cancelRestore = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const childId = pairingInternal.requireId((request.data || {}).childId, "child");
  const child = await db.doc(`children/${childId}`).get();
  const parents = (child.exists && child.get("linkedParents")) || [];
  if (!parents.includes(uid)) throw new HttpsError("permission-denied", "You are not linked to this child.");
  const ref = db.doc(`pendingRestores/${childId}`);
  if (!(await ref.get()).exists) return { ok: true, cancelled: false };
  await ref.delete();
  const childName = (child.get("profile") || {}).name || "your child";
  await pairingInternal.notifyParents(parents, `Restore of ${childName} cancelled`,
    `${request.auth.token.email || "A parent"} cancelled the restore of ${childName}.`).catch(() => {});
  return { ok: true, cancelled: true };
});

/**
 * Called by the reinstalled phone once it has signed in as the old child: removes
 * the temporary install record and its account. Data: { tempChildId }.
 */
exports.completeRestore = onCall(async (request) => {
  const childId = request.auth && request.auth.uid;
  if (!childId) throw new HttpsError("unauthenticated", "Sign in required.");
  const tempId = pairingInternal.requireId((request.data || {}).tempChildId, "device");
  const ref = db.doc(`restoreTokens/${tempId}`);
  const snap = await ref.get();
  if (!snap.exists || snap.get("oldChildId") !== childId) {
    throw new HttpsError("permission-denied", "Nothing to finish.");
  }
  await ref.delete();
  const tempRef = db.doc(`children/${tempId}`);
  const temp = await tempRef.get();
  if (temp.exists && (temp.get("linkedParents") || []).length === 0) await db.recursiveDelete(tempRef);
  await db.doc(`publicKeys/${tempId}`).delete().catch(() => {});
  const invites = await db.collection("pairingInvites").where("childId", "==", tempId).get();
  await Promise.all(invites.docs.map((d) => d.ref.delete()));
  const { getAuth } = require("firebase-admin/auth");
  await getAuth().deleteUser(tempId).catch(() => {});
  return { ok: true };
});

// ---- Diagnostics viewer -------------------------------------------------------

/**
 * Diagnostics for one account: the caller's own (all their phones) or a child
 * they supervise, newest first. App events and the server's events about that
 * account share one timeline. Data: { uid?, sinceMillis?, limit? }.
 */
exports.readDiagnostics = onCall(async (request) => {
  const caller = request.auth && request.auth.uid;
  if (!caller) throw new HttpsError("unauthenticated", "Sign in required.");
  const data = request.data || {};
  const target = data.uid ? pairingInternal.requireId(data.uid, "account") : caller;
  if (target !== caller) {
    const child = await db.doc(`children/${target}`).get();
    if (!child.exists || !(child.get("linkedParents") || []).includes(caller)) {
      throw new HttpsError("permission-denied", "You can only read your own or your children's diagnostics.");
    }
  }
  const since = Math.max(Number(data.sinceMillis) || 0, Date.now() - 30 * 24 * 60 * 60 * 1000);
  const limit = Math.min(Math.max(Number(data.limit) || 300, 1), 1000);
  const snap = await db.collection("diagEvents")
    .where("uid", "==", target)
    .where("atMillis", ">=", since)
    .orderBy("atMillis", "desc")
    .limit(limit)
    .get();
  return {
    events: snap.docs.map((d) => {
      const e = d.data();
      return {
        atMillis: e.atMillis, level: e.level, tag: e.tag, message: e.message, exception: e.exception || "",
        app: e.app, version: e.version, model: e.model,
      };
    }),
  };
});
