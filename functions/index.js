const { onDocumentCreated, onDocumentDeleted, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getStorage } = require("firebase-admin/storage");

initializeApp();
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

const db = getFirestore();
const STORAGE_BUCKET = "watch-dog-13d09.firebasestorage.app";
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
    let path = data.storagePath ||
      `screenshots/${event.params.childId}/${data.capturedAtMillis}.png`;
    path = String(path).replace(/^\/+/, "");
    try {
      await getStorage().bucket(STORAGE_BUCKET).file(path).delete({ ignoreNotFound: true });
    } catch (e) {
      // Best-effort; object may already be gone.
    }
  },
);

/**
 * Reconnect a reinstalled Shield: migrate the old child's data (geofences, rules,
 * protection, history, etc.) onto the new device's ID, move the parent links over,
 * and delete the old child record. Called by a linked parent after scanning the
 * reinstalled device's pairing code.
 */
exports.reconnectChild = onCall(async (request) => {
  const parentId = request.auth && request.auth.uid;
  if (!parentId) throw new HttpsError("unauthenticated", "Sign in required.");

  const data = request.data || {};
  const oldChildId = data.oldChildId;
  const newChildId = data.newChildId;
  const code = data.code;
  if (!oldChildId || !newChildId || !code) {
    throw new HttpsError("invalid-argument", "Missing oldChildId, newChildId or code.");
  }
  if (oldChildId === newChildId) {
    throw new HttpsError("invalid-argument", "This device is already connected.");
  }

  const oldRef = db.doc(`children/${oldChildId}`);
  const newRef = db.doc(`children/${newChildId}`);
  const [oldSnap, newSnap] = await Promise.all([oldRef.get(), newRef.get()]);
  if (!oldSnap.exists) throw new HttpsError("not-found", "The original child was not found.");
  if (!newSnap.exists) throw new HttpsError("not-found", "The reinstalled device was not found.");

  const oldData = oldSnap.data() || {};
  const newData = newSnap.data() || {};
  const parents = Array.isArray(oldData.linkedParents) ? oldData.linkedParents : [];
  if (!parents.includes(parentId)) {
    throw new HttpsError("permission-denied", "You are not linked to this child.");
  }
  if ((newData.pairingCode || "") !== code) {
    throw new HttpsError("permission-denied", "That pairing code does not match the scanned device.");
  }

  // Copy every subcollection from the old child onto the new one.
  const SUBS = [
    "geofences", "appRules", "inventory", "usage", "live",
    "trail", "travelHistory", "geofenceEvents", "settings",
  ];
  for (const sub of SUBS) {
    const docs = await oldRef.collection(sub).get();
    let batch = db.batch();
    let n = 0;
    for (const d of docs.docs) {
      batch.set(newRef.collection(sub).doc(d.id), d.data());
      if (++n >= 400) { await batch.commit(); batch = db.batch(); n = 0; }
    }
    if (n > 0) await batch.commit();
  }

  // Carry over the old profile + parent links onto the new child record.
  await newRef.set(
    {
      profile: oldData.profile || newData.profile || {},
      linkedParents: parents,
      pairingActive: false,
      role: "CHILD",
    },
    { merge: true },
  );

  // Remove the old child and all of its data.
  await db.recursiveDelete(oldRef);

  return { ok: true };
});

/**
 * Join a child as a read-only VIEWER using its pairing code. Verifies the code
 * and that the parent has enabled viewer access, then adds the caller's uid to
 * the child's `viewers` list. Read-only access is enforced by security rules.
 */
exports.joinAsViewer = onCall(async (request) => {
  const viewerId = request.auth && request.auth.uid;
  if (!viewerId) throw new HttpsError("unauthenticated", "Sign in required.");

  const code = ((request.data || {}).code || "").trim();
  if (!code) throw new HttpsError("invalid-argument", "Enter the pairing code.");

  // Resolve the child by its pairing code (codes are effectively unique).
  const q = await db.collection("children").where("pairingCode", "==", code).limit(2).get();
  if (q.empty) throw new HttpsError("not-found", "No child found for this code.");
  if (q.size > 1) throw new HttpsError("failed-precondition", "That code is ambiguous — try again.");
  const childSnap = q.docs[0];
  const childId = childSnap.id;

  const accessSnap = await db.doc(`children/${childId}/settings/viewerAccess`).get();
  if (!(accessSnap.exists && accessSnap.data() && accessSnap.data().enabled === true)) {
    throw new HttpsError("permission-denied", "Viewing isn't enabled for this child — ask the parent to turn it on.");
  }

  const { FieldValue } = require("firebase-admin/firestore");
  await childSnap.ref.update({ viewers: FieldValue.arrayUnion(viewerId) });
  return { ok: true, childId };
});

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

    // Child doc → linked parents + child name.
    const childSnap = await db.doc(`children/${childId}`).get();
    if (!childSnap.exists) return;
    const child = childSnap.data() || {};
    const parents = Array.isArray(child.linkedParents) ? child.linkedParents : [];
    if (parents.length === 0) return;
    const childName =
      (child.profile && child.profile.name) ? child.profile.name : "Your child";

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
    const label = data.label || "An app";

    const childSnap = await db.doc(`children/${childId}`).get();
    if (!childSnap.exists) return;
    const child = childSnap.data() || {};
    const parents = Array.isArray(child.linkedParents) ? child.linkedParents : [];
    if (parents.length === 0) return;
    const childName =
      (child.profile && child.profile.name) ? child.profile.name : "your child";

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
    const appLabel = data.appLabel || "an app";
    const minutes = Number(data.minutes) || 0;

    const childSnap = await db.doc(`children/${childId}`).get();
    if (!childSnap.exists) return;
    const child = childSnap.data() || {};
    const parents = Array.isArray(child.linkedParents) ? child.linkedParents : [];
    if (parents.length === 0) return;
    const childName = (child.profile && child.profile.name) ? child.profile.name : "Your child";

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

    const message = {
      tokens: tokenDocs.map((t) => t.token),
      notification: { title, body },
      data: { type: "request", childId, requestId: event.params.requestId },
      android: { priority: "high", notification: { channelId: "geofence_alerts" } },
    };
    const resp = await getMessaging().sendEachForMulticast(message);

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
