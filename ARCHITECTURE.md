# Architecture

How the code fits together. `CLAUDE.md` holds the rules; this holds the shape, so it is read
when you need it rather than loaded into every session.

Single-Activity MVVM. `MainActivity` creates the `Repository`, then swaps between fragments based on app state. An `AppFragmentFactory` builds them, so they come back with their ViewModels after rotation or process death:

- **WelcomeFragment** — shown when no file is chosen yet (and the app isn't in "New file by date" mode). Creates a file (`NewLogFile`: `ACTION_CREATE_DOCUMENT`, named `journal.md`) or picks one, takes a persistable permission on it, then calls back into MainActivity to open the log.
- **LogFragment** — main screen. Displays the log file in a text editor, a shortcut tray (horizontal staggered RecyclerView), and save/settings buttons. Saves on pause (smart save: skips if content hash unchanged) and on explicit button press (force save). On resume it calls `openCurrentFile`, which picks up a file changed in settings and resets the cursor when the file is different.
- **SettingsFragment** — manage shortcuts (add, edit, delete, reorder via drag-and-drop) and import/export them. Rows at the top open the File and Appearance screens.
- **LogFileSettingsFragment** (File) — "One file" or "New file by date". In one-file mode it shows the current file with **Select file** (`ACTION_OPEN_DOCUMENT`) and **New file** (`ACTION_CREATE_DOCUMENT`); either takes a persistable read/write permission before switching and releases the one on the file it replaces (the system caps how many an app keeps); the old file itself is left as it is. In dated mode it picks the folder and the file name template.
- **AppearanceSettingsFragment** — the editor text size.

Each fragment takes a pre-constructed ViewModel (not a factory lookup) passed from MainActivity, except `SettingsViewModel`, which the three settings screens share through `ViewModelProvider` so it lives as long as the Activity.

## Repository Layer

`Repository` implements `RepositoryInterface`, which composes:

- **`FileRepositoryInterface`** — reads/writes the user's log file via Android `ContentResolver` (URI-based access). Uses MD5 hashing to implement smart save (skip if unchanged). File URI and cursor index are stored in `SharedPreferences`.
- **`ShortcutRepositoryInterface`** — CRUD operations against `ShortcutDao` (Room). Shortcuts have a `position` field for ordering; drag-to-reorder updates all positions. Restoring a backup validates each row (unique label, non-empty text, valid cursor index, valid type). The "Add several shortcuts" dialog adds rows of name, text and cursor position (`ShortcutRow`); the cursor follows the end of the text until the person types one, and the type comes from whether the text has a `{DATETIME: …}` token.

## Shortcut System

`Shortcut` is the Room entity (`@Entity`). Fields: `label` (PK), `value`, `cursorIndex`, `type`, `position`.

Two shortcut types (`ShortcutType`):
- `TEXT` — inserts `value` verbatim.
- `DATETIME` — value contains `{DATETIME: <pattern>}` tokens replaced at insert time using `java.time.DateTimeFormatter` patterns (Android O+). `ShortcutUtils` handles replacement and adjusts `cursorIndex` to account for the expanded datetime string length.

JSON export format (v3.1.0+): `{ "schemaVersion": N, "shortcuts": [...] }`. Legacy CSV import remains available but is being phased out.

## Key Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Fragment orchestration, startup migration warning |
| `repository/Repository.kt` | Concrete repository; wires together file + shortcut interfaces |
| `repository/FileRepositoryInterface.kt` | File read/write, smart save via MD5 hash |
| `repository/ShortcutRepositoryInterface.kt` | Shortcut CRUD, bulk add, validation, export rows |
| `repository/Shortcut.kt` | `Shortcut` entity + `ShortcutDao` + `ShortcutType` |
| `repository/ShortcutDatabase.kt` | Room database definition |
| `utils/ShortcutUtils.kt` | Datetime token replacement at insert time |
| `utils/JsonShortcutUtils.kt` | JSON import/export with Gson |
| `ui/log/LogViewModel.kt` | File load/save, shortcut list exposure |
| `ui/settings/SettingsViewModel.kt` | Shortcut management, file selection, import/export |
| `ui/settings/LogFileSettingsFragment.kt` | File screen: select or create the log file, or set up a file by date |
