# Architecture: your file manager (a rebuild of EX File Manager's core local features)

Working name until `/replica-brand`: **Filebox**. Package: `dev.yourname.filebox` (placeholder).

This is a single offline Android app. There is no server, no account, no payments and no email. The pack's default web stack (Next.js, Postgres, Stripe...) does not apply, so each of those layers is marked "none" with a reason.

## Stack

| layer | choice | why |
| --- | --- | --- |
| language | Kotlin 2.x | First-class Android language. Every file and storage API is native, with no bridge. |
| UI | Jetpack Compose + Material 3 | Current standard toolkit. Lazy lists handle 10k-row folders. Theming feeds straight from `/replica-design` tokens. |
| why not Expo / React Native | n/a | A file manager is almost all native APIs (all-files access, SAF, MediaStore, foreground work, PackageInstaller). In RN you'd write those as native modules anyway. |
| navigation | Navigation Compose (type-safe routes) | One activity. Screens S01-S20 map to routes or dialogs. |
| state | ViewModel + Kotlin Flow / StateFlow | Survives rotation, and is easy to test with Turbine. |
| DI | Hilt | Standard for Android, wires Room, WorkManager and repositories with little code. |
| database | Room (SQLite), `schema.sql` | Only app metadata: favourites, trash, hidden, operations, search history. Runs on-device. |
| settings | Jetpack DataStore (Preferences) | Theme, default sort and view, show hidden, use trash. Key-value, so a table is overkill. |
| file access | `java.nio.file` / `java.io.File` for primary storage + `androidx.documentfile` (SAF) for SD/USB | With all-files access, paths are fastest. Removable volumes on Android 11+ need SAF tree URIs. Both sit behind one `FileSource` interface. |
| indexes | MediaStore (`MediaStore.Files`) | Categories, recent files and name search come from the system index: instant, with no crawler of our own. |
| thumbnails | Coil 3 + `coil-video` + a custom APK-icon fetcher | Memory and disk cache, cancels on scroll. |
| background work | WorkManager (expedited, with a foreground-service notification) | Long copy/move survives leaving the app and gets a progress notification. A serial unique queue avoids races. |
| testing | JUnit 5, Turbine, Robolectric for FileSource, Compose UI tests + an emulator | FileSource logic is tested against a temp dir. UI flows run on an API 30 and an API 35 emulator. |
| build | Gradle Kotlin DSL + version catalog. minSdk 26, targetSdk 35 (bump to current) | minSdk 26 covers ~97% of devices and keeps the legacy-storage path small (Android 8-10 only). |
| auth | none | No accounts. Single user. |
| payments | none | Personal build. |
| email | none | n/a |
| network | none: **no INTERNET permission** | Everything is local. A no-network file manager is a privacy feature worth keeping. |
| hosting / distribution | sideload a signed APK. Google Play optional later. | Play requires a permission declaration form for MANAGE_EXTERNAL_STORAGE (file managers qualify). |

## Module layout

```
app/
  ui/            screens (S01..S20), components, theme
  domain/        FileRef, FileEntry, Category, SortSpec, use cases
  data/
    fs/          FileSource (interface), PathFileSource, SafFileSource, VolumeRepository
    media/       MediaStoreIndex (categories, recent, search)
    db/          Room entities + DAOs (schema.sql)
    prefs/       SettingsRepository (DataStore)
    ops/         FileOperationRepository, FileOperationWorker, ConflictResolver
  platform/      Intents (open/share/install), PermissionManager, ShortcutHelper
```

`FileRef` is a sealed type, `Path(absolutePath)` or `Doc(treeUri, documentId)`. Everything above `data/fs` only sees `FileRef`, so the UI never cares which storage backend a file lives on.

## Schema

Tables: 7 (`favorites`, `trash_items`, `hidden_items`, `search_queries`, `file_operations`, `file_operation_items`, `folder_view_prefs`). Full DDL in `replica/schema.sql`.

Access rules: none are needed in the database. It lives in the app's private sandbox (`/data/data/<pkg>/databases`), there is one user, and nothing syncs. Authorisation is the OS's job: the all-files-access grant plus SAF tree grants. Each grant is checked in `PermissionManager` before any `FileSource` call, and a missing grant sends the user to S02 or S19.

Deviations from the pack's defaults, on purpose:
- `INTEGER` rowid keys, not uuid: single device, nothing to merge.
- Times stored as epoch-ms UTC `INTEGER`. SQLite has no timestamptz.
- No owner column: one user.
- Uniqueness is enforced in the database: `favorites.location`, `trash_items.trash_path`, `hidden_items.hidden_path` and `search_queries(text, filters)`. Favouriting twice or a double-tapped trash becomes a constraint hit handled as an upsert, not a duplicate row.
- `file_operation_items` has per-item status, so a resumed operation skips finished items (see idempotency).

Derived at runtime, never stored: StorageVolume (from `StorageManager`), FileEntry (from the FileSource), categories and recent files (MediaStore queries).

## API

There is no HTTP API. The "routes" are the repository and use-case functions the ViewModels call, plus the system intents the app sends.

### Storage and browsing

| function | does | who | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `PermissionManager.state()` | All-files / legacy permission status | S01 start, S02 | none | Granted, Denied, NeedsLegacy | F01 |
| `PermissionManager.requestAllFiles()` | Launches `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (or `READ/WRITE_EXTERNAL_STORAGE` on 8-10) | S02 | none | result on resume | F01 |
| `VolumeRepository.volumes()` | Lists mounted volumes with free and total space, live on mount/eject | S01, S16 | none | `Flow<List<StorageVolume>>` | F13 |
| `VolumeRepository.grantTree(volume)` | Launches `ACTION_OPEN_DOCUMENT_TREE`, persists the URI permission | S19 | volume | `Result<FileRef.Doc>` | F13 |
| `FileSource.list(dir, showHidden)` | Folder contents, off the main thread | S03, S08 | FileRef, flag | `List<FileEntry>` | F03-F05, F08 |
| `FileSource.stat(ref)` | Size, dates, type | S11 | FileRef | FileEntry | none |
| `FileSource.folderSize(ref)` | Recursive size and count, cancellable, streams partial totals | S11, list (could) | FileRef | `Flow<SizeProgress>` | none |
| `SortSpec.apply(list)` | Folders first, then by name (natural, locale-aware) / date / size / type | S03, S04 | list, spec | list | F12 |

### Index (MediaStore)

| function | does | who | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `MediaStoreIndex.categoryCounts()` | Counts per category, re-queries on a `ContentObserver` change | S01 | none | `Flow<Map<Category, Int>>` | F02 |
| `MediaStoreIndex.category(cat, sort)` | Paged files of one type (MIME plus an extension list for docs, APKs, archives) | S04 | Category, SortSpec | `PagingData<FileEntry>` | F02, F14 |
| `MediaStoreIndex.recent(days)` | Files by `DATE_MODIFIED`, grouped by local day | S01, S05 | days = 30 | grouped list | none |
| `MediaStoreIndex.search(query, filters)` | `DISPLAY_NAME LIKE ? ESCAPE '\'` plus MIME/size/date where-clauses | S06 | text, filters | `PagingData<FileEntry>` | F07 |
| `SearchRepository.remember / recent()` | Upsert into `search_queries`, keep the last 20 | S06 | text, filters | list | F07 |
| `MediaScanner.notify(paths)` | `MediaScannerConnection.scanFile` after every change | ops worker | paths | none | F03-F08 |

### File operations

| function | does | who | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `Ops.enqueue(type, sources, dest, policy)` | Inserts the operation and its items, enqueues `FileOperationWorker` on the unique chain `file-ops` (APPEND) | S07, S08, S12, S15 | type, refs, dest | operation id | F03, F04, F06 |
| `Ops.observe(id)` / `Ops.active()` | Progress for the dialog and notification | S09 | id | `Flow<FileOperation>` | F03, F04 |
| `Ops.resolveConflict(id, choice, applyAll)` | Resumes an operation waiting in `conflict` | S09 conflict dialog | skip / replace / keep_both | none | F03 |
| `Ops.cancel(id)` | Cancels the work, deletes the partial target | S09 | id | none | F03 |
| `FileActions.rename(ref, newName)` | Validates (illegal chars, exists, extension change), renames in place | S10 | FileRef, name | `Result<FileRef>` | F05 |
| `FileActions.createFolder / createTextFile` | Creates in the current dir | S10 | parent, name | `Result<FileRef>` | F08 |
| `TrashRepository.list / restore / purge / empty` | Recycle bin | S15 | ids | none | F06 |
| `HiddenRepository.hide / unhide / list` | Dot-prefix rename plus a `hidden_items` row | S07, S14 | refs | none | F11 |
| `FavoritesRepository.add / remove / list` | Bookmarks. `list` flags rows whose target no longer exists | S07, S13, S01 | ref | `Flow<List<Favorite>>` | F10 |

### Intents out (official Android APIs)

| intent / API | does | who | flow |
| --- | --- | --- | --- |
| `ACTION_VIEW` + `FileProvider` URI + `FLAG_GRANT_READ_URI_PERMISSION` | Open with the default or chosen app | S03-S06 | F02 |
| `ACTION_SEND` / `ACTION_SEND_MULTIPLE` | Share sheet | S07 | F09 |
| `ACTION_VIEW` on an APK (`application/vnd.android.package-archive`) + `REQUEST_INSTALL_PACKAGES` | System installer | S20 | F14 |
| `PackageManager.getPackageArchiveInfo` | APK label, icon, version for S20 | S20 | F14 |
| `ShortcutManagerCompat.requestPinShortcut` | Folder shortcut on the launcher | S07 (could) | none |

Intents in: the launcher only for v1. A shortcut deep link `filebox://open?path=` opens S03 at that path. `ACTION_GET_CONTENT` (act as a file picker for other apps) is a could-have for later.

Webhooks in / out: none.

Jobs:
- `FileOperationWorker`: on demand, serial. Expedited with a foreground notification (type `dataSync`). Processes `file_operation_items` one at a time.
- `TrashPurgeWorker`: periodic every 24h, while idle. Hard-deletes trash older than 30 days (configurable) and drops rows whose `trash_path` no longer exists.
- `OperationRecovery`: at app start. Any `running` operation with no live work is set back to `queued` and re-enqueued.

## The parts that bite

- **Storage permission matrix:** Android 8-9 use legacy READ/WRITE. Android 10 adds `requestLegacyExternalStorage=true`. Android 11+ use MANAGE_EXTERNAL_STORAGE. `Android/data` and `Android/obb` are off-limits on 11+, so show an "inaccessible" state on S03 and don't try to work around it. Removable volumes need SAF on 11+. Test all three API bands.
- **Two file backends:** SAF is 10-100x slower than paths for listing. Batch with `DocumentsContract` child queries (one cursor) instead of `DocumentFile.listFiles()` plus per-file calls.
- **Idempotency / resume:** copy each file to `.<name>.partial` in the target, fsync, then rename. On resume, `pending` items with a leftover `.partial` start over. `done` items are skipped. Moving on the same volume is an atomic rename. Moving across volumes is copy, verify size, then delete the source, marking the item `done` only after the delete.
- **Races:** one operation runs at a time (unique WorkManager chain), so two pastes never interleave. Files can still change outside the app: re-stat before each item, fail that item with "source missing" and carry on. Refresh the open folder on `ON_RESUME` and after each operation, plus a `FileObserver` on the current dir.
- **Copying into itself:** block a destination that equals the source or sits under it, before enqueueing. Don't follow symlinks while recursing (cycles).
- **File size limits:** FAT32 SD cards can't hold files over 4 GB, so check volume fs type and size up front. Check free space against `bytes_total` before starting, and stop cleanly on ENOSPC.
- **Name rules:** FAT/exFAT are case-insensitive, so "a.txt" to "A.txt" needs a two-step rename. Reject `/ \ : * ? " < > |` and trailing dots/spaces. Keep the extension selected-out in the rename field.
- **Search:** the MediaStore index lags new files and ignores `.nomedia`/hidden folders. Call `scanFile` after every operation. Offer an optional "deep search" that walks the current folder for anything the index misses.
- **Big folders:** list and sort off the main thread, use stable `key`s in LazyColumn, page the MediaStore results, and cancel thumbnail loads on fast scroll.
- **Time zones:** stored as UTC ms. "Today / Yesterday" grouping and date filters use the device's current zone. Nothing else depends on time.
- **Trash location:** `<volume root>/.filebox_trash/` with a `.nomedia` file, so moving into the bin is a same-volume rename (instant). If the volume is ejected, hide its rows rather than deleting them.
- **Play policy:** MANAGE_EXTERNAL_STORAGE needs the Play declaration. Fine for sideloading now.
- **Privacy / GDPR:** no network permission and no analytics, so no data leaves the device. Uninstalling removes the database (the trash folder on shared storage stays, so offer "empty bin" before uninstall).
- n/a here: rate limits, realtime, email deliverability, multi-tenancy, webhooks.

## Build order

1. **Vertical slice (a weekend):** grant permission, browse internal storage, open a file, create a folder, rename, delete permanently. Synchronous, ugly.
   Screens S02, S03, S10, S12 (+ a basic S07 long-press). Tables: none. Functions: `PermissionManager.*`, `PathFileSource.list/rename/createFolder/delete`, `ACTION_VIEW` via FileProvider.
   Proves: the permission flow on API 30+, the FileRef/FileSource abstraction, and Compose lists.

2. **Must-haves**
   - 2a Home and index: S01, S04, S05. `MediaStoreIndex.categoryCounts/category/recent`, `VolumeRepository.volumes` (primary only), thumbnails with Coil.
   - 2b Operations engine: S07 (full), S08, S09, S11. Tables `file_operations`, `file_operation_items`. `Ops.*`, `FileOperationWorker`, `OperationRecovery`, `ConflictResolver`, `MediaScanner.notify`, share intent.
   - 2c Search, sort and view: S06 (name search only), S17. `MediaStoreIndex.search`, `SortSpec`, DataStore settings. Empty states on S03-S06.

3. **Should-haves**
   - 3a SD/USB: S16, S19, `SafFileSource`, `VolumeRepository.grantTree`, eject handling.
   - 3b Recycle bin and undo: S15, `trash_items`, `TrashRepository`, `TrashPurgeWorker`, undo snackbar.
   - 3c Favourites and hidden: S13, S14, `favorites`, `hidden_items`.
   - 3d Search filters (S06 chips), settings and dark theme (S18), new text file, APK sheet and install (S20).
   - **Could-haves:** launcher shortcuts, folder sizes in the list, recent searches (`search_queries`), per-folder view (`folder_view_prefs`), batch rename, near-full warning.

4. **Fixes from replica-entrepreneur:** run `/replica-entrepreneur` after the first parity check. Likely themes for this app: ad load, upsell nags, permissions and privacy. The no-ads, no-network build already answers those.
