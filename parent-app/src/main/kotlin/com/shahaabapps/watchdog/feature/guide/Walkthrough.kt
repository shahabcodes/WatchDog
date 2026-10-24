package com.shahaabapps.watchdog.feature.guide

import com.shahaabapps.core.ui.tour.TourStep

/**
 * The on-screen walk-through: every control, one at a time, over the real
 * screens. Copy follows [AppGuideContent] (the chapter guide stays as the
 * long-form reference). Screens are ParentShell's tour keys; scenes are opened
 * by the screens themselves via TourScene. Steps whose control isn't showing
 * (no data yet, a card that only appears sometimes) are skipped automatically.
 */
object Walkthrough {

    // Screen keys understood by ParentShell's TourNavigator.
    private const val HOME = "overview"
    private const val MAP = "map"
    private const val ACTIVITY = "screentime"
    private const val ALERTS = "alerts"
    private const val MANAGE = "manage"
    private const val SETTINGS = "settings"
    private const val DIAGNOSTICS = "diagnostics"

    private fun step(screen: String, target: String, title: String, text: String, scene: String? = null) =
        TourStep(id = target, screen = screen, target = target, title = title, text = text, scene = scene)

    val steps: List<TourStep> = listOf(
        // ---- Home ----
        step(HOME, "home.switcher", "Switch children", "Tap the name pill to open \"Your children\". Pick a child and the whole app switches to them."),
        step(HOME, "home.bell", "Notifications", "The bell opens this child's requests and place alerts. Its orange dot is always there, so it doesn't mean unread alerts."),
        step(HOME, "home.verify", "Finish securing", "Tap \"Verify\", then scan the code from Shield → Me → Settings → \"Show secure code\" on your child's phone. The banner goes once it's verified."),
        step(HOME, "home.hero", "The live map", "Where your child is right now. Tap it to open the Map tab, or swipe down on Home for a fresh location."),
        step(HOME, "home.status", "Status at a glance", "\"Online\" or \"Offline\", the place they're at (or \"Outside known areas\"), their battery and when it last updated."),
        step(HOME, "nav.bar", "Getting around", "\"Home\" is the dashboard, \"Map\" shows places and live location, \"Activity\" shows screen time. All three follow the child you picked."),
        step(HOME, "home.ring", "Ring", "Type a message if you like, then tap \"Ring now\". Their phone plays a loud alarm, even on silent, and shows your message."),
        step(HOME, "home.locate", "Locate", "Jumps to the Map tab. It doesn't ask the phone for a new location."),
        step(HOME, "home.refresh", "Refresh", "Asks your child's phone for a fresh, precise location. It may arrive a moment after the spinner stops."),
        step(HOME, "home.focus", "Focus", "Edits the Bedtime and School schedules, the hours when apps pause."),

        // ---- Focus sheet ----
        step(HOME, "focus.zone", "Time zone", "Schedules follow your child's home time zone. Tap to pick \"Use the phone's zone\" or search for a city; it saves straight away.", scene = "home.focus"),
        step(HOME, "focus.bedtime", "Bedtime", "Switch it on, tap \"Start\" or \"End\" to pick times, and tap M T W T F S S for the days. \"Allowed apps\" stay usable.", scene = "home.focus"),
        step(HOME, "focus.school", "School", "Works like Bedtime: apps are blocked during these hours, but phone calls still work.", scene = "home.focus"),
        step(HOME, "focus.save", "Save", "Sends both modes to your child's phone. Swipe the sheet away without saving and your changes are lost.", scene = "home.focus"),

        // ---- Home cards ----
        step(HOME, "home.protectionIncomplete", "Protection incomplete", "Appears when something is off on your child's phone, like app blocking or a fake-location app. Most fixes are in Shield → Me → SET UP."),
        step(HOME, "home.locationHealth", "Location health", "A green tick means location is updating normally. An orange icon explains the problem and how to fix it on their phone."),
        step(HOME, "home.screentime", "Screen time today", "Today's total compared with yesterday, and the top apps. Open Activity for every app."),
        step(HOME, "home.pendingApps", "New apps to review", "New apps stay blocked until you decide. Tap \"Allow\" or \"Keep blocked\"."),
        step(HOME, "shot.capture", "See their screen", "Tap \"Capture\" for a snapshot of what's on their screen now. It needs the phone online and Accessibility on for Shield."),
        step(HOME, "shot.gallery", "Your screenshots", "Tap one to open it full screen. \"Save all\" copies them to Pictures/WatchDog; they're kept on this phone for 14 days."),
        step(HOME, "home.focusCard", "Focus modes", "Your Bedtime and School schedules at a glance. Tap to edit them."),
        step(HOME, "home.protection", "Prevent uninstall", "Set a removal PIN so Shield can't be removed, lock Shield's settings, block new apps and choose your safety alerts."),
        step(HOME, "home.installs", "App installs", "Block apps installed from outside the Play Store, or allow only the ones you pick."),
        step(HOME, "home.week", "This week", "A 7-day screen-time chart with the daily average."),
        step(HOME, "home.battery", "Battery", "The level, whether it's charging or how fast it drains, and recent hours. Tap the header for an hour-by-hour list."),
        step(HOME, "home.temperature", "Temperature", "\"Normal\", \"Warm\" or \"Running hot\", with the peak reading. Tap the header to see each reading."),
        step(HOME, "home.tracking", "Location tracking", "Pick \"Saver\", \"Balanced\" or \"High\". \"Balanced\" is recommended: precise while moving, light on battery when still."),
        step(HOME, "home.permissions", "Permissions", "What Shield is allowed to do on their phone, with a green tick or an orange warning when something is missing."),
        step(HOME, "home.permRequest", "Request", "Sends your child an \"Action needed\" notification that opens the right setting."),
        step(HOME, "home.bedtime", "Next focus window", "When Bedtime or School next starts, and when apps unpause."),

        // ---- Map ----
        step(MAP, "map.mode", "Live and Today", "\"Live\" shows where your child is now. \"Today\" replays where they went."),
        step(MAP, "map.fresh", "How fresh", "How old the last location is. The dot dims once it's over 5 minutes old."),
        step(MAP, "map.recenter", "Back to your child", "Flies the map back to your child's current location."),
        step(MAP, "map.add", "Add a place", "Tap + and pick a type, then tap the spot on the map. Name it, set the radius and choose arrival or leaving alerts."),
        step(MAP, "add.categories", "Pick a type", "Choose what you're adding, such as \"Home\" or \"School\". In Live view, a tap on the map then drops that place.", scene = "map.add"),
        step(MAP, "add.area", "Draw an area", "Tap at least 3 points around the edge, then \"Save area\". Use undo or \"Clear all\" to fix mistakes.", scene = "map.add"),
        step(MAP, "map.places", "Your places", "Opens \"Places & areas\", with every place and the latest arrivals and departures."),
        step(MAP, "places.list", "Places & areas", "Tap a place to fly to it, or ⋮ for \"Edit\" or \"Delete\".", scene = "map.places"),
        step(MAP, "places.recent", "Recent activity", "The latest arrivals and departures. Arrival alerts wait 3 minutes inside; leaving alerts wait 2 minutes outside.", scene = "map.places"),
        step(MAP, "today.pickers", "Pick a day", "Tap the date to choose a day, and the From and To times to look at part of it.", scene = "map.today"),
        step(MAP, "today.sheet", "Stops and distance", "A stop is anywhere your child stayed for more than 15 minutes. Tap here for the timeline: when they arrived, left and how long they stayed.", scene = "map.today"),
        step(MAP, "today.play", "Play it back", "Tap Play to watch the day, and drag the bar to jump to a moment.", scene = "map.today"),
        step(MAP, "today.speed", "Speed and stops", "Choose \"1×\", \"2×\" or \"4×\", or skip to the previous or next stop.", scene = "map.today"),
        step(MAP, "today.fit", "Fit and recenter", "Fit the whole day on screen, or fly back to your child.", scene = "map.today"),

        // ---- Activity ----
        step(ACTIVITY, "activity.total", "Screen time today", "Today's total for the child you picked on Home, updated as Shield reports usage."),
        step(ACTIVITY, "activity.search", "Find an app", "Type in \"Search apps\" to find one by name."),
        step(ACTIVITY, "activity.sort", "Sort the list", "Sort by \"Used time\", \"Name\" or \"Installed on\", which also shows each app's install date."),
        step(ACTIVITY, "activity.app", "Apps and rules", "Each card shows today's time. \"Blocked\" or \"Limited\" marks an app with a rule; tap an app to set one."),
        step(ACTIVITY, "activity.waiting", "Waiting for apps", "Open Shield on your child's phone so it sends its list of apps."),
        step(ACTIVITY, "rule.block", "Block completely", "Blocks the app at all times. Its limit and hours are kept for when you turn the block off.", scene = "activity.rule"),
        step(ACTIVITY, "rule.limit", "Daily limit", "The most time per day, from \"15m\" to \"3h\", or \"No limit\".", scene = "activity.rule"),
        step(ACTIVITY, "rule.hours", "Allowed hours", "Tap \"Add hours\" for a window. Outside these windows the app is blocked.", scene = "activity.rule"),
        step(ACTIVITY, "rule.save", "Save", "Sends the rule to your child's phone. Limits and hours follow your child's time zone from Focus modes.", scene = "activity.rule"),

        // ---- Notifications (the bell) ----
        step(ALERTS, "alerts.title", "Notifications", "Requests and place alerts for this child. Safety alerts arrive only as phone notifications."),
        step(ALERTS, "alerts.request", "A request", "Tap \"Approve\" and pick how long, or \"Not now\" to turn it down. Nothing changes until you confirm."),
        step(ALERTS, "alerts.history", "Place history", "\"TODAY\" and \"EARLIER\" list arrivals and departures."),
        step(ALERTS, "alerts.clear", "Clear all", "Deletes this history straight away, for every parent."),

        // ---- Your children sheet ----
        step(HOME, "sheet.child", "Your children", "Tap a child to switch the whole app to them. The tick marks who you're viewing.", scene = "home.sheet"),
        step(HOME, "sheet.add", "Add child", "Pair another phone by scanning its Shield code.", scene = "home.sheet"),
        step(HOME, "sheet.manage", "Manage children", "Parents, restoring a reinstalled phone, and removing a child.", scene = "home.sheet"),
        step(HOME, "sheet.guide", "App guide", "Starts this walk-through again any time.", scene = "home.sheet"),
        step(HOME, "sheet.settings", "Settings", "Appearance, map style and your account.", scene = "home.sheet"),
        step(HOME, "sheet.logout", "Log out", "Signs out at once. This phone stops getting alerts.", scene = "home.sheet"),

        // ---- Manage children ----
        step(MANAGE, "manage.card", "Each child", "Shows whether their phone is \"Online\" (checked in within 5 minutes) or \"Offline\"."),
        step(MANAGE, "manage.more", "More options", "Tap ⋮ for \"Parents\", restoring a reinstalled phone, and \"Remove\"."),
        step(MANAGE, "menu.parents", "Parents", "Every parent account for this child; yours is marked \"(you)\". Removing a parent needs approval on your child's phone.", scene = "manage.menu"),
        step(MANAGE, "menu.restoreScan", "Restore by scan", "Shield reinstalled? Don't add a new child: scan its code here and they keep their places, rules and history.", scene = "manage.menu"),
        step(MANAGE, "menu.restoreGallery", "Restore from gallery", "The same restore, from a screenshot of Shield's code.", scene = "manage.menu"),
        step(MANAGE, "menu.remove", "Remove", "Unlinks only your account. If you were the last parent, Shield's restrictions turn off on that phone.", scene = "manage.menu"),
        step(MANAGE, "manage.restoreWaiting", "Restore waiting", "With another parent, a restore waits 30 minutes. Any parent can tap here to cancel it."),
        step(MANAGE, "manage.add", "Add a child", "Pairs a new child's phone."),

        // ---- Settings ----
        step(SETTINGS, "settings.appearance", "Appearance", "Pick \"System\", \"Light\" or \"Dark\". It applies right away."),
        step(SETTINGS, "settings.mapStyle", "Map style", "Every map in Watch Dog uses your choice. \"Auto\" turns dark at night."),
        step(SETTINGS, "settings.twoFactor", "Two-factor", "A second lock on your account, with an authenticator app and backup codes."),
        step(SETTINGS, "settings.password", "Change password", "Enter your current password and the new one twice, plus a code if two-factor is on."),
        step(SETTINGS, "settings.reset", "Reset password", "Emails you a link for setting a new password."),
        step(SETTINGS, "settings.guide", "App guide", "Starts this walk-through again."),
        step(SETTINGS, "settings.readGuide", "Read the full guide", "Every screen explained in short chapters, with tips."),
        step(SETTINGS, "settings.diagnostics", "Diagnostics", "A log of errors and actions to help fix problems."),
        step(SETTINGS, "settings.signout", "Sign out", "Works at once, without asking. This phone stops getting alerts."),
        step(SETTINGS, "settings.about", "About", "Your version number and what's new."),

        // ---- Diagnostics ----
        step(DIAGNOSTICS, "diag.collect", "Collect diagnostics", "On by default. It records errors, warnings and actions, never locations or content."),
        step(DIAGNOSTICS, "diag.sources", "Choose a log", "\"This phone\", \"My account (all my phones)\" or one of your children's phones."),
        step(DIAGNOSTICS, "diag.refresh", "Refresh", "Reloads the log. Newest events come first: errors red, warnings amber."),
        step(DIAGNOSTICS, "diag.copy", "Copy", "Copies the log so you can paste it to support."),
        step(DIAGNOSTICS, "diag.share", "Share", "Sends the log straight to another app. That's the tour: tap \"Done\", or read the full guide."),
    )
}
