# Recon map: EX File Manager (Android)

Scope: core local file management. Browse, categories, recent files, search, multi-select copy/move/rename/delete/share, create folder or text file, favourites, hidden files, recycle bin, storage overview, SD/USB volumes, settings.
Not in scope (this pass): cloud drives, FTP/SFTP/WebDAV/SMB, archives, encryption, cleaner/analyzer, built-in viewers/players/editor, APK explorer internals.
For: personal use / learning project.
Date: 2026-11-11

## Clean-room note

Ace Mobile's terms of use (source 6) forbid copying the app or its trademarks, extracting its source code, and making "derivative versions". So this clone:
- is built from public listings and normal use only. No decompiling the APK, no extracting resources, no logging its network traffic.
- uses its own name, icon, colours, copy and assets.

Functionality and common UX patterns (file lists, a categories grid, a selection action bar) are standard across file managers and are not owned by Ace Mobile.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | Google Play listing | https://play.google.com/store/apps/details?id=com.ace.ex.file.manager | Page did not render for the fetch tool. Get screenshots by hand on a phone. |
| 2 | Cafe Bazaar mirror of the listing | https://cafebazaar.ir/app/com.ace.ex.file.manager?l=en | Full feature description, v1.6.1, updated 2026-10-15. The best feature list available. |
| 3 | BlueStacks app page | https://www.bluestacks.com/campaign/com.ace.ex.file.manager/en | Feature list: the home screen shows file and app counts, hide, shortcuts, bookmarks. |
| 4 | AppGoblin | https://appgoblin.info/apps/com.ace.ex.file.manager | 24.4M installs, 4.32 stars from 50K ratings, ads + IAP (est. 72% IAP), 9 ad SDKs, Firebase |
| 5 | Developer site | https://acemobileapps.net | Ace Mobile (HK). Support: acemobileapps@outlook.com. No help centre or docs. |
| 6 | Terms of use | https://acemobileapps.net/terms-of-use | No copying, no source extraction, no derivative versions (see the clean-room note) |
| 7 | APKMirror version history | https://apkmirror.com/apk/ace-mobile/ex-file-manager-file-explorer/ | 403 for the fetch tool. Versions 1.3.4 to 1.4.4 and later show up in search. |
| 8 | Your own phone | n/a | Install the app and screenshot each screen below into `replica/screens/` (S01.png ...). Needed to confirm the states marked "verify". |

No help centre, changelog or public API exists, so the screen list is inferred from the feature list plus standard Android file manager patterns. Rows marked **(verify)** need a look at the real app.

## Core loop

Open the app, find a file fast (by category, recent files or folder), then act on it: open, share, copy/move, rename or delete.

## Screens

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Home | app launch | Storage summary plus quick entry points | storage usage card (used/total, file and app counts), category grid, recent files strip, favourites, volumes list | loading (counting), filled, no-permission redirect |
| S02 | Permission onboarding | first launch, or when access is revoked | Explain and request "All files access" (MANAGE_EXTERNAL_STORAGE) | illustration, explainer text, Grant button, system settings handoff | default, returned-denied, granted |
| S03 | Folder browser | Home > volume / folder / breadcrumb | Navigate the file tree | top bar, breadcrumb path, file list/grid rows, FAB (new), sort/view menu | loading, empty folder, filled, inaccessible (Android/data), large dir |
| S04 | Category list | Home > category tile (Images, Videos, Audio, Docs, APKs, Downloads, Archives) | Flat list of files of one type across storage | list/grid, thumbnails, group-by-date or folder (verify), sort | loading, empty, filled |
| S05 | Recent files | Home > recent "see all" | Files changed recently, grouped by day | date section headers, thumbnail rows | empty, filled |
| S06 | Search | search icon on S01/S03/S04 | Find files by name with filters | search field, filter chips (type, size, date), results list, recent queries | idle, typing, no results, results |
| S07 | Selection mode | long-press any file row | Act on many files | contextual action bar (count, select all), bottom actions: copy, move, delete, rename, share, more (hide, favourite, shortcut, properties) | 1 selected, many selected, all selected |
| S08 | Destination picker | S07 > Copy/Move | Choose the target folder | folder browser in pick mode, "Paste here" / Cancel bar, new-folder button | default, same-folder (disabled), no-permission volume |
| S09 | Operation progress | after paste/delete of many files | Track a long copy/move/delete | progress dialog (file n of m, bytes, speed), Hide/Cancel, foreground notification, conflict dialog (skip/replace/keep both, apply to all) | running, conflict, done (toast), failed (insufficient space, permission) |
| S10 | Name dialog | FAB > new folder / new text file; S07 > rename | Create or rename | text field with extension handling, OK/Cancel | default, invalid name, name exists |
| S11 | Properties sheet | S07 > more > properties | File/folder details | name, path (copyable), size (folder size computed async), modified date, type, item count | single, multi (total size), computing |
| S12 | Delete confirm | S07 > delete | Confirm and choose bin vs permanent | dialog, "delete permanently" checkbox (verify) | default |
| S13 | Favourites | Home section / drawer | Bookmarked folders and files | list, remove action | empty, filled, target-missing |
| S14 | Hidden files | drawer or settings (verify) | Manage files hidden via the app | list, unhide action | empty, filled |
| S15 | Recycle bin | drawer (verify: not confirmed in public sources) | Restore or purge deleted files | list with deleted-at and original path, restore, delete forever, empty bin | empty, filled |
| S16 | Navigation drawer | hamburger on S01 | Global nav | volumes (internal, SD, USB), favourites, hidden, recycle bin, settings | with or without SD/USB |
| S17 | Sort & view sheet | S03/S04 overflow | Change list presentation | sort by name/date/size/type, asc/desc, list/grid toggle, show hidden toggle | default |
| S18 | Settings | drawer | Preferences | theme (light/dark/system), show hidden files, default view, confirm delete, recycle bin on/off, thumbnails, about | default |
| S19 | SD/USB access grant | tapping an SD/USB volume for the first time | SAF tree-permission handoff for removable storage | explainer, system folder picker | default, denied, granted |
| S20 | APK row actions | S04 APKs > tap | Show app info and install an APK | sheet: icon, label, version, size, installed/update status, Install button | not installed, installed, older version |

20 screens (several are dialogs or sheets).

## Flows

```
F01 First launch, grant access
    S02 -> system "All files access" toggle -> back -> S01
    happy path clicks: 3 (Grant, toggle, back)
    edge: user denies (stay on S02 with retry), access revoked later, Android <=10 (legacy READ/WRITE permission dialog instead)

F02 Open a file by category
    S01 -> S04 (e.g. Docs) -> tap file -> system "open with" / default app
    happy path clicks: 2
    edge: no app can open the type, empty category, the file was deleted outside the app (stale index)

F03 Copy several files into another folder
    S01 -> S03 -> long-press -> tap 2 more -> Copy -> S08 navigate -> Paste here -> S09 -> toast
    happy path clicks: ~7 plus navigation
    edge: name conflict, not enough space, target is the source or a child of it, SD card removed mid-copy, cancel halfway (clean up the partial file)

F04 Move files
    same as F03 with Move. Same volume = instant rename. Across volumes = copy then delete.
    edge: as F03, plus a delete failure after a successful copy

F05 Rename
    S03 -> long-press -> Rename -> S10 -> OK
    happy path clicks: 3 plus typing
    edge: name exists, illegal characters, extension change warning, batch rename (verify whether the original supports it)

F06 Delete and restore
    S03 -> long-press -> Delete -> S12 -> OK -> toast "Undo"; later S16 -> S15 -> Restore
    happy path clicks: 3 to delete, 3 to restore
    edge: original folder gone (recreate it), bin on another volume, permanent delete

F07 Search with filters
    S01 -> S06 -> type -> chip "Size > 100MB" -> tap result -> open, or long-press to act
    happy path clicks: 3 plus typing
    edge: no results, very large storage (stream results), search inside the current folder only vs everywhere

F08 Create a folder or text file
    S03 -> FAB -> New folder -> S10 -> OK
    happy path clicks: 3 plus typing
    edge: name exists, read-only location

F09 Share files
    long-press -> select -> Share -> Android share sheet
    happy path clicks: 3
    edge: sharing a folder (not allowed, or zip it later), many files, FileProvider URIs

F10 Favourite a folder and return to it
    S03 -> long-press -> More -> Favourite; later S01 favourites -> tap
    happy path clicks: 3, then 1
    edge: folder moved or deleted (mark missing)

F11 Hide and unhide a file
    long-press -> More -> Hide -> gone from lists; S14 -> Unhide
    edge: verify the method the original uses (dot-prefix rename vs an app-level list)

F12 Change sort, view or show-hidden
    S03 -> overflow -> S17 -> pick
    happy path clicks: 2
    edge: per-folder vs global preference (verify)

F13 Open an SD card or USB drive
    S16 -> SD card -> S19 (first time) -> system picker -> allow -> S03
    edge: no removable media, ejected while browsing, NTFS (read-only on many devices)

F14 Install an APK from Downloads
    S01 -> S04 (APKs) -> tap -> S20 -> Install -> system installer
    edge: "install unknown apps" permission not granted, corrupt APK, XAPK/APKS split bundles (out of scope)
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Top app bar | default, search, contextual (selection) | default, scrolled | all |
| Breadcrumb path bar | horizontal scrolling chips | default, overflow | S03, S08 |
| File row | list, grid tile | default, pressed, selected, cut-pending (dimmed), hidden (faded) | S03, S04, S05, S06, S13, S14, S15 |
| Thumbnail | image, video (with play badge), APK icon, type glyph | loading, loaded, fallback | file row |
| Storage usage card | internal, SD, USB | loading, filled, near-full warning | S01 |
| Category tile | 7 types, with count | default, loading count | S01 |
| Section header | date group, "see all" | default | S01, S05 |
| FAB / speed dial | new folder, new file | closed, open | S03 |
| Selection action bar | bottom actions plus overflow | enabled, disabled (e.g. rename with more than one selected) | S07 |
| Paste bar | Paste here / Cancel | enabled, disabled | S08 |
| Dialog | confirm, input, conflict, progress | default, error, busy | S09, S10, S12 |
| Bottom sheet | sort/view, properties, APK info | default | S11, S17, S20 |
| Filter chip | type, size, date | off, on | S06 |
| Search field | default | empty, typing, clearable | S06 |
| Empty state | empty folder, no results, empty bin, no favourites | default | S03-S06, S13-S15 |
| Snackbar/toast | info, undo | default | after operations |
| Progress notification | determinate | running, done, failed | S09 |
| Nav drawer | default | with/without removable volumes | S16 |
| Settings row | switch, choice, link | default, disabled | S18 |

## Inferred data model

The files themselves are the source of truth (Android filesystem plus MediaStore). The app only stores its own metadata.

```
StorageVolume  id, label (Internal | SD card | USB), root_path or saf_tree_uri,
               total_bytes, free_bytes, is_removable, is_mounted
               evidence: S01 usage card, S16 volumes, listing "SD cards and USB drives"
               confidence: high (derived at runtime from StorageManager, not persisted)

FileEntry      path/uri, name, is_dir, size, modified_at, mime_type, is_hidden
               evidence: every list screen, S11 properties
               confidence: high (runtime only, read from File/DocumentFile/MediaStore)

Favorite       id, path, label, is_dir, created_at
               evidence: listing "favorite or bookmark folders and files", S13
               confidence: high

RecentFile     path, mime_type, modified_at
               evidence: listing "recent files", S05
               confidence: medium (probably a MediaStore query on date_modified, not a stored table)

HiddenItem     id, original_path, current_path, hidden_at
               evidence: listing "hide files", S14
               confidence: guess (only needed if hiding renames or moves files. Not needed for dot-prefix hiding.)

TrashItem      id, original_path, trash_path, size, is_dir, deleted_at
               evidence: S15
               confidence: guess (the original's recycle bin is unconfirmed. Build it anyway as an improvement.)

SearchQuery    id, text, filters (json: type, min_size, max_size, date_range), searched_at
               evidence: S06 recent queries
               confidence: guess

FileOperation  id, type (copy | move | delete | trash | restore), sources (json),
               destination, status (queued | running | conflict | done | failed | cancelled),
               bytes_done, bytes_total, error, created_at
               evidence: S09 progress and notification
               confidence: medium (in-memory or WorkManager state, persisted only for resume)

Settings       theme, show_hidden, default_view (list | grid), sort_field, sort_dir,
               confirm_delete, use_trash, show_thumbnails, per_folder_view (json, maybe)
               evidence: S17, S18
               confidence: medium
```

Relationships: StorageVolume 1-n FileEntry (runtime). Favorite, HiddenItem, TrashItem and RecentFile each point at one FileEntry by path. FileOperation n-n FileEntry through sources. No user accounts and no server.

## Feature matrix

See `features.csv`. Must: 22, should: 12, could: 6, skip: 16.

## Out of scope (cannot or should not be cloned)

- The name "EX File Manager", its icon, screenshots, colours and copy (trademark and terms).
- Its install base, ratings and Play Store ranking.
- Ad and mediation partner deals (9 ad SDKs). Not wanted for a personal build anyway.
- Features outside the chosen slice: cloud drives, network protocols, PC access, archives, encryption, cleaner, duplicate finder, viewers, media players, code editor, XAPK installs, Chromecast. These are `skip` rows in features.csv and can be pulled in later.

## Size

Screens 20, flows 14, entities 9 (only 5 persisted).

Hard parts:
1. Android storage permissions. MANAGE_EXTERNAL_STORAGE plus the legacy path for Android 10 and older. Removable media goes through SAF tree URIs, which means two file-access code paths (java.io.File and DocumentFile). Publishing this permission on Play needs a policy declaration, though file managers are an allowed use.
2. Reliable copy/move. Long-running work in a foreground service or WorkManager, progress, cancel with cleanup, conflict resolution, cross-volume moves, then telling MediaStore about the changes so other apps see them.
3. Speed on big storage. Fast listing of folders with 10k+ entries, async folder sizes and counts, a thumbnail cache, and category/search queries over the whole drive without blocking the UI.

Size: **M**, a few weeks for one developer in Kotlin + Jetpack Compose. The core loop (S01-S04, S07-S10, S12) is a weekend-sized first slice.
