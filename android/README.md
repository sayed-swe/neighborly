# Neighborly for Android

Native Android client for Neighborly, built with Kotlin and Jetpack Compose (Material 3). It talks to the **same Supabase project** as the web app (`../src`) using only the public anon key. Row Level Security enforces every permission, and the app never changes the database schema.

| | |
|---|---|
| Language / UI | Kotlin 2.4, Jetpack Compose (BOM 2026.09), Material 3, light and dark |
| Architecture | MVVM with repositories, `StateFlow<UiState>` per screen, Hilt DI |
| Navigation | Navigation Compose with type-safe `@Serializable` routes (`navigation/Routes.kt`) |
| Backend | supabase-kt 3.9 (auth, postgrest, storage, realtime) + Ktor OkHttp |
| Images | Coil 3 + Android Photo Picker (compressed to ≤ 5 MB JPEG, max 1600 px) |
| Map | osmdroid (OpenStreetMap tiles, no API key) |
| Location | Fused Location Provider with a runtime permission request |
| SDK | minSdk 26, targetSdk 35, compileSdk 37 |

## 1. Configure

You don't need any secrets. The build reads the Supabase URL and anon key in this order:

1. `android/local.properties` (git-ignored)

   ```properties
   SUPABASE_URL=https://mnebcomkejcvgvxsvwza.supabase.co
   SUPABASE_ANON_KEY=<VITE_SUPABASE_ANON_KEY from ../.env.production>
   ```

2. Environment variables `SUPABASE_URL` / `SUPABASE_ANON_KEY` (used by CI repo secrets).
3. The repo's `../.env.production` (`VITE_SUPABASE_URL` / `VITE_SUPABASE_ANON_KEY`), so a fresh clone builds with no setup.

**Owner action, done once:** Supabase → Authentication → URL Configuration → Redirect URLs → add
`neighborly://auth-callback`. Email confirmation and password reset links open the app through this URL (PKCE flow).

## 2. Build and run

Use Android Studio (open the `android/` folder) or the command line, with JDK 17 or newer:

```bash
cd android
./gradlew assembleDebug                              # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                               # install on a connected device / emulator
./gradlew lintDebug testDebugUnitTest assembleDebug  # what CI runs
./gradlew connectedDebugAndroidTest                  # Compose UI tests (needs a device)
```

## 3. Test with the demo accounts

These come from `../supabase/seed_demo.sql`:

| Email | Password | Role |
|---|---|---|
| admin@neighborly.test | Admin@12345 | admin (sees **Menu → Moderation**) |
| rahim@neighborly.test | Neighbor@123 | resident |
| karima@neighborly.test | Neighbor@123 | resident |
| sohel@neighborly.test | Neighbor@123 | service provider |
| nusrat@neighborly.test | Neighbor@123 | resident |

Suggested walk-through:

- Log in as Rahim and check the Home feed, the "Nearby now" strip, reactions (long-press Like), comments, sharing, Buy & sell, Events, Map, Alerts and Chats.
- Log in as the admin, then go to Menu → Moderation and resolve the seeded report.
- Open the same chat on the web and on the phone to watch Realtime messages arrive.

## 4. Project layout

```
app/src/main/java/com/neighborly/app/
├── NeighborlyApp.kt, MainActivity.kt   # Hilt app; auth deep links (neighborly://auth-callback)
├── core/
│   ├── supabase/      # SupabaseModule (client), SessionManager (session → profile → AuthState)
│   ├── designsystem/  # Theme, PostCard, reactions, Avatar, pills, skeletons, empty/error states
│   ├── location/      # FusedLocationProvider + permission helper
│   └── util/          # Format / Geo (ports of src/lib/format.js + geo.js), typed errors, image compressor
├── data/
│   ├── model/         # @Serializable DTOs for every table / view / RPC used
│   └── repository/    # Auth, Feed, Reaction, Comment, Listing, LostFound, Event, Service, Help,
│                      # Alert, Notification, Chat, Report, Profile, Lookup, Storage, Map, Moderation
├── feature/           # auth, feed, post, marketplace, lostfound, events, services, messages,
│                      # alerts (+ notifications), map, profile, moderation, common
└── navigation/        # routes, NavHost, root (auth gate, top bar, bottom bar, live badges)
```

Every query mirrors `../src/lib/api.js`: the same tables, views, embedded selects, filters, page sizes and ordering. Things the database already does (notifications, profile creation, `last_message_at`, protecting role/status, moderator-hidden content, `is_verified`) are never duplicated in the app.

## 5. Signed release build

1. Create a keystore once and keep it **out of git** (`*.jks` is ignored):

   ```bash
   keytool -genkeypair -v -keystore neighborly-release.jks -alias neighborly -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `android/keystore.properties` (also git-ignored):

   ```properties
   storeFile=neighborly-release.jks
   storePassword=…
   keyAlias=neighborly
   keyPassword=…
   ```

3. Build with `./gradlew assembleRelease` (APK) or `./gradlew bundleRelease` (AAB for Play). Release builds are minified with R8 (`app/proguard-rules.pro`). Without `keystore.properties` the release build is unsigned.

## 6. CI

`.github/workflows/android.yml` runs `lintDebug testDebugUnitTest assembleDebug` on every push or PR that touches `android/**`, and uploads the debug APK as an artifact. The web deploy workflow ignores `android/**`.
