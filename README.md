# SafeVault

> An offline, hardware-encrypted notes & secrets vault for Android — your secrets never leave the device, never touch the network, and never hit the disk in plaintext.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-35-blue)
![Language](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Clean-informational)
![License](https://img.shields.io/badge/license-MIT-green)

SafeVault is a security-first showcase app: a personal vault where each note's
content is encrypted with **AES-256-GCM** using a **non-exportable,
auth-bound key held in the Android Keystore** (StrongBox-backed where the
hardware supports it). Biometrics are not a UI gate here — the Keystore itself
refuses to run the cipher without a recent user authentication. The app is
**100% offline** — it declares no `INTERNET` permission, so vault data
physically cannot be exfiltrated.

---

## Features

- **Encrypted notes** — create, edit, delete, tag and search notes; every
  note's body is stored as ciphertext, never plaintext.
- **Biometric lock screen** — unlock with fingerprint/face (class-3 STRONG)
  with an automatic **device-credential (PIN/pattern/password) fallback**. The
  vault key is bound to that authentication at the OS level.
- **Auto-lock** — the vault re-locks when the app is backgrounded and after a
  **configurable idle timeout** (immediate → 10 minutes).
- **Anti-leak UX** — `FLAG_SECURE` on the app window blocks screenshots and
  hides every screen from the recent-apps preview; copied secrets are
  **auto-wiped from the clipboard** after 30 seconds and flagged sensitive on
  Android 13+.
- **Tags & search** — case-insensitive search over titles, tags and note bodies,
  filtered in memory over the notes already decrypted for the list (typing costs
  no extra Keystore operations).
- **Material You** — dynamic color on Android 12+, curated brand palette
  fallback, and light / dark / system themes.
- **Robust states** — dedicated loading, empty, "no search results" and error
  states throughout.

### Why the security matters here

> **Defense in depth is the product, not a checkbox.** Keys live in
> hardware and are never readable by the app; content is authenticated-encrypted
> so tampering is detected; the app is provably offline (no network permission);
> and the runtime actively fights leakage (secure windows, clipboard wiping,
> backup exclusion, aggressive auto-lock). Each control is mapped to its source
> file in the [Security](#security) table below.

---

## Tech stack

| Concern            | Choice                                                        |
|--------------------|--------------------------------------------------------------|
| Language           | Kotlin 2.0 (K2 compiler)                                      |
| UI                 | Jetpack Compose + Material 3 (Material You dynamic color)     |
| Architecture       | MVVM + clean `data` / `domain` / `ui` layering               |
| DI                 | Hilt (Dagger)                                                 |
| Persistence        | Room (encrypted content columns) + DataStore (preferences)   |
| Async              | Kotlin Coroutines + Flow                                      |
| Navigation         | Navigation-Compose                                            |
| Security           | Android Keystore (AES-256-GCM via Cipher), BiometricPrompt    |
| Build              | Gradle Kotlin DSL + version catalog (`libs.versions.toml`)    |
| Testing            | JUnit4, MockK, Turbine, coroutines-test                      |

---

## Architecture

Clean, unidirectional, single-activity architecture. Dependencies point inward:
`ui → domain → data`, with interfaces in `domain` and implementations in `data`.

```
com.safevault.app
├── data
│   ├── crypto      → CryptoManager + KeystoreCryptoManager (AES-256-GCM)
│   ├── db          → Room: VaultDatabase, NoteDao, NoteEntity, Converters
│   └── repository  → NoteRepositoryImpl (encrypt/decrypt), SettingsRepositoryImpl
├── domain
│   ├── model       → Note, AppSettings, ThemeMode, AutoLockTimeout
│   ├── repository  → NoteRepository, SettingsRepository (interfaces)
│   └── usecase     → SaveNote / Delete / ObserveNotes / GetNote
├── di              → Hilt modules (Data, Repository, Dispatcher)
├── ui
│   ├── theme       → Material 3 theme + dynamic color
│   ├── navigation  → routes + NavHost
│   ├── screens     → lock / vault / editor / settings (+ ViewModels)
│   └── common      → loading / empty / error state views
├── util            → VaultLockManager, BiometricAuthenticator, SecureClipboard
└── MainActivity    → single-activity host + lock gate + FLAG_SECURE
```

**Data flow (write path):** `EditorViewModel` → `SaveNoteUseCase` →
`NoteRepositoryImpl` → `CryptoManager.encrypt()` → `NoteDao` (stores
`iv + ciphertext`). **Read path** reverses it, decrypting each row lazily and
skipping (rather than crashing on) any individual row that cannot be read.
Session-wide failures are *not* skipped: an expired authentication window or an
invalidated key propagates so the vault locks instead of rendering an
empty-looking vault.

**Lock gate:** `VaultLockManager` is the single source of truth for the unlocked
state. The `NavHost` (unlocked UI) is only composed while unlocked; otherwise the
`LockScreen` is shown and `BiometricPrompt` is presented by the Activity.

---

## Security

Every control maps to the file that implements it:

| Control                                          | Implementation |
|--------------------------------------------------|----------------|
| AES-256-GCM authenticated encryption             | `data/crypto/KeystoreCryptoManager.kt` |
| Key bound to biometric / device-credential auth  | `data/crypto/KeystoreCryptoManager.kt` (`setUserAuthenticationRequired`) |
| Non-exportable, hardware-backed key (StrongBox)  | `data/crypto/KeystoreCryptoManager.kt` |
| Per-record random IV, stored with ciphertext     | `data/crypto/CryptoManager.kt`, `data/db/NoteEntity.kt` |
| No plaintext at rest (ciphertext columns only)   | `data/db/NoteEntity.kt`, `data/repository/NoteRepositoryImpl.kt` |
| Biometric + device-credential unlock             | `util/BiometricAuthenticator.kt`, `MainActivity.kt` |
| Auto-lock on background + idle timeout           | `util/VaultLockManager.kt`, `MainActivity.kt` |
| `FLAG_SECURE` (block screenshots / recents)      | `MainActivity.kt` (whole window) |
| Clipboard auto-clear + sensitive flag            | `util/SecureClipboard.kt` |
| No network access (offline guarantee)            | `AndroidManifest.xml` (no `INTERNET` permission) |
| Backup / device-transfer exclusion of vault data | `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `allowBackup="false"` |
| No secrets in source / VCS                        | `.gitignore` (keystores, `local.properties`), no hardcoded keys |

### Threat notes

- **Auth binding (and its limits):** the key is created with
  `setUserAuthenticationRequired(true)` plus
  `setUserAuthenticationParameters(<window>, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`
  (`setUserAuthenticationValidityDurationSeconds` below API 30). The window
  equals the longest auto-lock the user can select, so the OS window is never
  shorter than a legitimately unlocked session. Once it elapses the Keystore
  throws `UserNotAuthenticatedException`, which locks the vault and sends the
  user back through `BiometricPrompt`.
  A **time-bound** key was chosen over per-operation authentication
  (`setUserAuthenticationParameters(0, …)`) deliberately: a per-operation key
  requires a `BiometricPrompt` + `CryptoObject` round trip for every single
  `Cipher`, i.e. one prompt per note on every list render. The cost of that
  choice is stated plainly: a time-bound key is bound to the secure lock screen,
  **not** to the biometric enrolment set, so adding a new fingerprint does not
  invalidate it. Removing or resetting the lock screen does.
- **Key invalidation:** when the OS destroys the key the Keystore throws
  `KeyPermanentlyInvalidatedException`. SafeVault handles it instead of
  crashing: the dead alias is dropped so a fresh key can be created, the vault
  locks, and the lock screen states that notes written before the change can no
  longer be decrypted. Those rows are skipped in the list rather than shown as
  garbage. They are **not** deleted automatically — wiping user data is not a
  decision the app makes silently.
- **Title/tags are metadata:** to keep search index-friendly, titles and tags
  are stored in plaintext. The UI guides users to keep secrets in the *content*
  field, which is always encrypted.

### Dependency choices

- **`androidx.biometric:biometric:1.1.0`** — the newest *stable* release. The
  1.2.0/1.4.0 lines are alpha-only and the `biometric-ktx` artifact has never
  had a stable release at all, so a security-critical app has no business
  shipping it: nothing here uses the `-ktx` coroutine wrappers
  (`setAllowedAuthenticators`, `BIOMETRIC_STRONG` and `DEVICE_CREDENTIAL` all
  exist in 1.1.0). Revisit when 1.4.0 goes stable.

### R8 / ProGuard

Release builds enable **R8 full mode** (`isMinifyEnabled = true`,
`isShrinkResources = true`). `app/proguard-rules.pro` keeps only what reflection
needs: Room generated code, Hilt/Dagger components,
and BiometricPrompt. Verbose `Log.v/d/i`
calls are stripped from release via `-assumenosideeffects`. Always smoke-test a
`release` build after changing dependencies, since full-mode R8 is aggressive.

---

## Build & Run

### Required SDKs / tooling

| Tool                | Version                          |
|---------------------|----------------------------------|
| Android Studio      | Ladybug (2024.2.1) or newer      |
| Android Gradle Plugin | 8.7.3                          |
| Gradle              | 8.11.1 (via wrapper)             |
| Kotlin              | 2.0.21                           |
| JDK                 | 17 (bundled with Android Studio) |
| compileSdk / targetSdk | 35                            |
| minSdk              | 26 (Android 8.0)                 |

### Steps

```bash
# 1. Clone
git clone <your-fork-url> && cd mobile-safevault-kotlin

# 2. Open in Android Studio (recommended) — it will sync Gradle automatically.
#    Or from the CLI once a JDK 17 + Android SDK are configured:
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run JVM unit tests
./gradlew installDebug           # install on a running device/emulator
```

> **Note on the Gradle wrapper:** all wrapper files — including
> `gradle/wrapper/gradle-wrapper.jar` — are committed, which is what makes
> `./gradlew` reproducible on a clean checkout and in CI.

To try biometrics on an emulator: create an API 30+ AVD, enroll a fingerprint in
**Settings → Security**, then use *Extended Controls → Fingerprint* to simulate a
touch at the prompt.

---

## Testing

Pure-JVM unit tests cover the security-critical and business logic without
requiring a device:

- `NoteRepositoryImplTest` — proves content is **stored encrypted and read back
  decrypted** (plaintext never appears in the store) using a reversible fake
  crypto and an in-memory DAO.
- `VaultLockManagerTest` — auto-lock policy: below/above the timeout, the
  `IMMEDIATELY` option, and the key-invalidation latch. Uptime is stubbed while
  real time barely moves, so an implementation reading the wall clock instead of
  `SystemClock.elapsedRealtime()` fails these tests.
- `NoteMatchesTest` — the search rule (title/tags/content, case-insensitive).
- `SaveNoteUseCaseTest` — validation, insert-vs-update routing, tag normalization.
- `ConvertersTest`, `AutoLockTimeoutTest` — persistence + policy mapping.

`KeystoreCryptoManager` has **no** unit test: `AndroidKeyStore` and
`KeyGenParameterSpec` only exist on a device/emulator, so a JVM test could only
assert against a mock of the API under test. It is exercised manually on a
device (see *Build & Run*).

```bash
./gradlew testDebugUnitTest
```

---

## What this demonstrates

- **Mobile security engineering:** correct use of the Android Keystore,
  AES-256-GCM with proper IV handling, biometric auth with credential fallback,
  `FLAG_SECURE`, clipboard hygiene, backup exclusion, and a genuine offline
  guarantee.
- **Clean architecture & testability:** strict `data`/`domain`/`ui` separation,
  repository interfaces in the domain, use-cases encapsulating business rules,
  and JVM-only tests for the critical paths.
- **Modern Android UI:** idiomatic Jetpack Compose + Material 3 with Material You
  dynamic theming, unidirectional state, and complete empty/loading/error states.
- **Production-grade tooling:** Hilt DI, Room + DataStore, a Gradle version
  catalog, and R8 full-mode release configuration with curated keep rules.

---

## License

MIT — see `LICENSE`.
