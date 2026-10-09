# Permissions, components and dependencies (reviewed from source)

| Permission | Why | Notes |
|---|---|---|
| READ_CALENDAR | Agenda | Runtime request, only when agenda is enabled |
| READ_CONTACTS | Contact search | Runtime request, only if contact search is enabled |
| QUERY_ALL_PACKAGES | List launchable apps | Required for a launcher |
| REQUEST_DELETE_PACKAGES | Uninstall action | User confirms in system dialog |
| ACCESS_HIDDEN_PROFILES | Private space | Effective only while default home |
| USE_BIOMETRIC | Unlock Private space entry | |
| BIND_APPWIDGET | Widget binding | System still shows its own consent UI |
| SET_ALARM | Open OEM clock apps | |
| org.breezyweather.READ_PROVIDER | Optional weather from Breezy Weather | |

Not requested: INTERNET, location, storage, microphone, camera. Notification access (media/notification previews) and accessibility
access (lock screen / open panels) are optional special accesses the user grants in system settings; text is kept in memory only
according to the app's own explanation strings.

Exported components: `MainActivity` (HOME intent), `SettingsActivity` (launcher), `PinItemActivity` (system pin-shortcut/widget actions),
`BreezyWeatherUpdateReceiver` (exported by necessity; payload ignored), accessibility service (protected by BIND_ACCESSIBILITY_SERVICE).
`MediaNotificationListener` is not exported.

Dependencies (gradle/libs.versions.toml): AndroidX core/lifecycle/activity/navigation, Compose BOM, Material3, Room, material-kolor, AboutLibraries (offline). No ads or tracking SDKs.
Several are pre-release/very new versions (e.g. Material3 1.5.0-alpha); they were not re-resolved here.

Changed by Niva: `allowBackup=false` (the inherited empty backup rules would otherwise let Android back up the launcher database).
