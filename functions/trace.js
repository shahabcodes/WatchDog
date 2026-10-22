/**
 * Server-side diagnostics, written to the same `diagEvents` collection as the
 * apps' (app "server"), so one timeline shows what each phone did and what the
 * server did in response.
 *
 *  - onCall(): drop-in for firebase-functions' onCall that records each call:
 *    function name, caller, outcome (ok / error code) and duration.
 *  - serverDiag(): record any other server event (pushes sent, alerts, restores).
 *
 * PRIVACY: ids, outcomes and error codes only; never request data (PINs,
 * passwords, codes) or decrypted content.
 */
const { onCall: rawOnCall, HttpsError } = require("firebase-functions/v2/https");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");

const KEEP_MS = 30 * 24 * 60 * 60 * 1000;
const VERSION = (process.env.K_REVISION || "").slice(0, 32);

/** The deployed function's name (each 2nd-gen function runs as its own service). */
function functionName() {
  return process.env.FUNCTION_TARGET || process.env.K_SERVICE || "function";
}

/** Records one server event for [uid] (the account it concerns; "" if none). */
async function serverDiag(level, tag, message, uid = "") {
  try {
    const now = Date.now();
    await getFirestore().collection("diagEvents").add({
      at: FieldValue.serverTimestamp(),
      atMillis: now,
      expireAt: Timestamp.fromMillis(now + KEEP_MS),
      level,
      tag: String(tag).slice(0, 40),
      message: String(message).slice(0, 500),
      exception: "",
      uid: uid || "",
      install: "server",
      app: "server",
      version: VERSION,
      model: functionName().slice(0, 60),
      sdk: 0,
    });
  } catch (e) {
    console.error("serverDiag failed", e);
  }
}

/** onCall with a diagnostics record per call. Same signature as firebase-functions' onCall. */
function onCall(optsOrHandler, maybeHandler) {
  const handler = maybeHandler || optsOrHandler;
  const traced = async (request) => {
    const start = Date.now();
    const uid = (request.auth && request.auth.uid) || "";
    try {
      const result = await handler(request);
      await serverDiag("INFO", "call", `${functionName()} ok ${Date.now() - start}ms`, uid);
      return result;
    } catch (e) {
      const ms = Date.now() - start;
      if (e instanceof HttpsError) {
        const level = e.code === "internal" ? "ERROR" : "WARN";
        await serverDiag(level, "call", `${functionName()} ${e.code}: ${e.message} ${ms}ms`, uid);
      } else {
        await serverDiag("ERROR", "call", `${functionName()} crashed: ${(e && e.message) || e} ${ms}ms`, uid);
      }
      throw e;
    }
  };
  return maybeHandler ? rawOnCall(optsOrHandler, traced) : rawOnCall(traced);
}

module.exports = { onCall, serverDiag };
