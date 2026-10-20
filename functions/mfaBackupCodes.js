/**
 * Backup codes for parent TOTP two-factor. Firebase's native MFA has no recovery
 * codes, so this adds them server-side:
 *
 *  - generateMfaBackupCodes: a signed-in parent with TOTP enrolled, who confirmed
 *    their password recently, gets 10 fresh single-use codes. Only salted hashes
 *    are stored (mfaBackupCodes/{uid}, closed to clients by firestore.rules); the
 *    plaintext codes are returned once and never again.
 *  - getMfaBackupCodeStatus: how many unused codes the caller has left.
 *  - redeemMfaBackupCode: for a parent who lost their authenticator. Needs the
 *    account's email + password (checked against Firebase Auth) AND an unused
 *    code. On success the TOTP factor is removed, so the parent signs in with
 *    their password and sets 2FA up again on the new device (which voids the old
 *    codes and issues new ones).
 *
 * Codes from an earlier enrollment are void: a code set only counts if it was
 * generated after the current authenticator was enrolled.
 */
const crypto = require("crypto");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");

// The project's Firebase Web/Android API key, used to check a password through
// the Identity Toolkit REST API (the Admin SDK can't verify passwords). Not a
// secret in the strict sense (it ships in the APK), but kept out of this public
// repo. The key is restricted to the Android app, so the request identifies as it.
const AUTH_API_KEY = defineSecret("AUTH_API_KEY");
const ANDROID_PACKAGE = "com.shahaabapps.watchdog";
// SHA-1 of keystore/debug.keystore — the signature every Watch Dog build carries.
const ANDROID_CERT_SHA1 = "D46E3727390A43D9CD939FD57A5E8BEE7225D65F";

const CODE_COUNT = 10;
// No 0/O/1/I to avoid misreads: 32 symbols = 5 bits each, 10 symbols = 50 bits.
const ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
const CODE_LENGTH = 10;
// Must have confirmed the password (re-auth or sign-in) this recently to mint codes.
const RECENT_AUTH_SECONDS = 15 * 60;
const MAX_FAILED_ATTEMPTS = 5;
const LOCKOUT_MS = 15 * 60 * 1000;

const db = () => getFirestore();
const docFor = (uid) => db().doc(`mfaBackupCodes/${uid}`);

/** A random code, formatted XXXXX-XXXXX for readability. */
function newCode() {
  let s = "";
  for (let i = 0; i < CODE_LENGTH; i++) s += ALPHABET[crypto.randomInt(ALPHABET.length)];
  return `${s.slice(0, 5)}-${s.slice(5)}`;
}

/** Uppercase and strip spaces/dashes so "abcde fghjk" matches "ABCDE-FGHJK". */
function normalizeCode(code) {
  return String(code || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function hashCode(salt, normalized) {
  return crypto.createHash("sha256").update(`${salt}:${normalized}`).digest("hex");
}

/** Index of the unused entry matching [code], or -1. Checks every entry (no early exit). */
function findMatch(entries, code) {
  const normalized = normalizeCode(code);
  if (normalized.length !== CODE_LENGTH) return -1;
  let found = -1;
  entries.forEach((e, i) => {
    const a = Buffer.from(hashCode(e.salt, normalized), "hex");
    const b = Buffer.from(e.hash, "hex");
    if (a.length === b.length && crypto.timingSafeEqual(a, b) && !e.usedAt && found < 0) found = i;
  });
  return found;
}

/** Fresh codes plus the hashed entries to store for them. */
function makeCodeSet() {
  const codes = [];
  const entries = [];
  for (let i = 0; i < CODE_COUNT; i++) {
    const code = newCode();
    const salt = crypto.randomBytes(16).toString("hex");
    codes.push(code);
    entries.push({ salt, hash: hashCode(salt, normalizeCode(code)), usedAt: null });
  }
  return { codes, entries };
}

/** The user's TOTP factor, or null. */
function totpFactor(userRecord) {
  const factors = (userRecord.multiFactor && userRecord.multiFactor.enrolledFactors) || [];
  return factors.find((f) => f.factorId === "totp") || null;
}

/** Codes count only if generated after the current authenticator was enrolled. */
function codesValidFor(data, factor) {
  if (!data || !Array.isArray(data.codes) || !data.generatedAt || !factor) return false;
  const enrolledAt = Date.parse(factor.enrollmentTime || "");
  // Fail closed: if the enrollment time can't be read, treat the codes as void.
  return !Number.isNaN(enrolledAt) && data.generatedAt.toMillis() >= enrolledAt;
}

exports.generateMfaBackupCodes = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const authTime = Number(request.auth.token.auth_time || 0);
  if (Date.now() / 1000 - authTime > RECENT_AUTH_SECONDS) {
    throw new HttpsError("failed-precondition", "Please confirm your password again, then retry.");
  }
  const user = await getAuth().getUser(uid);
  if (!totpFactor(user)) {
    throw new HttpsError("failed-precondition", "Turn on two-factor authentication first.");
  }

  const { codes, entries } = makeCodeSet();
  await docFor(uid).set({
    codes: entries,
    generatedAt: Timestamp.now(),
    failedAttempts: 0,
    lockedUntil: null,
  });
  return { codes };
});

exports.getMfaBackupCodeStatus = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const [user, snap] = await Promise.all([getAuth().getUser(uid), docFor(uid).get()]);
  const factor = totpFactor(user);
  const data = snap.exists ? snap.data() : null;
  if (!factor || !codesValidFor(data, factor)) {
    return { enrolled: !!factor, remaining: 0, total: 0, generatedAtMillis: 0 };
  }
  return {
    enrolled: true,
    remaining: data.codes.filter((e) => !e.usedAt).length,
    total: data.codes.length,
    generatedAtMillis: data.generatedAt.toMillis(),
  };
});

/**
 * Checks [email]/[password] with Firebase Auth. Resolves to the account's uid when
 * the password is right AND the account has a second factor; throws otherwise.
 */
async function verifyPasswordForMfaAccount(email, password) {
  const resp = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${AUTH_API_KEY.value()}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Android-Package": ANDROID_PACKAGE,
        "X-Android-Cert": ANDROID_CERT_SHA1,
      },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  const body = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    const reason = String((body.error && body.error.message) || "");
    if (reason.startsWith("TOO_MANY_ATTEMPTS")) {
      throw new HttpsError("resource-exhausted", "Too many attempts. Wait a few minutes and try again.");
    }
    if (reason.startsWith("USER_DISABLED")) {
      throw new HttpsError("permission-denied", "This account has been disabled.");
    }
    if (/INVALID_LOGIN_CREDENTIALS|INVALID_PASSWORD|EMAIL_NOT_FOUND|INVALID_EMAIL/.test(reason)) {
      throw new HttpsError("permission-denied", "Wrong password or backup code.");
    }
    console.error("Password check failed", resp.status, reason);
    throw new HttpsError("unavailable", "Couldn't check your password. Try again.");
  }
  if (!body.mfaPendingCredential) {
    throw new HttpsError("failed-precondition", "Two-factor isn't on for this account. Sign in with your password.");
  }
  const user = await getAuth().getUserByEmail(email);
  if (body.localId && body.localId !== user.uid) {
    throw new HttpsError("internal", "Account mismatch.");
  }
  return user;
}

exports.redeemMfaBackupCode = onCall({ secrets: [AUTH_API_KEY] }, async (request) => {
  const data = request.data || {};
  const email = typeof data.email === "string" ? data.email.trim() : "";
  const password = typeof data.password === "string" ? data.password : "";
  const code = typeof data.code === "string" ? data.code : "";
  if (!email || !password || !code || email.length > 320 || password.length > 4096 || code.length > 64) {
    throw new HttpsError("invalid-argument", "Enter your email, password and a backup code.");
  }
  // Throttle by account email and by network address BEFORE checking the
  // password, so this endpoint can't be used to guess passwords.
  const { rateLimit } = require("./pairing")._internal;
  const tag = (v) => crypto.createHash("sha256").update(String(v)).digest("hex").slice(0, 32);
  await rateLimit(`redeemEmail_${tag(email.toLowerCase())}`, 10);
  const ip = (request.rawRequest && request.rawRequest.ip) || "";
  if (ip) await rateLimit(`redeemIp_${tag(ip)}`, 20);

  // A signed-in caller may only redeem for their own account.
  const user = await verifyPasswordForMfaAccount(email, password);
  if (request.auth && request.auth.uid !== user.uid) {
    throw new HttpsError("permission-denied", "That backup code is for a different account.");
  }
  const uid = user.uid;
  const factor = totpFactor(user);
  if (!factor) {
    throw new HttpsError("failed-precondition", "Two-factor isn't on for this account. Sign in with your password.");
  }

  const ref = docFor(uid);
  // Check-and-consume atomically so one code can't be redeemed twice in parallel.
  const outcome = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const doc = snap.exists ? snap.data() : null;
    if (!codesValidFor(doc, factor)) return { ok: false, reason: "none" };
    const now = Date.now();
    if (doc.lockedUntil && doc.lockedUntil.toMillis() > now) return { ok: false, reason: "locked" };

    const i = findMatch(doc.codes, code);
    if (i < 0) {
      const failed = (doc.failedAttempts || 0) + 1;
      const locked = failed >= MAX_FAILED_ATTEMPTS;
      tx.update(ref, {
        failedAttempts: locked ? 0 : failed,
        lockedUntil: locked ? Timestamp.fromMillis(now + LOCKOUT_MS) : null,
      });
      return { ok: false, reason: locked ? "locked" : "wrong" };
    }
    const codes = doc.codes.slice();
    codes[i] = { ...codes[i], usedAt: Timestamp.fromMillis(now) };
    tx.update(ref, { codes, failedAttempts: 0, lockedUntil: null });
    return { ok: true, index: i };
  });

  if (!outcome.ok) {
    if (outcome.reason === "locked") {
      throw new HttpsError("resource-exhausted", "Too many wrong backup codes. Try again in 15 minutes.");
    }
    if (outcome.reason === "none") {
      throw new HttpsError("failed-precondition", "This account has no backup codes.");
    }
    throw new HttpsError("permission-denied", "Wrong password or backup code.");
  }

  // Remove the lost authenticator. If that fails, give the code back.
  try {
    await getAuth().updateUser(uid, { multiFactor: { enrolledFactors: null } });
  } catch (e) {
    console.error("Removing TOTP factor failed", e);
    await db().runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const codes = snap.data().codes.slice();
      codes[outcome.index] = { ...codes[outcome.index], usedAt: null };
      tx.update(ref, { codes });
    }).catch(() => {});
    throw new HttpsError("internal", "Couldn't turn off two-factor. Try again.");
  }
  return { ok: true };
});

// Pure helpers, exported for local tests.
exports._test = { newCode, normalizeCode, hashCode, findMatch, makeCodeSet, codesValidFor, totpFactor };
