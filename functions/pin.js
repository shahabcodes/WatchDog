/**
 * The removal PIN, verified on the server.
 *
 * The PIN's salted hash used to live in children/{childId}/settings/protection,
 * which the child's own account can read — so a determined child could copy it
 * and brute-force it offline. Now:
 *  - the hash lives in pinSecrets/{childId}, which no app can read or write;
 *  - setRemovalPin (a linked parent) hashes and stores a new PIN;
 *  - verifyRemovalPin (the child device) checks a typed PIN, with an escalating
 *    lockout, and returns an unlock window in server time;
 *  - migratePin (a linked parent's app, on open) moves an existing hash out of
 *    the readable doc, so parents don't need to re-enter their PIN.
 * Hashing matches the apps' PinHasher: PBKDF2-HMAC-SHA256, 150k rounds, 16-byte
 * salt, 32-byte key, standard Base64 — so existing hashes verify unchanged.
 */
const crypto = require("crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { _internal: pairing } = require("./pairing");

const ITERATIONS = 150000;
const PIN_RE = /^\d{15,20}$/;
const UNLOCK_WINDOW_MS = 2 * 60 * 1000;
const MAX_FAILURES = 5;
const BASE_LOCK_MS = 15 * 60 * 1000;
const MAX_LOCK_MS = 24 * 60 * 60 * 1000;

const db = () => getFirestore();
const secretRef = (childId) => db().doc(`pinSecrets/${childId}`);
const protectionRef = (childId) => db().doc(`children/${childId}/settings/protection`);

function hashPin(pin) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.pbkdf2Sync(pin, salt, ITERATIONS, 32, "sha256");
  return { salt: salt.toString("base64"), hash: hash.toString("base64") };
}

function verifyHash(pin, saltB64, hashB64) {
  if (!saltB64 || !hashB64) return false;
  const expected = Buffer.from(hashB64, "base64");
  const actual = crypto.pbkdf2Sync(pin, Buffer.from(saltB64, "base64"), ITERATIONS, expected.length || 32, "sha256");
  return expected.length > 0 && expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

/** Throws unless the caller is a non-anonymous parent linked to [childId]. */
async function requireLinkedParent(request, childId) {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  if (pairing.isAnonymous(request.auth)) throw new HttpsError("permission-denied", "Parent account required.");
  const child = await db().doc(`children/${childId}`).get();
  if (!child.exists || !(child.get("linkedParents") || []).includes(uid)) {
    throw new HttpsError("permission-denied", "You are not linked to this child.");
  }
  return uid;
}

/**
 * Moves any PIN hash still in the child-readable protection doc into pinSecrets
 * (hashing a legacy plaintext PIN first) and removes it from that doc. Returns
 * the secret now in force, or null when no PIN is set.
 */
async function migrateIfNeeded(childId) {
  const [secretSnap, protSnap] = await Promise.all([secretRef(childId).get(), protectionRef(childId).get()]);
  const prot = protSnap.exists ? protSnap.data() : {};
  const hasReadable = !!(prot.pinHash || prot.pinSalt || prot.pin);
  let secret = secretSnap.exists ? secretSnap.data() : null;
  if (!secret && hasReadable) {
    if (prot.pinHash && prot.pinSalt) secret = { salt: prot.pinSalt, hash: prot.pinHash };
    else if (typeof prot.pin === "string" && prot.pin) secret = hashPin(prot.pin);
    if (secret) {
      await secretRef(childId).set({ ...secret, failures: 0, lockouts: 0, lockedUntil: null, updatedAt: Timestamp.now() });
    }
  }
  if (hasReadable) {
    await protectionRef(childId).set({
      pinSalt: FieldValue.delete(), pinHash: FieldValue.delete(), pin: FieldValue.delete(), hasPin: !!secret,
    }, { merge: true });
  }
  return secret;
}

exports.setRemovalPin = onCall(async (request) => {
  const data = request.data || {};
  const childId = pairing.requireId(data.childId, "child");
  await requireLinkedParent(request, childId);
  const pin = typeof data.pin === "string" ? data.pin : "";
  if (!PIN_RE.test(pin)) throw new HttpsError("invalid-argument", "The PIN must be 15 to 20 digits.");
  const { salt, hash } = hashPin(pin);
  await secretRef(childId).set({ salt, hash, failures: 0, lockouts: 0, lockedUntil: null, updatedAt: Timestamp.now() });
  await protectionRef(childId).set({
    hasPin: true, pinSalt: FieldValue.delete(), pinHash: FieldValue.delete(), pin: FieldValue.delete(),
  }, { merge: true });
  return { ok: true };
});

exports.migratePin = onCall(async (request) => {
  const childId = pairing.requireId((request.data || {}).childId, "child");
  await requireLinkedParent(request, childId);
  const secret = await migrateIfNeeded(childId);
  return { hasPin: !!secret };
});

/** The child device checks a typed PIN. Returns { ok, unlockUntilMillis }. */
exports.verifyRemovalPin = onCall(async (request) => {
  const childId = request.auth && request.auth.uid;
  if (!childId) throw new HttpsError("unauthenticated", "Sign in required.");
  const pin = typeof (request.data || {}).pin === "string" ? request.data.pin : "";
  if (!PIN_RE.test(pin)) throw new HttpsError("invalid-argument", "Incorrect PIN.");
  if (!(await db().doc(`children/${childId}`).get()).exists) {
    throw new HttpsError("permission-denied", "This device isn't set up.");
  }
  const secret = await migrateIfNeeded(childId);
  if (!secret) throw new HttpsError("failed-precondition", "No removal PIN has been set by a parent.");

  const ref = secretRef(childId);
  const now = Date.now();
  // Check the lock before the (slow) hash, then record the outcome atomically.
  const before = (await ref.get()).data() || {};
  if (before.lockedUntil && before.lockedUntil.toMillis() > now) {
    const mins = Math.ceil((before.lockedUntil.toMillis() - now) / 60000);
    throw new HttpsError("resource-exhausted", `Too many wrong PINs. Try again in ${mins} minute${mins === 1 ? "" : "s"}.`);
  }
  const ok = verifyHash(pin, secret.salt, secret.hash);
  const result = await db().runTransaction(async (tx) => {
    const d = (await tx.get(ref)).data() || {};
    if (ok) {
      tx.update(ref, { failures: 0, lockedUntil: null });
      return { ok: true };
    }
    const failures = (d.failures || 0) + 1;
    if (failures >= MAX_FAILURES) {
      const lockouts = (d.lockouts || 0) + 1;
      const lockMs = Math.min(BASE_LOCK_MS * 2 ** (lockouts - 1), MAX_LOCK_MS);
      tx.update(ref, { failures: 0, lockouts, lockedUntil: Timestamp.fromMillis(now + lockMs) });
      return { ok: false, lockedMinutes: Math.round(lockMs / 60000) };
    }
    tx.update(ref, { failures });
    return { ok: false, left: MAX_FAILURES - failures };
  });
  if (!result.ok) {
    if (result.lockedMinutes) {
      throw new HttpsError("resource-exhausted", `Too many wrong PINs. Try again in ${result.lockedMinutes} minutes.`);
    }
    throw new HttpsError("permission-denied", `Incorrect PIN. ${result.left} tr${result.left === 1 ? "y" : "ies"} left.`);
  }
  // Clear the lockout history on success.
  await ref.update({ lockouts: 0 }).catch(() => {});
  return { ok: true, unlockUntilMillis: now + UNLOCK_WINDOW_MS };
});

exports._test = { hashPin, verifyHash };
