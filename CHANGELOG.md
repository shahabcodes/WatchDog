# Changelog

All notable changes to Watch Dog (parent) and Shield (child) are recorded here.
Versions are `0.1.<build>`, where `<build>` is the CI build number. Each CI build
on `main` publishes a versioned pre-release (kept as history) plus a rolling
`latest` release.

## Unreleased / latest

### Added
- **Travel history**: Shield records places the child stays at for 5+ minutes;
  Watch Dog shows them as a timeline (via the live-location card's ⋯ menu).
- **Live child location** on Watch Dog — a Family-Link-style map card on the
  child's page plus a live marker on the full map.
- Loading indicators across the app (dashboard, child apps list, screen time,
  travel history).
- iOS-style three-dot overflow menus on the live-location card and saved places.
- **Ring the child's device**: a full-screen alarm over the lock screen with an
  optional message. The child stops it with the on-screen button, the **volume**
  keys, or the **power** button.
- Guided setup now **requests every permission it needs** (notifications,
  full-screen alarm, location) and asks on app start when something is missing.
- **About** section in both apps' Settings showing the installed version and an
  in-app release history.
- Confirmation dialogs before deleting a place or removing an app rule.
- Undo (with a snackbar) while drawing a geofence.

### Changed
- Redesigned the map's Pin/Area control as a modern iOS segmented control with
  a sliding thumb.
- Warmer, more colorful UI: tinted icon badges, online status dots, refreshed
  cards, consistent spacing.
- The schedule editor supports **multiple allowed-hours windows** per app.
- Ring now lives beside the live-location card, not inside the full map.

## 0.2 (earlier)
- Custom map markers and polygon geofence areas.
- Screen-time usage reports and a geofence activity feed.
- Modern map editor and UI polish pass.

## 0.1 (initial)
- QR-code pairing and family profiles (name, photo, DOB).
- App time limits, total daily limits, blocking and schedules.
- Location geofences with enter/leave alerts.
- Firebase backend (Auth + Firestore); dark/light themes and accent palettes.
