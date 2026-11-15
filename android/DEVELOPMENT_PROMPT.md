# Neighborly Android — development prompt

> Paste everything below this line into your AI coding agent (Claude Code, Cursor, Android Studio Gemini, …) from the root of the cloned repository.

---

You are a senior Android engineer. Build the **native Android app for Neighborly** in **Kotlin + Jetpack Compose**, connected to the **existing Supabase backend** the web app already uses. Put all Android code in the **`android/`** folder of the repository below, and push your work to GitHub as described in the last section.

## 0. Links and sources of truth

| What | Where |
|---|---|
| GitHub repository | **https://github.com/sayed-swe/neighborly** |
| Live web app (reference for every screen and behaviour) | https://sayed-swe.github.io/neighborly/ |
| Database schema, RLS policies, triggers, RPCs | `supabase/migrations/20261114000001_schema.sql` … `0004_*.sql` |
| Every query the web app makes (copy the logic) | `src/lib/api.js` |
| Supabase URL + public anon key | `.env.production` (repo root) |
| Demo accounts for testing | top of `supabase/seed_demo.sql` |
| Original requirements (IDs used below: ACC, FEED, MAP, MKT, SRV, LF, EVT, MSG, NTF, MOD, ADM) | Neighborly SRS & Architecture document (ask the team for the link) |

Read `src/lib/api.js` and the schema migration **before writing any data code**. The Android app must behave exactly like the web app against the same data.

## 1. Hard rules

1. **Do not change the database** from the app or by hand. Tables, views, RLS policies, triggers and RPCs already exist and are shared with the web app. If something is truly missing, add a **new** file `supabase/migrations/<timestamp>_android_<what>.sql`, explain why in the PR, and do not apply it yourself.
2. **Never use the `service_role` key.** The app uses only the public anon key. All permissions come from Row Level Security, so never "work around" a refused query in the client.
3. **Never commit secrets.** `local.properties`, keystores (`*.jks`, `*.keystore`) and `google-services.json` stay out of git.
4. **Only touch `android/`** and `.github/workflows/android.yml`. The single exception: add `paths-ignore: ['android/**']` to `.github/workflows/deploy.yml` so Android commits don't redeploy the website. Do not edit `src/`, `public/`, `supabase/` or web config.
5. Small, working commits on a feature branch; the project must build after every commit.

## 2. Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin 2.x, Jetpack Compose (Compose BOM), Material 3 |
| Min / target SDK | minSdk 26 (required by supabase-kt), targetSdk 35, compileSdk 35 |
| Architecture | MVVM + repositories, unidirectional data flow, `StateFlow<UiState>` per screen |
| DI | Hilt (`hilt-android`, `hilt-navigation-compose`) |
| Navigation | Navigation Compose with type-safe routes (`@Serializable` route objects) |
| Backend | **supabase-kt 3.x** via BOM `io.github.jan-tennert.supabase:bom` (3.8.0 or newer): `auth-kt`, `postgrest-kt`, `storage-kt`, `realtime-kt`, plus `io.ktor:ktor-client-okhttp` (same Ktor major as supabase-kt) |
| JSON | kotlinx.serialization (`ignoreUnknownKeys = true`, `explicitNulls = false`) |
| Images | Coil 3 (`coil-compose`, `coil-network-okhttp`); Android Photo Picker for choosing photos |
| Map | osmdroid (`org.osmdroid:osmdroid-android`) inside `AndroidView` — OpenStreetMap, same as the web, no API key |
| Location | `play-services-location` (FusedLocationProviderClient) + runtime permission request |
| Local storage | DataStore (preferences: last feed scope, onboarding) |
| Tests | JUnit, kotlinx-coroutines-test, Turbine, MockK; Compose UI tests for login and feed |
| Build | Gradle Kotlin DSL + version catalog `gradle/libs.versions.toml`, JDK 17 |

Use the latest **stable** versions at the time you start. Don't use alpha or beta artifacts.

## 3. Project layout (inside the repo)

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── gradlew, gradlew.bat, gradle/wrapper/
├── .gitignore                      # build/, .gradle/, local.properties, *.jks, .idea/
├── README.md                       # how to configure and run
├── DEVELOPMENT_PROMPT.md           # this file
└── app/
    ├── build.gradle.kts
    └── src/main/java/com/neighborly/app/
        ├── NeighborlyApp.kt        # @HiltAndroidApp
        ├── MainActivity.kt         # handles auth deep links
        ├── core/
        │   ├── supabase/           # SupabaseModule (Hilt), session handling
        │   ├── designsystem/       # Theme, colors, type, Avatar, PostCard, ReactionPicker, …
        │   ├── location/           # LocationProvider
        │   └── util/               # timeAgo, price format, distance (port src/lib/format.js + geo.js)
        ├── data/
        │   ├── model/              # @Serializable DTOs (one per table/view/RPC result)
        │   └── repository/         # AuthRepo, FeedRepo, CommentRepo, ListingRepo, LostFoundRepo,
        │                           # EventRepo, ServiceRepo, HelpRepo, AlertRepo, NotificationRepo,
        │                           # ChatRepo, ReportRepo, ProfileRepo, LookupRepo, StorageRepo, ModerationRepo
        ├── feature/
        │   ├── auth/  feed/  post/  map/  marketplace/  services/  lostfound/
        │   ├── events/  messages/  alerts/  profile/  moderation/
        └── navigation/             # NavGraph, routes, bottom bar
```

## 4. Supabase connection

### 4.1 Config (no secrets in git)

`android/local.properties` (git-ignored):

```properties
SUPABASE_URL=https://mnebcomkejcvgvxsvwza.supabase.co
SUPABASE_ANON_KEY=<copy VITE_SUPABASE_ANON_KEY from .env.production>
```

`app/build.gradle.kts`: read `local.properties` first. If a key is missing, fall back to the repo's `../.env.production`, so CI and new clones work with no extra setup. Expose both values as `BuildConfig` fields.

```kotlin
import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val webEnv: Map<String, String> = rootProject.file("../.env.production").takeIf { it.exists() }
    ?.readLines()?.filter { "=" in it && !it.trimStart().startsWith("#") }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() } ?: emptyMap()

fun cfg(key: String, webKey: String): String =
    listOf(localProps.getProperty(key), System.getenv(key), webEnv[webKey])
        .firstOrNull { !it.isNullOrBlank() }
        ?: error("Missing $key: add it to android/local.properties")

android {
    buildFeatures { compose = true; buildConfig = true }
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${cfg("SUPABASE_URL", "VITE_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("SUPABASE_ANON_KEY", "VITE_SUPABASE_ANON_KEY")}\"")
    }
}
```

### 4.2 Client (Hilt singleton)

```kotlin
@Module @InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides @Singleton
    fun supabase(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; explicitNulls = false })
        install(Auth) {
            scheme = "neighborly"          // deep link: neighborly://auth-callback
            host = "auth-callback"
            flowType = FlowType.PKCE
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
```

- `MainActivity`: add an intent filter for `neighborly://auth-callback`, and call `supabase.handleDeeplinks(intent)` in `onCreate` and `onNewIntent`.
- **Owner action (put it in the PR description, don't do it yourself):** add `neighborly://auth-callback` under Supabase → Authentication → URL Configuration → Redirect URLs.
- Session state: expose `supabase.auth.sessionStatus` as a Flow. Load the profile row after `Authenticated`; show the login flow on `NotAuthenticated`.

### 4.3 Backend contract — use exactly these names

**Tables** (all have RLS): `profiles`, `neighborhoods`, `post_categories`, `posts`, `comments`, `reactions`, `marketplace_listings`, `lost_found_items`, `events`, `event_participants`, `services`, `service_categories`, `help_requests`, `help_responses`, `emergency_alerts`, `conversations`, `conversation_members`, `messages`, `notifications`, `reports`.

**Views:**
- `feed_posts`: a post plus `author_name`, `author_username`, `author_avatar`, `author_role`, `neighborhood_name`, `category_name`, `reaction_count`, `comment_count`, `share_count`, `my_reaction`, `top_reactions` (array) and `shared_post` (JSON object or null).
- `map_items`: `kind` (`post|listing|lost_found|event|help|alert|service`), `item_id` (text), `title`, `subtitle`, `latitude`, `longitude`, `created_at`.

**RPCs:**

| Function | Args | Returns |
|---|---|---|
| `start_conversation` | `other_user: uuid` | `bigint` conversation id (finds or creates the 1:1 chat) |
| `my_conversations` | – | rows: `conversation_id, other_user_id, other_name, other_username, other_avatar, last_message, last_sender_id, last_message_at, unread_count` |
| `admin_stats` | – | JSON of totals + `posts_by_day`, `signups_by_day`, `users_by_neighborhood` (moderators/admins only) |
| `admin_users` | – | user rows with email + last sign-in (moderators/admins only) |

**Enum values** (send them as strings):
- **Reactions:** `like, love, care, haha, wow, sad, angry`
- **Content and comment status:** `published, hidden, deleted` / `visible, hidden, deleted`
- **Listing type and status:** `sell, buy, exchange, free` / `available, reserved, sold, closed`
- **Item condition:** `new, like_new, used, for_parts`
- **Lost & found:** `lost, found` / `open, resolved`
- **Events and RSVP:** `upcoming, ongoing, completed, cancelled` / `going, interested`
- **Help type and status:** `medical, grocery, repair, transport, childcare, elderly_care, other` / `open, in_progress, resolved, closed`
- **Alerts:** severity `low, medium, high, critical`; status `active, resolved, expired`
- **Reports:** target `post, comment, event, help_request, service, listing, lost_found, user`; reason `spam, abuse, inappropriate, scam, false_info, harassment, other`
- **Roles and user status:** `resident, service_provider, moderator, admin` / `active, suspended, banned`

**Things the database already does — don't duplicate them in the app:**
- creates the profile at sign-up
- writes every notification (reactions, comments, replies, shares, RSVPs, help offers, alerts)
- keeps `last_message_at` / `last_read_at` up to date
- stops users changing their own role or status
- keeps moderator-hidden content hidden
- lets only admins set `services.is_verified`

### 4.4 Reference snippets (supabase-kt 3.x — check the exact API against the docs for the version you install)

```kotlin
// Models: snake_case via @SerialName, nullable where the column is nullable
@Serializable data class FeedPost(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("post_type") val postType: String,
    val title: String? = null,
    val body: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
    @SerialName("shared_post_id") val sharedPostId: Long? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("author_role") val authorRole: String,
    @SerialName("neighborhood_name") val neighborhoodName: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("reaction_count") val reactionCount: Int,
    @SerialName("comment_count") val commentCount: Int,
    @SerialName("share_count") val shareCount: Int,
    @SerialName("my_reaction") val myReaction: String? = null,
    @SerialName("top_reactions") val topReactions: List<String> = emptyList(),
    @SerialName("shared_post") val sharedPost: SharedPost? = null,
)

// Feed page (10 per page, newest first) — mirrors getFeed() in src/lib/api.js
suspend fun feed(page: Int, neighborhoodId: Long?) = supabase.from("feed_posts").select {
    filter {
        eq("status", "published")
        neighborhoodId?.let { eq("neighborhood_id", it) }
    }
    order("created_at", Order.DESCENDING)
    range(page * 10L, page * 10L + 9)
}.decodeList<FeedPost>()

// Comments with author + likes (embedded resources, same string as the web)
supabase.from("comments").select(
    Columns.raw("id, content, parent_id, created_at, user_id, status, author:profiles(id, full_name, username, avatar_url, role), reactions(user_id)")
) { filter { eq("post_id", postId); eq("status", "visible") }; order("created_at", Order.ASCENDING) }

// Ambiguous foreign keys MUST name the constraint:
//   notifications → actor:profiles!notifications_actor_id_fkey(id, full_name, avatar_url)
//   reports       → reporter:profiles!reports_reporter_id_fkey(...), reviewer:profiles!reports_reviewed_by_fkey(...)

// Sign-up: metadata keys are read by the handle_new_user trigger
supabase.auth.signUpWith(Email, redirectUrl = "neighborly://auth-callback") {
    email = form.email; password = form.password
    data = buildJsonObject {
        put("full_name", form.fullName); put("username", form.username.lowercase())
        put("phone", form.phone); put("neighborhood_id", form.neighborhoodId.toString())
    }
}

// RPC
val conversationId = supabase.postgrest.rpc("start_conversation",
    buildJsonObject { put("other_user", otherUserId) }).decodeAs<Long>()

// Photo upload: bucket "media", path MUST start with the user's id (storage RLS)
val uid = supabase.auth.currentUserOrNull()!!.id
val path = "$uid/post-${System.currentTimeMillis()}-${(1000..9999).random()}.jpg"
supabase.storage.from("media").upload(path, jpegBytes) { upsert = false }
val publicUrl = supabase.storage.from("media").publicUrl(path)

// Realtime: new messages in one chat
val channel = supabase.channel("conv-$conversationId")
channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
    table = "messages"
    filter("conversation_id", FilterOperator.EQ, conversationId)
}.map { it.decodeRecord<Message>() }.onEach { /* append if id not present */ }.launchIn(viewModelScope)
channel.subscribe()   // unsubscribe in onCleared()
// Same pattern for notifications: table = "notifications", filter user_id = current user id
```

**Reaction logic** (port `setReaction` from `src/lib/api.js`):
- Pass `null` → delete the user's row for that target.
- Otherwise update the existing row's `reaction_type`, or insert a new row.
- Update the UI optimistically and roll back if the call fails.

**Search text** in `or()` / `ilike` filters must have the characters `, ( ) % * \` replaced with spaces first (same as `clean()` in `api.js`).

## 5. Design — Facebook-style layout, Neighborly brand

Match the live web app's look in native Material 3. Use **no Facebook logos, names or assets**.

- **Colours:** primary green `#22774A` (dark `#1E4A33`, container `#E3F1E8`); background `#F0F2F5`; cards `#FFFFFF`; text `#050505` / secondary `#65676B`; error `#D93025`; accent dot `#E0663A`. Support dark mode by deriving a dark scheme from the same greens.
- **Shape and type:** cards with 8 dp corners and a 1 dp elevation look; system font (Roboto). Sizes: post text 15 sp, names 15 sp semibold, metadata 13 sp.
- **Top app bar:** round house logo + "Neighborly" in green bold; action icons for Search, Chats (with unread badge) and Notifications (with unread badge).
- **Bottom navigation (5 tabs, same as the web header):** Home, Map, Buy & sell, Events, Alerts (badge = active alerts). A Menu/profile entry reaches Feed filters, Services & Help, Lost & found, Messages, My profile, Moderation (moderators only) and Log out.
- **Home:**
  - a horizontal **"Nearby now"** strip of tall 110×200 dp cards (a "Create post" card first, then active alerts, upcoming events, new listings, lost & found)
  - a composer card ("What's happening around you, {first name}?" + Photo / Location / Ask buttons)
  - scope chips (my neighborhood / all)
  - an infinite feed
- **Post card:**
  - header: avatar, name, time · neighborhood · category; Admin and Pro badges; "Official announcement" badge
  - body, optional location pill, full-width image, and a shared-post box for shares
  - stats row: reaction emojis + count, comments, shares
  - action row: Like / Comment / Share. Long-press Like opens the 7-emoji picker (👍 ❤️ 🥰 😆 😮 😢 😡); tap toggles Like.
  - overflow menu: copy link, edit, delete, report
- **Comments:** bottom sheet or post detail screen; bubbles in grey `#F0F2F5`; Like / Reply / time under each; one level of replies.
- **Share:** bottom sheet with "Share now" (optional caption), "Send in Messages" (pick a chat) and "Copy link" (`https://sayed-swe.github.io/neighborly/post/{id}`).
- **Buy & sell:** a 2-column grid of square photo cards (price bold, title, place · time), filter chips for type and category, and a detail screen with a "Message seller" button and a Q&A thread.
- **Chat:** chat list, plus a thread with green bubbles for my messages and grey for theirs.
- **Polish:** loading skeletons, empty states with an action, and pull-to-refresh on every list.

## 6. Features and acceptance criteria (build in this order)

Each phase ends with a commit, a push and a green CI build.

| Phase | Scope | Done when |
|---|---|---|
| 1. Setup | Gradle project in `android/`, version catalog, Hilt, theme, nav shell with 5 tabs, Supabase client, `android.yml` CI | App launches; CI builds the debug APK |
| 2. Auth & profile (ACC-01…07) | Sign up (name, username 3–30 `[a-z0-9_.]`, phone, neighborhood picker from `neighborhoods`, email, password 8+), log in, log out, reset password, auth deep link, persisted session, blocked screen when `status != active`, profile view and edit (avatar, cover, bio, phone, address, neighborhood) | A demo user from `seed_demo.sql` can log in; a new sign-up creates a profile row |
| 3. Feed (FEED-01…13) | Composer (text, photo via Photo Picker, compressed to ≤5 MB JPEG at max 1600 px, category, audience, location), paged feed, reactions + picker + "who reacted" sheet, comments, replies, comment likes, share, edit/delete, report, post detail, search and filters, "Near me" radius (1/3/5/10 km), Nearby-now strip | Every action matches the web app on the same account and data |
| 4. Community (MKT, LF, EVT, SRV) | Buy & sell grid + detail + create/edit + status changes; Lost & found tabs + report + reunite; Events list + RSVP going/interested + detail + create/cancel; Services directory (call via `ACTION_DIAL`, message) + Help requests (ask, offer help, resolve) | Create/edit/delete flows work and RLS errors show as friendly messages |
| 5. Messages & notifications (MSG, NTF) | Chat list (`my_conversations`), thread with Realtime, start chat from profile/listing/service, mark read (update own `conversation_members.last_read_at`); notifications list, mark one/all read, tap → deep navigate; emergency alerts list + raise alert (severity, location) + resolve; live badge counts via Realtime | A message sent on the web shows on the phone within ~1 s, and the reverse |
| 6. Map (MAP-01…05) | osmdroid map of `map_items`, coloured markers per kind, toggle chips, "Near me" with radius circle, a distance-sorted list, marker → item | All seeded items with coordinates appear in the right place |
| 7. Moderation (MOD, part of ADM) | Visible only for `moderator` / `admin`: reports queue (open/closed), review screen (content + owner), actions dismiss / warn (insert `notifications` type `system`) / remove content / suspend / ban, `admin_stats` dashboard cards | The demo admin can resolve the seeded report |
| 8. Polish & release | Dark mode, accessibility (content descriptions, 48 dp targets), error and offline states, tests, signed release build instructions in README | Tests pass in CI; README explains setup |

The full admin tables (CSV export, lookups editor) stay on the web; don't rebuild them.

## 7. Quality bar

- ViewModels expose `StateFlow<UiState>`. Repositories return `Result<T>` or throw typed errors. No Supabase calls in composables.
- Paged lists: feed 10 per page; listings / lost & found / events / help capped at 50–60, as on the web; chats load the last 500 messages.
- Unit tests for repositories (MockK the client or wrap it behind interfaces) and ViewModels (Turbine). One Compose UI test each for login and posting.
- `./gradlew lintDebug testDebugUnitTest assembleDebug` passes locally and in CI.
- Strings in `strings.xml`; no hard-coded user-facing text in composables.
- Don't log tokens, passwords or personal data.

## 8. CI — `.github/workflows/android.yml`

```yaml
name: Android CI
on:
  push:
    branches: [main]
    paths: ['android/**', '.github/workflows/android.yml']
  pull_request:
    paths: ['android/**', '.github/workflows/android.yml']
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - name: Build, lint and test
        # Supabase config comes from ../.env.production (or repo secrets SUPABASE_URL / SUPABASE_ANON_KEY)
        env:
          SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
          SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}
        run: ./gradlew lintDebug testDebugUnitTest assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: neighborly-debug-apk
          path: android/app/build/outputs/apk/debug/*.apk
```

Also add this to `.github/workflows/deploy.yml` under `on.push`:

```yaml
    paths-ignore: ['android/**', '.github/workflows/android.yml']
```

---

## 9. GitHub — clone, branch, push (separate section)

Repository: **https://github.com/sayed-swe/neighborly**

```bash
# 1. Get the code
git clone https://github.com/sayed-swe/neighborly.git
cd neighborly

# 2. Work on a feature branch (never commit straight to main)
git checkout -b feature/android-app

# 3. Create the Android project inside android/ (Android Studio: New Project → Empty Activity,
#    location = <repo>/android, package com.neighborly.app, Kotlin DSL, minSdk 26)

# 4. Local config (git-ignored)
printf 'SUPABASE_URL=https://mnebcomkejcvgvxsvwza.supabase.co\nSUPABASE_ANON_KEY=<value of VITE_SUPABASE_ANON_KEY in .env.production>\n' > android/local.properties

# 5. Make sure secrets and build output are ignored
cat android/.gitignore     # must contain: build/ .gradle/ local.properties *.jks *.keystore .idea/ captures/

# 6. Commit each phase separately, prefix messages with "android:"
git add android .github/workflows/android.yml .github/workflows/deploy.yml
git commit -m "android: phase 1 - Compose project, Hilt, theme, nav shell, Supabase client, CI"
git push -u origin feature/android-app

# 7. Later phases
git add android
git commit -m "android: phase 2 - auth, sign-up, profile"
git push

# 8. Open a pull request into main
gh pr create --base main --head feature/android-app \
  --title "Android app (Kotlin + Jetpack Compose)" \
  --body "Native Android client in android/. Uses the existing Supabase project; no schema changes. Owner action: add neighborly://auth-callback to Supabase Auth redirect URLs."

# 9. Before each push: stay current with main
git fetch origin
git rebase origin/main
./android/gradlew -p android assembleDebug    # must pass before pushing
```

Commit message format: `android: <phase or area> - <what changed>`. The PR description must list:
- what was built
- any new migration file (with the reason)
- the owner actions (Supabase redirect URL, optional repo secrets)
- how to test with the demo accounts
