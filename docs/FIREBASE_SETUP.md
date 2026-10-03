# Firebase configuration checklist

Everything you need to configure in the [Firebase console](https://console.firebase.google.com)
for Watch Dog (parent) and Shield (child).

## 1. Create a project
- Create one Firebase project (e.g. `watchdog-family`). Both apps share it.

## 2. Register the two Android apps
Add **two** Android apps under Project settings → Your apps:

| App | Package name | Put the file in |
|-----|--------------|-----------------|
| Watch Dog (parent) | `com.fms.parentalcontrol.parent` | `parent-app/google-services.json` |
| Shield (child)     | `com.fms.parentalcontrol.child`  | `child-app/google-services.json`  |

Download each `google-services.json` and drop it in the folder shown. These files are
git-ignored — each developer/machine supplies their own.

> SHA-1/SHA-256 fingerprints are **only** needed if you later add Google Sign-In, Phone
> auth, or Dynamic Links. Email/Password and Anonymous do not need them.

## 3. Authentication → Sign-in method
Enable these providers:
- **Email/Password** — used by the parent app (Watch Dog).
- **Anonymous** — used by the child app (Shield). **This is required**, otherwise Shield
  cannot sign in and will show the "Can't connect / Retry" screen.

## 4. Firestore Database
- Create the database (Native mode). Pick a region close to your users (cannot be changed later).
- Publish the rules from [`firebase/firestore.rules`](../firebase/firestore.rules).
- No composite indexes are required yet (the dashboard uses a single `array-contains` query).

Collections used so far:
- `users/{parentUid}` — parent profile.
- `children/{childId}` — child profile, `pairingCode`, `pairingActive`, `linkedParents[]`.

## 5. Storage
- Enable Cloud Storage (note the default bucket).
- Publish the rules from [`firebase/storage.rules`](../firebase/storage.rules).
- Used for profile avatars at `avatars/{uid}.jpg`.

## 6. CI (optional, for real-backend APKs)
By default CI builds against committed placeholders. To build APKs that reach your project,
add these repository secrets (Settings → Secrets and variables → Actions):
```bash
base64 -w0 parent-app/google-services.json   # -> GOOGLE_SERVICES_PARENT_B64
base64 -w0 child-app/google-services.json     # -> GOOGLE_SERVICES_CHILD_B64
```

## Needed in later steps (not required yet)
- **Cloud Messaging (FCM)** — no console setup beyond it being enabled; used for geofence
  alerts (step 5) and the remote screenshot command (step 6).
- **Cloud Functions** (requires the **Blaze** pay-as-you-go plan) — recommended to harden
  pairing and to relay geofence/screenshot events securely. See the security note below.
- **App Check** (recommended) — register both apps (Play Integrity) and enable enforcement
  for Firestore/Storage to block abuse.

## Security note (current pairing)
Pairing is currently done **client-side**: the parent app reads the child doc, verifies the
scanned code, and adds itself to `linkedParents` (rules permit changing only that field while
`pairingActive`). This is fine for development. For production, move linking into a **Cloud
Function** so the code is validated server-side and child docs aren't broadly readable.
