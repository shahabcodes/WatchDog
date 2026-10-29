// Firestore rules tests: each attack found in the 2026-10 review must be refused,
// and every legitimate write the apps make must still be allowed.
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where,
} from "firebase/firestore";

const CHILD = "child1";
const PARENT = "parent1";
const STRANGER = "stranger";

let env;
const as = (uid, provider = "password") =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: provider } }).firestore();
const child = () => as(CHILD, "anonymous");
const parent = () => as(PARENT);
const stranger = () => as(STRANGER, "anonymous");

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-watchdog",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});
after(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `children/${CHILD}`), {
      profile: { name: "Emma" }, role: "CHILD", linkedParents: [PARENT],
      pairingActive: false, encActive: false, device: { online: true },
    });
    await setDoc(doc(db, `children/${CHILD}/requests/r1`), {
      kind: "MORE_TIME", appPackage: "", appLabel: "", minutes: 15,
      status: "PENDING", createdAtMillis: 1, respondedAtMillis: 0,
    });
    await setDoc(doc(db, `children/${CHILD}/appRules/a1`), { e: "x", enc: true, updatedAtMillis: 1 });
    await setDoc(doc(db, `children/${CHILD}/live/ring`), { active: true, message: "", nonce: "n", requestedAtMillis: 1 });
    await setDoc(doc(db, `publicKeys/${PARENT}`), { e2ePublicKey: "pk" });
  });
});

describe("attacks are refused", () => {
  it("child cannot change its own linkedParents", async () => {
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { linkedParents: [] }));
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { linkedParents: [PARENT, STRANGER] }));
  });
  it("child cannot reopen pairing or set a pairing code", async () => {
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { pairingActive: true }));
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { pairingCode: "ABCDEFGH" }));
  });
  it("child cannot delete its own doc", async () => {
    await assertFails(deleteDoc(doc(child(), `children/${CHILD}`)));
  });
  it("anyone creating a fake child cannot list parents on it", async () => {
    await assertFails(setDoc(doc(stranger(), `children/${STRANGER}`), {
      profile: { name: "Fake" }, linkedParents: [PARENT],
    }));
  });
  it("publicKeys cannot be listed", async () => {
    await assertFails(getDocs(collection(stranger(), "publicKeys")));
  });
  it("child cannot approve its own request", async () => {
    await assertFails(updateDoc(doc(child(), `children/${CHILD}/requests/r1`), {
      status: "APPROVED", grantUntilMillis: 4102444800000, respondedAtMillis: 1,
    }));
  });
  it("child cannot create a pre-approved request", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/requests/r2`), {
      kind: "EXTEND_BEDTIME", appPackage: "", appLabel: "", minutes: 0,
      status: "APPROVED", createdAtMillis: 1, respondedAtMillis: 0,
    }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/requests/r3`), {
      kind: "EXTEND_BEDTIME", appPackage: "", appLabel: "", minutes: 0,
      status: "PENDING", createdAtMillis: 1, respondedAtMillis: 0, grantUntilMillis: 4102444800000,
    }));
  });
  it("child cannot write or delete app rules, geofences or settings", async () => {
    await assertFails(deleteDoc(doc(child(), `children/${CHILD}/appRules/a1`)));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/appRules/a2`), { e: "x", enc: true }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/geofences/g1`), { e: "x", enc: true }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/settings/protection`), { uninstallBlocked: false }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/settings/focus`), { e: "x", enc: true }));
  });
  it("child cannot clear the pending-app review list or delete history", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), `children/${CHILD}/pendingApps/com.x`), { packageName: "com.x" }));
    await assertFails(deleteDoc(doc(child(), `children/${CHILD}/pendingApps/com.x`)));
  });
  it("child cannot switch off its parents' alerts or the settings lock", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/settings/alerts`), { protectionOff: false, fakeGps: false }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/settings/protection`), { lockSettings: false }, { merge: true }));
  });
  it("nobody can read or write PIN hashes from an app", async () => {
    await assertFails(getDoc(doc(child(), `pinSecrets/${CHILD}`)));
    await assertFails(getDoc(doc(parent(), `pinSecrets/${CHILD}`)));
    await assertFails(setDoc(doc(parent(), `children/${CHILD}/settings/protection`), { pinHash: "h", pinSalt: "s" }, { merge: true }));
    await assertFails(setDoc(doc(parent(), `children/${CHILD}/settings/protection`), { hasPin: false }, { merge: true }));
  });
  it("child cannot put long text into new-app notifications", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/pendingApps/com.spam`), {
      packageName: "com.spam", label: "x".repeat(101), installedAtMillis: 1,
    }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/pendingApps/com.extra`), {
      packageName: "com.extra", label: "ok", installedAtMillis: 1, body: "phishing text",
    }));
  });
  it("child-written collections only take the expected ids and recipients", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/usage/junk-1`), { e: "x", enc: true }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/trail/anything`), { points: [] }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/inventory/other`), { e: "x" }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/e2eeKeys/${STRANGER}`), { wrapped: "w" }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/e2eeKeys/${STRANGER}/devices/d1`), { wrapped: "w" }));
    await assertSucceeds(deleteDoc(doc(child(), `children/${CHILD}/e2eeKeys/${STRANGER}`)));
  });
  it("user profile is a small fixed shape", async () => {
    await assertSucceeds(setDoc(doc(parent(), `users/${PARENT}`), { name: "Dad", photoUrl: null, dateOfBirthMillis: null, role: "PARENT" }));
    await assertFails(setDoc(doc(parent(), `users/${PARENT}`), { name: "Dad", blob: "x".repeat(1000) }));
    await assertFails(setDoc(doc(parent(), `users/${PARENT}`), { name: "x".repeat(101) }));
  });
  it("a co-parent can't read another parent's link request or resync; removals are visible to the family", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, `children/${CHILD}/parentRequests/${STRANGER}`), { email: "x@y" });
      await setDoc(doc(db, `children/${CHILD}/keyResync/${STRANGER}`), { at: 1 });
      await setDoc(doc(db, `children/${CHILD}/parentRemovals/${STRANGER}`), { email: "x@y" });
    });
    await assertFails(getDoc(doc(parent(), `children/${CHILD}/parentRequests/${STRANGER}`)));
    await assertFails(getDoc(doc(parent(), `children/${CHILD}/keyResync/${STRANGER}`)));
    await assertSucceeds(getDoc(doc(parent(), `children/${CHILD}/parentRemovals/${STRANGER}`)));
    await assertSucceeds(getDoc(doc(child(), `children/${CHILD}/parentRemovals/${STRANGER}`)));
    await assertFails(getDoc(doc(stranger(), `children/${CHILD}/parentRemovals/${STRANGER}`)));
    await assertFails(setDoc(doc(parent(), `children/${CHILD}/parentRemovals/${STRANGER}`), { email: "x" }));
  });
  it("restore: only the install reads its token; ring handoff is parent-to-own-doc, child reads", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `restoreTokens/${STRANGER}`), { token: "t", oldChildId: CHILD });
    });
    await assertSucceeds(getDoc(doc(stranger(), `restoreTokens/${STRANGER}`)));
    await assertFails(getDoc(doc(parent(), `restoreTokens/${STRANGER}`)));
    await assertFails(getDoc(doc(child(), `restoreTokens/${STRANGER}`)));
    await assertFails(setDoc(doc(stranger(), `restoreTokens/${STRANGER}`), { token: "forged" }));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/ringImport/${PARENT}`), { wrapped: "w", senderPub: "p", at: 1 }));
    await assertFails(setDoc(doc(parent(), `children/${CHILD}/ringImport/${STRANGER}`), { wrapped: "w", senderPub: "p", at: 1 }));
    await assertFails(setDoc(doc(stranger(), `children/${CHILD}/ringImport/${STRANGER}`), { wrapped: "w", senderPub: "p", at: 1 }));
    await assertFails(setDoc(doc(child(), `children/${CHILD}/ringImport/${PARENT}`), { wrapped: "w", senderPub: "p", at: 1 }));
    await assertSucceeds(getDoc(doc(child(), `children/${CHILD}/ringImport/${PARENT}`)));
    await assertSucceeds(deleteDoc(doc(child(), `children/${CHILD}/ringImport/${PARENT}`)));
  });
  it("diagnostics: apps add their own bounded events and can't read any", async () => {
    const ev = (uid) => ({ at: 1, atMillis: 1, expireAt: 1, level: "WARN", tag: "e2ee", message: "m", exception: "",
      uid, install: "i", app: "com.shahaabapps.watchdog", version: "0.1.0", model: "Pixel", sdk: 35 });
    await assertSucceeds(setDoc(doc(parent(), "diagEvents/e1"), ev(PARENT)));
    await assertFails(setDoc(doc(parent(), "diagEvents/e2"), ev(CHILD)));
    await assertFails(setDoc(doc(parent(), "diagEvents/e3"), { ...ev(PARENT), message: "x".repeat(501) }));
    await assertFails(setDoc(doc(parent(), "diagEvents/e4"), { ...ev(PARENT), extra: 1 }));
    await assertFails(getDoc(doc(parent(), "diagEvents/e1")));
    await assertFails(getDocs(collection(child(), "diagEvents")));
  });
  it("a waiting restore is visible to the child's parents only, and only the server writes it", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `pendingRestores/${CHILD}`), { newChildId: "n", parentId: PARENT });
    });
    await assertSucceeds(getDoc(doc(parent(), `pendingRestores/${CHILD}`)));
    await assertFails(getDoc(doc(stranger(), `pendingRestores/${CHILD}`)));
    await assertFails(setDoc(doc(parent(), `pendingRestores/${CHILD}`), { newChildId: "x" }));
    await assertFails(deleteDoc(doc(parent(), `pendingRestores/${CHILD}`)));
  });
  it("only the owner can delete their shared key", async () => {
    await assertSucceeds(setDoc(doc(parent(), `publicKeys/${PARENT}`), { e2ePublicKey: "pk", aad2: true }));
    await assertFails(deleteDoc(doc(stranger(), `publicKeys/${PARENT}`)));
    await assertFails(deleteDoc(doc(child(), `publicKeys/${PARENT}`)));
    await assertSucceeds(deleteDoc(doc(parent(), `publicKeys/${PARENT}`)));
  });
  it("nobody can publish a device key for someone else, or wrap keys as a parent", async () => {
    await assertFails(setDoc(doc(stranger(), `publicKeys/${PARENT}/devices/evil`), { e2ePublicKey: "attacker", aad2: true }));
    await assertFails(setDoc(doc(child(), `publicKeys/${PARENT}/devices/evil`), { e2ePublicKey: "attacker" }));
    await assertFails(setDoc(doc(parent(), `children/${CHILD}/e2eeKeys/${PARENT}/devices/dev1`), { wrapped: "w" }));
    await assertFails(getDoc(doc(stranger(), `children/${CHILD}/e2eeKeys/${PARENT}/devices/dev1`)));
  });
  it("child cannot change its home time zone (schedules follow it)", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/settings/timezone`), { zoneId: "Pacific/Kiritimati" }));
  });
  it("catch-all no longer lets the child write anywhere", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/anything/x`), { a: 1 }));
  });
  it("child cannot issue parent commands", async () => {
    await assertFails(setDoc(doc(child(), `children/${CHILD}/commands/screenshot`), { nonce: 1 }));
  });
  it("parent cannot rewrite linkedParents directly (unlink goes through a function)", async () => {
    await assertFails(updateDoc(doc(parent(), `children/${CHILD}`), { linkedParents: [], pairingActive: true }));
  });
  it("parent cannot forge the child's location", async () => {
    await assertFails(setDoc(doc(parent(), `children/${CHILD}/live/location`), { latitude: 1, longitude: 1 }));
  });
  it("stranger cannot read the child or its requests", async () => {
    await assertFails(getDoc(doc(stranger(), `children/${CHILD}`)));
    await assertFails(getDoc(doc(stranger(), `children/${CHILD}/requests/r1`)));
  });
  it("server-only collections are closed", async () => {
    for (const c of ["pairingInvites", "rateLimits", "childMonitor", "mfaBackupCodes", "pinSecrets"]) {
      await assertFails(getDoc(doc(parent(), `${c}/x`)));
      await assertFails(setDoc(doc(parent(), `${c}/x`), { a: 1 }));
    }
  });
  it("child cannot set a long profile name (push-text abuse) or another role", async () => {
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { "profile.name": "x".repeat(61) }));
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { role: "PARENT" }));
  });
  it("blockAttempts stay sealed, unedited and private to the family", async () => {
    const path = `children/${CHILD}/blockAttempts/b1`;
    // Which app was blocked must never reach the server in plaintext.
    await assertFails(setDoc(doc(child(), path), { enc: true, e: "sealed", atMillis: 1, label: "TikTok" }));
    await assertFails(setDoc(doc(child(), path), { packageName: "com.zhiliaoapp.musically", atMillis: 1 }));
    await assertFails(setDoc(doc(child(), path), { enc: false, e: "sealed", atMillis: 1 }));
    await assertFails(setDoc(doc(child(), path), { enc: true, e: "x".repeat(4096), atMillis: 1 }));
    await assertFails(setDoc(doc(parent(), path), { enc: true, e: "sealed", atMillis: 1 }));
    await assertFails(setDoc(doc(stranger(), path), { enc: true, e: "sealed", atMillis: 1 }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), path), { enc: true, e: "sealed", atMillis: 1 });
    });
    await assertFails(getDoc(doc(stranger(), path)));
    await assertFails(getDocs(collection(stranger(), `children/${CHILD}/blockAttempts`)));
    await assertFails(updateDoc(doc(child(), path), { e: "other" }));
    await assertFails(deleteDoc(doc(child(), path)));
    await assertFails(deleteDoc(doc(stranger(), path)));
  });
});

describe("legitimate app writes still work", () => {
  it("child creates its doc unsupervised and saves its profile", async () => {
    const db = as("child2", "anonymous");
    await assertSucceeds(setDoc(doc(db, "children/child2"), { profile: { name: "Sam" }, role: "CHILD" }, { merge: true }));
    await assertSucceeds(setDoc(doc(db, "children/child2"), { profile: { name: "Sammy" }, role: "CHILD" }, { merge: true }));
  });
  it("child heartbeat, permissions and guard flags", async () => {
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}`), {
      device: { online: true, lastSeenMillis: 2, hb: "x", hbEnc: true, guard: {
        accessibility: true, usageAccess: true, admin: true, clockOk: true, gpsGenuine: true, tz: "Asia/Dubai", homeTz: "Asia/Dubai",
      } },
      encActive: true,
    }, { merge: true }));
  });
  it("child creates and withdraws a pending request", async () => {
    const ref = doc(child(), `children/${CHILD}/requests/r9`);
    await assertSucceeds(setDoc(ref, {
      kind: "UNLOCK_APP", appPackage: "com.game", appLabel: "Game", minutes: 30,
      status: "PENDING", createdAtMillis: 5, respondedAtMillis: 0,
    }));
    await assertSucceeds(deleteDoc(ref));
  });
  it("parent approves and denies requests", async () => {
    await assertSucceeds(updateDoc(doc(parent(), `children/${CHILD}/requests/r1`), {
      status: "APPROVED", respondedAtMillis: 9, grantUntilMillis: 0,
    }));
  });
  it("parent manages rules, geofences, settings", async () => {
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/appRules/a2`), { e: "x", enc: true, updatedAtMillis: 1 }));
    await assertSucceeds(deleteDoc(doc(parent(), `children/${CHILD}/appRules/a1`)));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/geofences/g1`), { e: "x", enc: true, radiusMeters: 100 }));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/settings/tracking`), { mode: "x", updatedAtMillis: 1 }));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/settings/timezone`), { zoneId: "Asia/Dubai", updatedAtMillis: 1 }));
    await assertSucceeds(getDoc(doc(child(), `children/${CHILD}/settings/timezone`)));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/settings/alerts`), { protectionOff: true, clockChanged: false, fakeGps: true, travel: true, noCheckIn: false }));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/settings/protection`), { uninstallBlocked: false, lockSettings: true }, { merge: true }));
  });
  it("live: child location, parent ring, child stops ring", async () => {
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/live/location`), { e: "x", enc: true, accuracyMeters: 5, timestampMillis: 1 }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/live/locationStatus`), { state: "tracking" }));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/live/ring`), { active: true, message: "hi", nonce: "n2", requestedAtMillis: 2 }));
    await assertSucceeds(updateDoc(doc(child(), `children/${CHILD}/live/ring`), { active: false }));
  });
  it("commands: parent issues, child reports screenshot status", async () => {
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/commands/screenshot`), { nonce: 1 }));
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/commands/location`), { nonce: 1 }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/commands/screenshotStatus`), { status: "ok", atMillis: 1 }));
  });
  it("child-written telemetry collections", async () => {
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/trail/2026-10-19`), { points: [] }, { merge: true }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/usage/2026-10-19`), { e: "x", enc: true }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/inventory/installed`), { e: "x", enc: true }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/travelHistory/t1`), { e: "x", enc: true }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/geofenceEvents/e1`), { geofenceId: "g", transition: "EXIT", timestampMillis: 1 }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/screenshots/s1`), { data: "x", capturedAtMillis: 1, enc: true }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/pendingApps/com.new`), { packageName: "com.new", label: "New", installedAtMillis: 1 }));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/e2eeKeys/${PARENT}`), { wrapped: "w" }));
  });
  it("blockAttempts: child records a sealed attempt, parent reads and clears it", async () => {
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/blockAttempts/b1`), { enc: true, e: "sealed", atMillis: 1 }));
    await assertSucceeds(getDoc(doc(parent(), `children/${CHILD}/blockAttempts/b1`)));
    await assertSucceeds(getDocs(collection(parent(), `children/${CHILD}/blockAttempts`)));
    await assertSucceeds(deleteDoc(doc(parent(), `children/${CHILD}/blockAttempts/b1`)));
  });
  it("parent clears events, screenshots and reviewed apps", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, `children/${CHILD}/geofenceEvents/e1`), { a: 1 });
      await setDoc(doc(db, `children/${CHILD}/screenshots/s1`), { a: 1 });
      await setDoc(doc(db, `children/${CHILD}/pendingApps/com.x`), { a: 1 });
    });
    await assertSucceeds(deleteDoc(doc(parent(), `children/${CHILD}/geofenceEvents/e1`)));
    await assertSucceeds(deleteDoc(doc(parent(), `children/${CHILD}/screenshots/s1`)));
    await assertSucceeds(deleteDoc(doc(parent(), `children/${CHILD}/pendingApps/com.x`)));
  });
  it("parent lists its children and reads data; child reads its parent requests", async () => {
    await assertSucceeds(getDocs(query(collection(parent(), "children"), where("linkedParents", "array-contains", PARENT))));
    await assertSucceeds(getDocs(collection(parent(), `children/${CHILD}/requests`)));
    await assertSucceeds(getDocs(collection(child(), `children/${CHILD}/parentRequests`)));
  });
  it("public keys: fetch by id, write own", async () => {
    await assertSucceeds(getDoc(doc(child(), `publicKeys/${PARENT}`)));
    await assertSucceeds(setDoc(doc(child(), `publicKeys/${CHILD}`), { e2ePublicKey: "ck" }, { merge: true }));
    await assertSucceeds(setDoc(doc(parent(), `publicKeys/${PARENT}`), { e2ePublicKey: "pk", aad2: true }, { merge: true }));
    await assertFails(setDoc(doc(parent(), `publicKeys/${PARENT}`), { e2ePublicKey: "pk", other: 1 }, { merge: true }));
  });
  it("per-device keys: owner publishes/removes, child wraps per device", async () => {
    await assertSucceeds(setDoc(doc(parent(), `publicKeys/${PARENT}/devices/dev1`), { e2ePublicKey: "pk", aad2: true, model: "Pixel 8", updatedAt: 1 }));
    await assertSucceeds(getDocs(collection(child(), `publicKeys/${PARENT}/devices`)));
    await assertSucceeds(setDoc(doc(child(), `children/${CHILD}/e2eeKeys/${PARENT}/devices/dev1`), { wrapped: "w", ringFp: "r", parentKeyFp: "f", updatedAt: 1 }));
    await assertSucceeds(getDoc(doc(parent(), `children/${CHILD}/e2eeKeys/${PARENT}/devices/dev1`)));
    await assertSucceeds(deleteDoc(doc(parent(), `publicKeys/${PARENT}/devices/dev1`)));
  });
  it("key resync request and clear", async () => {
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/keyResync/${PARENT}`), { at: 1 }));
    await assertSucceeds(deleteDoc(doc(child(), `children/${CHILD}/keyResync/${PARENT}`)));
  });
});
