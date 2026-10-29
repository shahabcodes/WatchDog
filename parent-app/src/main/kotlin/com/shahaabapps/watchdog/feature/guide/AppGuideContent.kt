package com.shahaabapps.watchdog.feature.guide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.graphics.vector.ImageVector

/** One numbered step inside a guide chapter. Text in "double quotes" is an on-screen label. */
data class GuideStep(val title: String, val body: String)

/**
 * One chapter of the in-app guide. [whereToFind] is a "→"-separated path shown as
 * pills; `{child}` anywhere in the text is replaced with the selected child's name.
 */
data class GuideChapter(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tagline: String,
    val whereToFind: String,
    val steps: List<GuideStep>,
    val tip: String? = null,
)

/** A titled group of chapters on the guide's index. */
data class GuideSection(val label: String, val chapterIds: List<String>)

private fun step(title: String, body: String) = GuideStep(title, body)

/** Everything the App guide says, in reading order. Copy matches the shipping UI labels. */
object AppGuideContent {

    val chapters: List<GuideChapter> = listOf(
        GuideChapter(
            id = "welcome",
            title = "Welcome to Watch Dog",
            icon = Icons.Filled.FamilyRestroom,
            tagline = "A guiding light for your family.",
            whereToFind = "Bottom bar → Home · Map · Activity",
            steps = listOf(
                step("Two apps, one family", "Watch Dog is your app. Your child's phone runs a separate app called Shield. Shield reports location and screen time, and applies the rules you set here."),
                step("Private by design", "Place names and locations, routes, app lists, rules and focus schedules are end-to-end encrypted. Only Watch Dog and Shield on your family's phones can read them, not the server."),
                step("Their phone has the final say", "Nothing links until someone holding your child's phone taps \"Approve\" in Shield. The same applies when a parent is added or removed, or when you sign in on a new phone or reinstall Watch Dog. Signing out and back in on the same phone doesn't need a new approval."),
                step("Getting around", "Use the floating bar at the bottom. \"Home\" is your child's dashboard, \"Map\" shows places and live location, and \"Activity\" shows screen time. All three show the child you picked on Home."),
                step("Works offline too", "Changes you make without internet are saved on your phone. A pill at the bottom says \"Saved – will reach {child}'s phone when you're back online\", then \"Waiting to sync\". Back online, it shows \"Syncing…\" and then \"Synced\"."),
            ),
            tip = "Your child doesn't need an account. Only parents sign in to Watch Dog.",
        ),
        GuideChapter(
            id = "account",
            title = "Your parent account",
            icon = Icons.Filled.AccountCircle,
            tagline = "Create it once, keep it safe.",
            whereToFind = "Signed out → Welcome → Create an account / I already have an account",
            steps = listOf(
                step("Create an account", "Tap \"Create an account\" and enter your email. Pick a password of at least 10 characters with a lowercase letter and a number (the meter tells you what's missing), then type it again in \"Confirm password\". Tick \"I'm the parent or guardian…\" to turn on \"Create account\"."),
                step("Set up your profile", "\"Your Profile\" opens next. Only your name is required. \"Tap to add a photo\" and \"Select date of birth\" are optional, then tap \"Continue\"."),
                step("Sign in", "Tap \"I already have an account\", enter your email and password, then tap \"Sign in\". The eye icon shows or hides your password."),
                step("Two-factor at sign-in", "If two-factor is on, enter the 6-digit code from your authenticator app and tap \"Verify\". Lost it? Tap \"Lost your authenticator? Use a backup code\", then \"Sign in with backup code\". This turns two-factor off so you can set it up again."),
                step("Forgot your password?", "On Sign in, tap \"Forgot password?\", then \"Send reset link\". A \"Check your inbox\" card confirms the email was sent."),
            ),
            tip = "No verification email is sent at sign-up. The first time you pair a child, Watch Dog asks you to verify your email and sends the link then. Tap it, then scan the code again.",
        ),
        GuideChapter(
            id = "add_child",
            title = "Add your child",
            icon = Icons.Filled.QrCodeScanner,
            tagline = "Scan Shield's code, then approve on their phone.",
            whereToFind = "Home → name pill → Add child",
            steps = listOf(
                step("Start on your child's phone", "Install Shield on your child's phone and open it. On \"Set Up Profile\", enter their name and tap \"Continue\". Shield then shows a pairing QR code."),
                step("Scan the code", "The first time, \"Pair your child's phone\" opens by itself after your profile. Hold Shield's code inside the frame. If the camera is blocked, tap \"Enable camera\"."),
                step("Or use a screenshot", "Tap \"Scan QR from gallery\" and pick a picture of Shield's code, for example a screenshot your child sent you."),
                step("Approve on their phone", "\"Almost done\" means Shield is asking to connect your account. On your child's phone, tap \"Approve\" on \"Connect a parent?\" within 15 minutes."),
                step("All set", "Once their phone approves, you see \"ALL SET\" and that your child is connected. Tap \"Open the family map\" to go to Home."),
                step("If it fails", "Red text explains what went wrong, for example an expired code or \"Verify your email first\". Each code lasts 10 minutes and works once, so scan the newest one."),
            ),
            tip = "You can't type the code in Watch Dog. Scan the QR code, or pick a screenshot of it from your gallery. Until a child is linked, this pairing screen is all Watch Dog shows.",
        ),
        GuideChapter(
            id = "child_phone",
            title = "Set up your child's phone",
            icon = Icons.Filled.PhoneAndroid,
            tagline = "Give Shield what it needs to work.",
            whereToFind = "Child's phone → Shield → Me → SET UP",
            steps = listOf(
                step("Finish setup", "Under \"Finish setup\", tap \"Enable\" for \"Usage access\" and for \"Accessibility (Shield)\". These turn on screen time, app blocking and screen capture."),
                step("Enable places", "Allow location, then choose \"Allow all the time\", so places and live location work."),
                step("Keep Shield running", "Tap \"Allow\" for \"Unrestricted battery\". On some phones, also tap \"Open\" for \"Auto-start on boot\"."),
                step("Enable alerts", "Allow notifications. Turn on the optional \"Full-screen ring alarm\" too, so a ring fills the screen."),
                step("Protect this app", "After you turn on Prevent uninstall in Watch Dog, tap \"Enable\" under \"Protect this app\", then \"Activate this device admin app\"."),
                step("Answer approval requests", "\"Connect a parent?\", \"Approve a parent's phone?\" and \"Remove a parent?\" only appear while Shield is open. Nothing changes until someone taps there."),
            ),
            tip = "When Watch Dog says \"Protection setup\", it means this SET UP list. File access for app installs has no card here: grant it from the notification Watch Dog's \"Request\" sends. After a restore, go through this list again.",
        ),
        GuideChapter(
            id = "home",
            title = "Your Home screen",
            icon = Icons.Filled.GridView,
            tagline = "One dashboard for each child.",
            whereToFind = "Bottom bar → Home",
            steps = listOf(
                step("The live map", "The map at the top shows \"Online\" or \"Offline\", where your child is (for example \"At Home\" or \"Outside known areas\"), their battery level and when it last updated. Tap the map to open the Map tab."),
                step("Switch children", "Tap the name pill at the top left to open \"Your children\". Tap a child to switch the whole app to them. This sheet also has \"Add child\", \"Manage children\", \"App guide\", \"Settings\" and \"Log out\"."),
                step("Notifications", "Tap the bell at the top right to open this child's requests and place alerts."),
                step("Pull to refresh", "Swipe down from the top of Home to ask your child's phone for a fresh location."),
                step("Finish securing this connection", "If you see this banner, tap \"Verify\". On your child's phone, open Shield → Me → Settings → \"Show secure code\", then scan that code. The banner goes away once the connection is verified."),
                step("Protection incomplete", "This red card appears when something is wrong on your child's phone, such as app blocking switched off or a fake-location app in use. Missing permissions (app blocking, screen-time tracking, uninstall protection) are fixed in Shield → Me → SET UP on their phone. For the others, use the phone's own Settings: set the date and time to automatic, uninstall the fake-location app, or turn off Developer options → USB debugging."),
            ),
            tip = "Scroll down for more cards; the next chapters cover them. The orange dot on the bell is always there, so it doesn't mean you have unread alerts.",
        ),
        GuideChapter(
            id = "quick_actions",
            title = "Quick actions",
            icon = Icons.Filled.TouchApp,
            tagline = "Ring, Locate, Refresh and Focus.",
            whereToFind = "Home → tiles under the map",
            steps = listOf(
                step("Ring", "Tap \"Ring\", type a message if you like, then tap \"Ring now\". Their phone plays a loud alarm, even on silent, and shows your message."),
                step("What they see", "Your child sees \"Your family is looking for you\" with \"I'm okay, stop ringing\" and \"Snooze 5 minutes\". The volume or power button also stops it."),
                step("Locate", "Tap \"Locate\" to jump to the Map tab. It doesn't ask the phone for a new location."),
                step("Refresh", "Tap \"Refresh\" to ask your child's phone for a fresh, precise location. The spinner stops after about a second, and the new location may arrive a little later."),
                step("Focus", "Tap \"Focus\" to edit the Bedtime and School schedules."),
            ),
            tip = "Ring and Refresh only work while your child's phone is online and Shield is running. Watch Dog doesn't show whether a ring was delivered. For a full-screen ring, \"Full-screen alarm\" must be on under Permissions.",
        ),
        GuideChapter(
            id = "phone_health",
            title = "Phone health and permissions",
            icon = Icons.Filled.BatteryStd,
            tagline = "See why location is fresh or stale.",
            whereToFind = "Home → scroll down",
            steps = listOf(
                step("LOCATION HEALTH", "A green tick means location is updating normally. An orange icon explains the problem, such as missing location permission or battery saver, and how to fix it on your child's phone."),
                step("BATTERY", "Shows the current level, a status such as \"Charging\" or \"Draining about N%/hr\", when the phone was last charged, and a chart of recent hours. Tap the header to see an hour-by-hour list."),
                step("TEMPERATURE", "Appears once the phone reports its temperature. It shows \"Normal\", \"Warm\" or \"Running hot\", the peak reading and a chart. Tap the header to see each reading."),
                step("LOCATION TRACKING", "Pick \"Saver\", \"Balanced\" or \"High\". \"Balanced\" is recommended: precise while moving and light on battery when still. Your choice saves straight away."),
                step("Permissions on their phone", "Each row shows what Shield is allowed to do, with a green tick or an orange warning. Tap \"Request\" on a missing permission. Your child gets an \"Action needed\" notification that opens the right setting."),
            ),
            tip = "\"Online\" means the phone checked in within the last 5 minutes. Shield checks in about every 2 minutes.",
        ),
        GuideChapter(
            id = "screen_capture",
            title = "See their screen",
            icon = Icons.Filled.Screenshot,
            tagline = "A snapshot of what's on screen right now.",
            whereToFind = "Home → SCREEN",
            steps = listOf(
                step("Capture", "Tap \"Capture\". You'll see \"Waking the device…\", then \"Capturing the screen…\" and \"Receiving…\", and then the new picture appears."),
                step("If nothing arrives", "A red message explains why, for example \"Couldn't reach the device. Make sure it's online and Shield is running.\" Tap \"Tap to try again\"."),
                step("Browse", "Swipe the strip of thumbnails, which starts with the newest. Tap one to open it full screen. Pinch or double-tap to zoom, and swipe to move between pictures."),
                step("Save, share or delete", "In the full-screen view, use the download, share or bin icon. Under the thumbnails, \"Save all\" copies every picture to Pictures/WatchDog, and \"Delete all\" removes them all after you confirm."),
                step("Where they're kept", "Screenshots are kept only on this phone and are deleted after 14 days. Uninstalling Watch Dog or clearing its data loses them. Copies you saved to the Gallery stay there."),
            ),
            tip = "Capture only works when Accessibility is on for Shield and your child's phone runs Android 11 or newer. Your child isn't notified when you capture. The bin icon in the full-screen view deletes without asking first.",
        ),
        GuideChapter(
            id = "map_places",
            title = "Map and places",
            icon = Icons.Filled.Map,
            tagline = "Live location, saved places and arrival alerts.",
            whereToFind = "Bottom bar → Map",
            steps = listOf(
                step("Live view", "The map centres on your child every time you open it. A pill shows how fresh the location is, for example \"Updated 3 min ago\", and the dot dims once it's over 5 minutes old. Tap the round location button to fly back to your child."),
                step("Add a place", "Tap + and pick a type, such as \"Home\" or \"School\". Then tap the spot on the map, as the hint at the top says. In Live view, any tap on the map starts a new place of the selected type (Home by default). Swipe the sheet away if you didn't mean to."),
                step("Name it and set alerts", "In \"New place\", type a name, pick an icon and drag the radius slider (50 to 1000 m). Choose \"Notify on arrival\" and \"Notify on leaving\", then tap \"Save place\"."),
                step("Draw an area", "Tap + → \"Draw area\", then tap at least 3 points around the edge. Use undo or \"Clear all\" to fix mistakes. Tap \"Save area\", name the area, and tap \"Save area\" again."),
                step("Manage places", "Tap the \"Places\" button to open \"Places & areas\". Tap a place to fly to it, or tap ⋮ for \"Edit\" or \"Delete\". \"Recent activity\" lists the latest arrivals and departures."),
                step("When alerts fire", "An arrival alert waits until your child has been inside for 3 minutes. A leaving alert waits until they've been more than 100 m outside the edge for 2 minutes."),
            ),
            tip = "You can't change the shape of a drawn area; delete it and draw it again. A place with both alerts off stays on the map but sends no alerts, and Home won't show \"At <place>\" for it. Use two fingers to rotate or tilt the map.",
        ),
        GuideChapter(
            id = "today_history",
            title = "Replay the day",
            icon = Icons.Filled.Timeline,
            tagline = "Where they went, and how long they stayed.",
            whereToFind = "Map → Today",
            steps = listOf(
                step("Pick a day and time", "Tap the date pill to choose a day. Tap the From and To times to look at part of the day."),
                step("Read the route", "The path is drawn as a line with numbered stops. Dashed grey lines mark gaps of more than 5 minutes without a location, for example in a tunnel or while the phone was off."),
                step("Play it back", "Tap Play to watch the day, and choose \"1×\", \"2×\" or \"4×\" for speed. Drag the bar to jump to a moment, or skip to the previous or next stop."),
                step("See every stop", "A stop is anywhere your child stayed for more than 15 minutes. Tap the panel header, which shows the number of places and distance travelled, to open the timeline. Each stop shows when your child arrived and left, and how long they stayed. Tap a stop to fly to it on the map."),
                step("Save a stop as a place", "If a stop isn't one of your places yet, tap \"+ Save as place\". \"New place\" opens named \"Home\", so rename it if needed and tap \"Save place\"."),
            ),
            tip = "Use the side buttons to fit the whole day on screen or to recentre. Tapping the map doesn't add a place in Today view; tap + to switch back to Live first. The switch still reads \"Today\" when you pick another day.",
        ),
        GuideChapter(
            id = "activity",
            title = "Activity and screen time",
            icon = Icons.Filled.BarChart,
            tagline = "How long they used each app today.",
            whereToFind = "Bottom bar → Activity",
            steps = listOf(
                step("Today's total", "The top of the screen shows \"Screen time today\" for the child you picked on Home. It updates as Shield reports usage."),
                step("Find an app", "Type in \"Search apps\", or sort the list with \"Used time\", \"Name\" or \"Installed on\". \"Installed on\" also shows each app's install date."),
                step("Read the cards", "Each card shows today's time with a bar. \"Blocked\" means the app is blocked, and \"Limited\" means it has a daily limit."),
                step("On Home too", "Home compares \"Screen time today\" with yesterday and lists the top 5 apps. \"THIS WEEK\" shows a 7-day chart with the daily average."),
                step("Empty list?", "If you see \"Waiting for app list\", open Shield on your child's phone so it sends its list of apps."),
            ),
            tip = "Tap any app to give it a rule. The next chapter shows how.",
        ),
        GuideChapter(
            id = "app_rules",
            title = "App rules and limits",
            icon = Icons.Filled.Timer,
            tagline = "Block an app, cap it, or set its hours.",
            whereToFind = "Activity → tap an app",
            steps = listOf(
                step("Block completely", "Turn on \"Block completely\" to block the app at all times. The limit and hours are hidden but kept, so turning the block off brings them back."),
                step("Daily limit", "Pick \"15m\", \"30m\", \"1h\", \"2h\" or \"3h\" as the most time per day, or \"No limit\"."),
                step("Allowed hours", "Tap \"Add hours\" to add a window, which starts as 8:00 AM – 11:00 AM. Tap a time to change it, or X to remove the window. Outside these windows the app is blocked."),
                step("Save or remove", "Tap \"Save\" to send the rule to your child's phone. To clear a rule, tap \"Remove rule\", then \"Remove\"."),
            ),
            tip = "If you swipe the sheet away without saving, your changes are lost. Limits and hours follow your child's time zone, set in Focus modes. Changing a rule cancels any extra time you approved earlier for that app.",
        ),
        GuideChapter(
            id = "requests",
            title = "Requests and notifications",
            icon = Icons.Filled.QuestionAnswer,
            tagline = "Answer your child's asks.",
            whereToFind = "Home → bell",
            steps = listOf(
                step("Your child asks", "Your child can ask for more time, to unlock an app, to stay up later, or to use an app during school. Their requests show under \"REQUESTS\"."),
                step("Approve", "Tap \"Approve\", then pick \"For a while\" (15 min to 4 hours) or \"Until a time\". Tap \"Approve\" again to confirm."),
                step("Decline", "\"Not now\" turns the request down; it doesn't snooze it. Tap \"Decline\" to confirm."),
                step("From a notification", "Tapping \"Approve\" or \"Reject\" on the notification opens the right dialog in Watch Dog. Nothing changes until you confirm there."),
                step("Place history", "\"TODAY\" and \"EARLIER\" list arrivals and departures. \"Clear all\" deletes this history straight away, for every parent."),
            ),
            tip = "Safety alerts, such as protection being turned off or fake GPS, arrive only as phone notifications. They don't appear on this screen.",
        ),
        GuideChapter(
            id = "focus_modes",
            title = "Bedtime and School",
            icon = Icons.Filled.Bedtime,
            tagline = "Set the hours when apps pause.",
            whereToFind = "Home → Focus",
            steps = listOf(
                step("Time zone", "Schedules follow your child's home time zone. Tap the time zone row, then choose \"Use the phone's zone\" or search for a city. Your choice saves as soon as you pick it."),
                step("Turn a mode on", "Switch on \"Bedtime\" or \"School\". Apps are blocked during these hours, but phone calls still work."),
                step("Set times and days", "Tap \"Start\" or \"End\" to pick a time. A window can run past midnight. Tap the letters M T W T F S S to choose the days."),
                step("Allowed apps", "Tap \"Allowed apps\", tick the apps that stay usable, then tap \"Done\". \"None — everything is blocked\" means no apps are allowed."),
                step("Save", "Tap \"Save\" to send both modes to your child's phone. Home then shows a banner such as \"Bedtime at 9:00 PM\"."),
            ),
            tip = "If you swipe the sheet away without tapping Save, changes to times, days and apps are lost. If your child's phone stays on another time zone for 2 hours, you get a \"Travelling\" alert.",
        ),
        GuideChapter(
            id = "protection",
            title = "Protection",
            icon = Icons.Filled.Lock,
            tagline = "Keep Shield on, and new apps in check.",
            whereToFind = "Home → PROTECTION → Prevent uninstall",
            steps = listOf(
                step("Prevent uninstall", "Turn on \"Prevent uninstall\", type a \"New removal PIN (15–20 digits)\", and tap \"Save\". Write the PIN down: Watch Dog can never show it again, only reset it."),
                step("Finish on their phone", "On your child's phone, open Shield → Me → \"Protect this app\", tap \"Enable\", then \"Activate this device admin app\". Until then, the \"Protection incomplete\" card on Home offers \"Ask {child}'s phone to turn it on\"."),
                step("Lock Shield's settings", "This keeps your child out of the Settings pages that could switch Shield off. Your removal PIN unlocks them on the phone."),
                step("Block new apps", "New apps your child installs stay blocked until you decide. They appear on Home under \"NEW APPS TO REVIEW\", where you tap \"Allow\" or \"Keep blocked\"."),
                step("Block pop-up and split-screen view", "Choose \"Off\", \"Bedtime & school\" or \"Always\": while it's on, an app your child opens in a pop-up window, split screen or picture-in-picture is covered and closed, so blocked apps can't hide there."),
                step("Choose your alerts", "Under \"Alerts\", turn \"Protection turned off\", \"Date or time changed\", \"Fake GPS\", \"Travelling\" and \"Not checking in\" on or off. \"Protection turned off\" also covers USB debugging being turned on and a parent's phone waiting for approval on your child's phone."),
            ),
            tip = "Changes here apply only when you tap \"Save\". \"Reset PIN\" sets a new PIN, and turning protection off keeps the old one. Choosing \"Allow\" for a new app also removes any limit or hours you set for it.",
        ),
        GuideChapter(
            id = "app_installs",
            title = "App installs",
            icon = Icons.Filled.GetApp,
            tagline = "Control apps from outside the Play Store.",
            whereToFind = "Home → APP INSTALLS → Control app installs",
            steps = listOf(
                step("Switch it on", "Turn on \"Control sideloaded installs\" to block apps installed or updated from outside the Play Store."),
                step("Pick a mode", "Turn on \"Allow only the apps I pick\" to block everything you haven't picked. Leave it off to block only the apps you pick."),
                step("Choose apps", "Flip the switch next to each app. You can search by name or package, and sort with \"Selected first\", \"Name A–Z\", \"Name Z–A\" or \"Package\"."),
                step("Add by package name", "For an app that isn't on the phone yet, type its package name (for example com.android.chrome). Then tap \"Allow this package\" or \"Block this package\"."),
                step("File access needed", "Shield needs All-files access on your child's phone for this to work. If it's missing, tap \"Request\" on the \"Install control (file access)\" row under Permissions on Home."),
            ),
            tip = "Every change saves straight away, so there's no Save button. Installs from a computer over USB can't be stopped, but you are notified about them.",
        ),
        GuideChapter(
            id = "alerts",
            title = "Alerts you'll get",
            icon = Icons.Filled.NotificationsActive,
            tagline = "What pings your phone, and why.",
            whereToFind = "Android notification shade → Watch Dog",
            steps = listOf(
                step("Allow notifications", "When Watch Dog first opens, it asks to send notifications. Tap \"Allow\", or alerts can't reach you."),
                step("Places", "You get \"{child} arrived at …\" and \"{child} left …\" for places that have alerts on. The place name is decrypted on your phone, so the server never sees it."),
                step("Requests and new apps", "Requests from your child come with \"Approve\" and \"Reject\" buttons. \"New app on {child}'s phone\" means Shield blocked an app that was just installed."),
                step("Blocked apps", "\"{child} tried to open …\" says which blocked app and why, such as \"Bedtime\" or \"Daily limit reached\". You get at most one per app every 10 minutes, with \"tried 4 times\" when your child kept trying. Tap it to see that app's rules. The app's name is decrypted on your phone, so the server never sees it."),
                step("Protection changes", "\"Shield protection changed on {child}'s phone\" means something was weakened, such as app blocking, the clock, fake GPS or USB debugging. It also comes when a parent's phone (yours or someone else's) is waiting for \"Approve a parent's phone?\" on your child's phone; tapping \"Approve\" in Shield clears it. If that wasn't you, someone may have your Watch Dog login. While something stays off, \"{child}'s phone isn't fully protected\" reminds you once a day."),
                step("Check-ins and travel", "\"No contact with {child}'s phone\" means Shield hasn't checked in for 3 hours, and \"{child}'s phone is back\" follows when it does. A time-zone alert suggests switching Focus when your child travels."),
                step("Family changes", "You're told when a parent is added or removed, and when a reinstalled phone is restored. These alerts can't be turned off."),
                step("Tune them", "Turn safety alerts on or off for each child in Home → Protection → \"Alerts\". In Android settings, Watch Dog's notifications are grouped as \"Place alerts\", \"Child requests\" and \"Blocked apps\"."),
            ),
            tip = "Muting \"Place alerts\" in Android also mutes safety and family alerts. Signing out stops all alerts on this phone.",
        ),
        GuideChapter(
            id = "manage_children",
            title = "Children and co-parents",
            icon = Icons.Filled.People,
            tagline = "Parents, restores and removals.",
            whereToFind = "Home → name pill → Manage children",
            steps = listOf(
                step("See every child", "Each card shows \"Online\" or \"Offline\". Tap ⋮ for \"Parents\", the restore options and \"Remove\"."),
                step("Parents of a child", "Tap ⋮ → \"Parents\" to see every parent account. Yours is marked \"(you)\"."),
                step("Remove another parent", "Tap \"Remove\" next to a parent, then \"Ask to remove\". Nothing changes until it's approved on your child's phone, and every parent is told."),
                step("Reinstalled Shield?", "Don't add the phone as a new child. On that phone, open Shield and tap \"Reconnecting this child's device? Skip setup\" to show its QR code."),
                step("Restore it", "In Watch Dog, tap ⋮ → \"Restore reinstalled phone · scan code\" (or \"· from gallery\") and scan that code. Your child keeps their places, rules and history, and Shield restarts once."),
                step("Restore waiting", "If another parent also looks after this child, the restore waits 30 minutes. During that time, any parent can tap \"Restore waiting · Cancel\" to stop it."),
                step("Remove a child", "Tap ⋮ → \"Remove\", then \"Remove\" again to confirm. This unlinks only your account. If you were the last parent, Shield's restrictions turn off on that phone."),
            ),
            tip = "A restore only works once Shield has stopped checking in from the original install, so wait 10 minutes after reinstalling. A request to remove a parent expires after 24 hours.",
        ),
        GuideChapter(
            id = "settings",
            title = "Settings",
            icon = Icons.Filled.Settings,
            tagline = "Appearance, maps and your account.",
            whereToFind = "Home → name pill → Settings",
            steps = listOf(
                step("Appearance", "Pick \"System\", \"Light\" or \"Dark\". The change applies right away."),
                step("Map style", "Pick \"3D\", \"Standard\", \"Bright\", \"Positron\", \"Dark\" or \"Auto\" (dark at night). Every map in Watch Dog uses your choice."),
                step("Reset password", "Tap \"Reset password\", then \"Send link\", to email yourself a link for setting a new password."),
                step("Sign out", "\"Sign out\" works at once, without asking first. This phone stops getting alerts, and your children's phones stop sharing data with it. Signing back in on the same phone doesn't need a new approval on your child's phone."),
                step("About", "Scroll down to see your version number and \"What's new\"."),
            ),
            tip = "Appearance and map style are saved on this phone only. \"Log out\" in the Your children sheet does the same as \"Sign out\".",
        ),
        GuideChapter(
            id = "security",
            title = "Two-factor and password",
            icon = Icons.Filled.VerifiedUser,
            tagline = "A second lock on your account.",
            whereToFind = "Home → name pill → Settings → Two-factor authentication",
            steps = listOf(
                step("Turn on", "Tap \"Turn on\". If asked, tap \"Send verification email\", tap the link in the email, then \"I've verified — continue\". Re-enter your password and tap \"Continue\"."),
                step("Link your authenticator", "Scan the QR code with Google Authenticator or a similar app, or tap the setup key to copy it. Enter the 6-digit code and tap \"Verify & turn on\"."),
                step("Save your backup codes", "You get 10 backup codes, shown only this once. Use \"Copy all\" or \"Share / save\" to keep them somewhere safe, then tap \"I've saved them\"."),
                step("Manage backup codes", "The Backup codes card shows how many you have left. \"Get new backup codes\" gives you a new set, and the old codes stop working."),
                step("Turn it off", "Tap \"Turn off two-factor authentication\" and confirm with your password and a code."),
                step("Change password", "In Settings, tap \"Change password\". Enter your current password and the new one twice, then tap \"Change password\". If two-factor is on, you also enter a code."),
            ),
            tip = "If you sign in with a backup code, two-factor turns off and a \"Two-factor is off\" notice offers \"Set up now\". The two-factor screens can't be screenshotted, which keeps your codes safe.",
        ),
        GuideChapter(
            id = "diagnostics",
            title = "Diagnostics",
            icon = Icons.Filled.BugReport,
            tagline = "A log to help fix problems.",
            whereToFind = "Home → name pill → Settings → Diagnostics",
            steps = listOf(
                step("Collect diagnostics", "This is on by default. It records errors, warnings and actions with their date and time. It never records locations or content."),
                step("Choose a log", "Pick \"This phone\", \"My account (all my phones)\" or one of your children's phones. The log loads straight away; tap \"Refresh\" to reload it."),
                step("Read the events", "The newest events come first. Errors are red, warnings amber, and everything else grey."),
                step("Send it", "Tap \"Copy\" or \"Share\" to send the log to support."),
            ),
            tip = "If \"My account\" shows \"alert NOT sent (no phone registered)\", no phone was signed in to get that alert.",
        ),
        GuideChapter(
            id = "child_view",
            title = "What your child sees",
            icon = Icons.Filled.ChildCare,
            tagline = "Shield, from their side.",
            whereToFind = "Child's phone → Shield → Home · My time · Me",
            steps = listOf(
                step("You're protected", "Shield's Home tab shows \"You're protected\", today's screen time and their downtime schedule."),
                step("Block screens", "When a limit, bedtime or school time starts, your child sees a block screen with a button to ask you. If you approve, the app opens again by itself."),
                step("Asking you", "\"Ask for more time\" sends you a request for 30 more minutes (shown when a limit, Bedtime or School applies; while waiting, your child can cancel the ask). Block screens offer asks such as \"Ask for 15 more minutes\" or \"Ask to allow it\". Your child then sees \"Sent to your parent. Waiting…\"."),
                step("What you can see", "Their Me tab shows \"WHAT YOUR PARENT CAN SEE\": location, apps and screen time, their screen when you ask, and battery. It also tells them you can't read their messages or see their photos."),
                step("Locked settings", "With \"Lock Shield's settings\" on, opening those pages sends them back to the home screen with \"Shield settings are locked. Ask a parent to unlock them.\""),
            ),
            tip = "To remove Shield, open Me → SET UP, tap \"Enter PIN\" next to \"Allow removal\" on the \"Uninstall protection\" card, and enter your PIN. This gives a 2-minute window and switches off uninstall protection; if Shield isn't removed, tap \"Enable\" on \"Protect this app\" again.",
        ),
        GuideChapter(
            id = "troubleshooting",
            title = "Troubleshooting",
            icon = Icons.Filled.Build,
            tagline = "Quick fixes for common problems.",
            whereToFind = "Home → LOCATION HEALTH",
            steps = listOf(
                step("Location unknown", "Check \"LOCATION HEALTH\" on Home and fix what it says on your child's phone. Then tap \"Refresh\"."),
                step("Location or history stuck", "This often happens after reinstalling Watch Dog. On your child's phone, open Shield → Me → Settings → \"Re-sync security\". If the Verify banner shows on Home, \"Reconcile secure link\" on its screen does the same from your phone."),
                step("A new phone for you", "If you signed in on a new phone, or reinstalled Watch Dog (or cleared its data), open Shield on your child's phone and tap \"Approve\" on \"Approve a parent's phone?\". Until then, your new phone can't see their data. Signing out and back in on the same phone doesn't need a new approval. If Shield asks anyway, update both phones to the latest version."),
                step("You're offline", "You can keep making changes. The pill says \"Waiting to sync\" while you're offline, then \"Syncing…\" and \"Synced\" once you're back online."),
                step("Pairing won't work", "Scan the newest code from Shield. If the camera doesn't react, use \"Scan QR from gallery\", or leave the screen and open it again."),
                step("No alerts arriving", "Allow notifications for Watch Dog in Android settings. Then check Diagnostics → \"My account\" to see whether each alert was sent."),
            ),
            tip = "If Shield was reinstalled on your child's phone, restore it from Manage children rather than adding a new child.",
        ),
    )

    /** The index's groups, in display order. Every chapter appears exactly once. */
    val sections: List<GuideSection> = listOf(
        GuideSection("Getting started", listOf("welcome", "account", "add_child", "child_phone")),
        GuideSection(
            "Every day",
            listOf("home", "quick_actions", "phone_health", "screen_capture", "map_places", "today_history", "activity"),
        ),
        GuideSection("Rules", listOf("app_rules", "requests", "focus_modes", "protection", "app_installs")),
        GuideSection("Family & account", listOf("alerts", "manage_children", "settings", "security", "diagnostics")),
        GuideSection("Your child's side", listOf("child_view")),
        GuideSection("Help", listOf("troubleshooting")),
    )

    private val byId: Map<String, GuideChapter> = chapters.associateBy { it.id }

    fun chapter(id: String): GuideChapter? = byId[id]

    fun indexOf(id: String): Int = chapters.indexOfFirst { it.id == id }

    /** The section a chapter belongs to, e.g. "Every day" for "home". */
    fun sectionOf(id: String): GuideSection? = sections.firstOrNull { id in it.chapterIds }

    /** Replaces `{child}` with the child's name, or "your child" when none is selected. */
    fun personalise(text: String, childName: String?): String =
        text.replace("{child}", childName?.trim()?.ifBlank { null } ?: "your child")
}
