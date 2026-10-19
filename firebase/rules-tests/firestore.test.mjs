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
    for (const c of ["pairingInvites", "rateLimits", "childMonitor", "mfaBackupCodes"]) {
      await assertFails(getDoc(doc(parent(), `${c}/x`)));
      await assertFails(setDoc(doc(parent(), `${c}/x`), { a: 1 }));
    }
  });
  it("child cannot set a long profile name (push-text abuse) or another role", async () => {
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { "profile.name": "x".repeat(61) }));
    await assertFails(updateDoc(doc(child(), `children/${CHILD}`), { role: "PARENT" }));
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
  });
  it("key resync request and clear", async () => {
    await assertSucceeds(setDoc(doc(parent(), `children/${CHILD}/keyResync/${PARENT}`), { at: 1 }));
    await assertSucceeds(deleteDoc(doc(child(), `children/${CHILD}/keyResync/${PARENT}`)));
  });
});
