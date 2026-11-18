/**
 * One child with several phones ("kid"). Each Shield phone keeps its own child
 * record, account and keys; a kid groups them so Watch Dog shows one child and
 * writes shared rules to every phone. The group lives on each phone's record as
 * `kid: { id, name, phones }`, written only here (firestore.rules lets neither
 * the child nor a parent write it).
 *
 *  - mergeChildren: a parent joins phones (or adds a phone to an existing kid).
 *    The caller must be a parent of every phone, and every phone must have the
 *    same parents, so no parent ends up supervising only part of a child.
 *  - unmergeChild: a parent takes one phone back out; it keeps its rules.
 *  - detachFromKid: used when a phone loses its last parent.
 */
const crypto = require("crypto");
const { HttpsError } = require("firebase-functions/v2/https");
const { onCall } = require("./trace");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const pairing = require("./pairing")._internal;

const MAX_PHONES = 6;
const db = () => getFirestore();

function sameParents(a, b) {
  const x = [...(a || [])].sort();
  const y = [...(b || [])].sort();
  return x.length === y.length && x.every((v, i) => v === y[i]);
}

function cleanName(name) {
  if (typeof name !== "string") return "";
  return name.trim().replace(/\s+/g, " ").slice(0, 60);
}

/**
 * Data: { childIds: [id, ...], name }. Joins the phones (and the other phones of
 * any kid one of them is already in) into one kid. Returns { kidId, phones }.
 */
exports.mergeChildren = onCall(async (request) => {
  const parentId = request.auth && request.auth.uid;
  if (!parentId) throw new HttpsError("unauthenticated", "Sign in required.");
  if (pairing.isAnonymous(request.auth)) {
    throw new HttpsError("permission-denied", "Sign in with your parent account.");
  }
  const data = request.data || {};
  const asked = Array.isArray(data.childIds) ? data.childIds : [];
  const ids = [...new Set(asked.map((id) => pairing.requireId(id, "child")))];
  if (ids.length < 2) throw new HttpsError("invalid-argument", "Pick at least two phones.");
  if (ids.length > MAX_PHONES) throw new HttpsError("invalid-argument", `A child can have up to ${MAX_PHONES} phones.`);
  await pairing.rateLimit(`merge_${parentId}`, 20);

  return db().runTransaction(async (tx) => {
    const refs = ids.map((id) => db().doc(`children/${id}`));
    const snaps = await Promise.all(refs.map((r) => tx.get(r)));
    // Phones already in a kid bring that kid's other phones along.
    const extra = new Set();
    for (const s of snaps) {
      if (!s.exists) throw new HttpsError("not-found", "One of the phones was not found.");
      const kid = s.get("kid");
      (kid && Array.isArray(kid.phones) ? kid.phones : []).forEach((p) => {
        if (typeof p === "string" && !ids.includes(p)) extra.add(p);
      });
    }
    const extraIds = [...extra].filter((id) => /^[A-Za-z0-9_-]{1,128}$/.test(id));
    const extraSnaps = await Promise.all(extraIds.map((id) => tx.get(db().doc(`children/${id}`))));
    const all = snaps.concat(extraSnaps.filter((s) => s.exists));
    if (all.length > MAX_PHONES) throw new HttpsError("invalid-argument", `A child can have up to ${MAX_PHONES} phones.`);

    const parents = all[0].get("linkedParents") || [];
    for (const s of all) {
      const p = s.get("linkedParents") || [];
      if (!p.includes(parentId)) throw new HttpsError("permission-denied", "You are not linked to every phone.");
      if (!sameParents(p, parents)) {
        throw new HttpsError(
          "failed-precondition",
          "These phones have different parents. Add the same parents to every phone first, then merge them.",
        );
      }
    }

    const existing = all.map((s) => s.get("kid")).find((k) => k && typeof k.id === "string" && k.id);
    const kidId = existing ? existing.id : crypto.randomBytes(12).toString("hex");
    const name = cleanName(data.name) || (existing && cleanName(existing.name)) ||
      cleanName((all[0].get("profile") || {}).name) || "Child";
    const phones = all.map((s) => s.id);
    for (const s of all) tx.update(s.ref, { kid: { id: kidId, name, phones } });
    return { kidId, phones };
  });
});

/** Takes [childId] out of its kid; a kid left with one phone is dissolved. */
async function detachFromKid(childId) {
  await db().runTransaction(async (tx) => {
    const ref = db().doc(`children/${childId}`);
    const snap = await tx.get(ref);
    const kid = snap.exists && snap.get("kid");
    if (!kid) return;
    const rest = (Array.isArray(kid.phones) ? kid.phones : []).filter((p) => p !== childId && typeof p === "string");
    const restSnaps = await Promise.all(rest.map((id) => tx.get(db().doc(`children/${id}`))));
    const alive = restSnaps.filter((s) => s.exists && s.get("kid") && s.get("kid").id === kid.id);
    tx.update(ref, { kid: FieldValue.delete() });
    if (alive.length >= 2) {
      const phones = alive.map((s) => s.id);
      alive.forEach((s) => tx.update(s.ref, { "kid.phones": phones }));
    } else {
      alive.forEach((s) => tx.update(s.ref, { kid: FieldValue.delete() }));
    }
  });
}

/** Data: { childId }. A parent of the phone takes it out of its kid. */
exports.unmergeChild = onCall(async (request) => {
  const parentId = request.auth && request.auth.uid;
  if (!parentId) throw new HttpsError("unauthenticated", "Sign in required.");
  const childId = pairing.requireId((request.data || {}).childId, "child");
  const snap = await db().doc(`children/${childId}`).get();
  if (!snap.exists) throw new HttpsError("not-found", "That phone was not found.");
  if (!(snap.get("linkedParents") || []).includes(parentId)) {
    throw new HttpsError("permission-denied", "You are not linked to this phone.");
  }
  await detachFromKid(childId);
  return { ok: true };
});

/** The name to show for a child record: its kid's name, else its own. */
function displayName(child, fallback) {
  const kid = child && child.kid;
  const name = (kid && cleanName(kid.name)) || cleanName(child && child.profile && child.profile.name);
  return name || fallback;
}

exports._internal = { detachFromKid, displayName, sameParents };
