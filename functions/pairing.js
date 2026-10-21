/**
 * Pairing a parent to a child device, done entirely server-side.
 *
 *  1. startPairing — the child device (or an already-linked parent inviting a
 *     co-parent) gets a fresh code: 8 symbols from a CSPRNG, valid 10 minutes,
 *     single use. Only its hash is stored (pairingInvites/{hash}, server-only).
 *  2. linkChild — a signed-in, non-anonymous parent redeems the code. The code is
 *     consumed, and a parent request is recorded on the child
 *     (children/{childId}/parentRequests/{parentUid}); nothing is linked yet.
 *  3. respondToParentRequest — the child device shows "Link <email>?" and the
 *     person holding it approves or denies. Only on approval is the parent added
 *     to linkedParents (which is what makes Shield share its data key), and any
 *     existing parents are told.
 *
 * linkedParents is never client-writable (see firestore.rules); this file and
 * unlinkChild / reconnectChild are the only writers.
 */
const crypto = require("crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

const ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // no 0/O/1/I
const CODE_LENGTH = 8; // 40 bits; with 10-min expiry, single use and rate limits
const INVITE_TTL_MS = 10 * 60 * 1000;
const REQUEST_TTL_MS = 15 * 60 * 1000;
const LINK_ATTEMPTS_PER_HOUR = 10;
const ID_RE = /^[A-Za-z0-9_-]{1,128}$/;

const db = () => getFirestore();

function normalizeCode(code) {
  return String(code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function inviteRef(normalized) {
  const hash = crypto.createHash("sha256").update(normalized).digest("hex");
  return db().doc(`pairingInvites/${hash}`);
}

function newCode() {
  let s = "";
  for (let i = 0; i < CODE_LENGTH; i++) s += ALPHABET[crypto.randomInt(ALPHABET.length)];
  return s;
}

/** A path-safe document id, or an invalid-argument error. */
function requireId(value, what) {
  if (typeof value !== "string" || !ID_RE.test(value)) {
    throw new HttpsError("invalid-argument", `Invalid ${what}.`);
  }
  return value;
}

function isAnonymous(auth) {
  return !auth || !auth.token || !auth.token.firebase ||
    auth.token.firebase.sign_in_provider === "anonymous";
}

/** Counts an attempt for [key]; throws once more than [max] happened this hour. */
async function rateLimit(key, max) {
  const ref = db().doc(`rateLimits/${key}`);
  const allowed = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const now = Date.now();
    const d = snap.exists ? snap.data() : {};
    const fresh = !d.windowStart || now - d.windowStart.toMillis() > 60 * 60 * 1000;
    const count = fresh ? 0 : (d.count || 0);
    if (count >= max) return false;
    tx.set(ref, {
      windowStart: fresh ? Timestamp.fromMillis(now) : d.windowStart,
      count: count + 1,
    });
    return true;
  });
  if (!allowed) {
    throw new HttpsError("resource-exhausted", "Too many attempts. Try again in an hour.");
  }
}

/** Sends a plain title/body push to each parent's registered devices. */
async function notifyParents(parentIds, title, body) {
  const tokenDocs = [];
  for (const parentId of parentIds) {
    const toks = await db().collection(`users/${parentId}/fcmTokens`).get();
    toks.forEach((d) => tokenDocs.push(`users/${parentId}/fcmTokens/${d.id}`));
  }
  if (tokenDocs.length === 0) return;
  const tokens = tokenDocs.map((p) => p.split("/").pop());
  const resp = await getMessaging().sendEachForMulticast({
    tokens,
    data: { title, body },
    android: { priority: "high" },
  });
  const stale = [];
  resp.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-argument") {
      stale.push(db().doc(tokenDocs[i]).delete());
    }
  });
  await Promise.all(stale);
}

/**
 * Issue a fresh pairing code for a child. Callable by the child device itself, or
 * by a parent already linked to it (to invite a co-parent). Replaces any earlier
 * unused code for that child. Data: { childId? }. Returns { code, expiresAtMillis }.
 */
exports.startPairing = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const childId = request.data && request.data.childId ?
    requireId(request.data.childId, "child") : uid;

  const childSnap = await db().doc(`children/${childId}`).get();
  if (!childSnap.exists) throw new HttpsError("not-found", "Set up this device first.");
  const parents = childSnap.get("linkedParents") || [];
  if (uid !== childId && !parents.includes(uid)) {
    throw new HttpsError("permission-denied", "You are not linked to this child.");
  }
  await rateLimit(`startPairing_${uid}`, 30);

  // One live code per child: drop older ones.
  const old = await db().collection("pairingInvites").where("childId", "==", childId).get();
  const batch = db().batch();
  old.forEach((d) => batch.delete(d.ref));

  let code;
  let ref;
  for (;;) {
    code = newCode();
    ref = inviteRef(code);
    if (!(await ref.get()).exists) break;
  }
  const expiresAtMillis = Date.now() + INVITE_TTL_MS;
  batch.set(ref, {
    childId,
    invitedBy: uid,
    expiresAt: Timestamp.fromMillis(expiresAtMillis),
  });
  await batch.commit();
  return { code, expiresAtMillis };
});

/**
 * Consume a pairing code and ask the child device to approve the caller. Callable
 * only by a signed-in parent account (anonymous accounts are refused) and rate
 * limited. Data: { code }. Returns { status: "pending" | "linked", childId }.
 */
exports.linkChild = onCall(async (request) => {
  const parentId = request.auth && request.auth.uid;
  if (!parentId) throw new HttpsError("unauthenticated", "Sign in required.");
  if (isAnonymous(request.auth)) {
    throw new HttpsError("permission-denied", "Sign in to Watch Dog with your parent account.");
  }
  const normalized = normalizeCode((request.data || {}).code);
  if (normalized.length !== CODE_LENGTH) {
    throw new HttpsError("invalid-argument", "Enter the 8-character code shown on your child's phone.");
  }
  await rateLimit(`linkChild_${parentId}`, LINK_ATTEMPTS_PER_HOUR);

  const ref = inviteRef(normalized);
  const childId = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return null;
    const invite = snap.data();
    tx.delete(ref); // single use, even if expired
    if (invite.expiresAt.toMillis() < Date.now()) return null;
    return invite.childId;
  });
  if (!childId) {
    throw new HttpsError(
      "not-found",
      "That code is wrong or has expired. Open Shield on your child's phone for a fresh code.",
    );
  }

  const childRef = db().doc(`children/${childId}`);
  const child = await childRef.get();
  if (!child.exists) throw new HttpsError("not-found", "That child device no longer exists.");
  if ((child.get("linkedParents") || []).includes(parentId)) {
    return { status: "linked", childId };
  }
  await childRef.collection("parentRequests").doc(parentId).set({
    parentId,
    email: request.auth.token.email || "",
    requestedAt: Timestamp.now(),
    expiresAt: Timestamp.fromMillis(Date.now() + REQUEST_TTL_MS),
  });
  return { status: "pending", childId };
});

/**
 * The child device approves or denies a parent request. Only the child itself may
 * call this. Data: { parentId, approve }. Returns { ok }.
 */
exports.respondToParentRequest = onCall(async (request) => {
  const childId = request.auth && request.auth.uid;
  if (!childId) throw new HttpsError("unauthenticated", "Sign in required.");
  const parentId = requireId((request.data || {}).parentId, "parent");
  const approve = (request.data || {}).approve === true;

  const childRef = db().doc(`children/${childId}`);
  const reqRef = childRef.collection("parentRequests").doc(parentId);
  const result = await db().runTransaction(async (tx) => {
    const [childSnap, reqSnap] = await Promise.all([tx.get(childRef), tx.get(reqRef)]);
    if (!childSnap.exists || !reqSnap.exists) return { ok: false };
    tx.delete(reqRef);
    if (!approve) return { ok: true, approved: false };
    if (reqSnap.get("expiresAt").toMillis() < Date.now()) return { ok: false, expired: true };
    const before = childSnap.get("linkedParents") || [];
    tx.update(childRef, {
      linkedParents: FieldValue.arrayUnion(parentId),
      pairingActive: false,
      pairingCode: FieldValue.delete(),
    });
    return {
      ok: true,
      approved: true,
      existing: before.filter((p) => p !== parentId),
      childName: (childSnap.get("profile") || {}).name || "your child",
      email: reqSnap.get("email") || "Another parent",
    };
  });
  if (!result.ok) {
    throw new HttpsError(
      "failed-precondition",
      result.expired ? "That request expired. Ask the parent to scan a new code." : "That request is no longer pending.",
    );
  }
  if (result.approved) {
    // Drop any other unused code for this child: pairing is done.
    const invites = await db().collection("pairingInvites").where("childId", "==", childId).get();
    await Promise.all(invites.docs.map((d) => d.ref.delete()));
    if (result.existing.length > 0) {
      await notifyParents(
        result.existing,
        `New parent linked to ${result.childName}`,
        `${result.email} can now see ${result.childName}'s location and activity. If you didn't expect this, open Watch Dog.`,
      ).catch((e) => console.error("notify existing parents failed", e));
    }
  }
  return { ok: true };
});

/**
 * A parent unlinks themselves from a child. Only removes the caller.
 * Data: { childId }. Returns { ok }.
 */
exports.unlinkChild = onCall(async (request) => {
  const parentId = request.auth && request.auth.uid;
  if (!parentId) throw new HttpsError("unauthenticated", "Sign in required.");
  const childId = requireId((request.data || {}).childId, "child");
  const ref = db().doc(`children/${childId}`);
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const parents = snap.get("linkedParents") || [];
    if (!parents.includes(parentId)) return;
    const remaining = parents.filter((p) => p !== parentId);
    tx.update(ref, { linkedParents: remaining, pairingActive: remaining.length === 0 });
  });
  return { ok: true };
});

/**
 * Consume a code for [childId] without the approval step — used by reconnectChild,
 * where a linked parent moves the child's data onto a reinstalled device whose
 * fresh code they just scanned on that device. Throws on a wrong/expired code.
 */
async function consumeInviteFor(childId, code) {
  const normalized = normalizeCode(code);
  if (normalized.length !== CODE_LENGTH) return false;
  const ref = inviteRef(normalized);
  return db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return false;
    const invite = snap.data();
    if (invite.childId !== childId) return false;
    tx.delete(ref);
    return invite.expiresAt.toMillis() >= Date.now();
  });
}

exports._internal = { consumeInviteFor, requireId, rateLimit, isAnonymous, notifyParents, CODE_LENGTH };
exports._test = { normalizeCode, newCode };

/**
 * Who supervises a child: the linked parents' emails, so Shield can always show
 * "Supervised by …". Callable by the child device itself or a linked parent.
 * Data: { childId? }. Returns { parents: [{ uid, email }] }.
 */
exports.linkedParentInfo = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const childId = request.data && request.data.childId ? requireId(request.data.childId, "child") : uid;
  const snap = await db().doc(`children/${childId}`).get();
  if (!snap.exists) return { parents: [] };
  const parents = (snap.get("linkedParents") || []).filter((p) => typeof p === "string");
  if (uid !== childId && !parents.includes(uid)) {
    throw new HttpsError("permission-denied", "You are not linked to this child.");
  }
  if (parents.length === 0) return { parents: [] };
  const { getAuth } = require("firebase-admin/auth");
  const result = await getAuth().getUsers(parents.map((p) => ({ uid: p })));
  const emails = new Map(result.users.map((u) => [u.uid, u.email || ""]));
  return { parents: parents.map((p) => ({ uid: p, email: emails.get(p) || "" })) };
});
