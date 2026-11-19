# Components: Filebox

Built in Jetpack Compose on Material 3 (an accessible base: semantics, ripple, focus and 48dp targets come built in). Every colour is a theme role (`MaterialTheme.colorScheme.*` or `FileboxTheme.extended.*`), every size a token from `Dimens.kt`. No raw hex or dp in screens.

**Source of values:** the original has not been measured. `replica/screens/` is empty, so the values are Material 3 defaults tuned for a dense file list. Once you add screenshots, compare row height, grid columns and category-tile layout against them, then update `tokens.json` (not the components).

**Assets:**
- **Icons:** Material Symbols Rounded (Apache 2.0), imported as vector drawables. Never use the original's icons.
- **File-type glyphs:** Material Symbols too (`image`, `movie`, `music_note`, `description`, `android`, `download`, `folder_zip`, `folder`, `draft`).
- **Illustrations** (S02 onboarding, empty states): your own, or none for v1. An icon in a tinted circle is enough.
- **Copy:** every label below is written fresh, so treat it as placeholder until `/replica-brand` sets the voice.

**Universal rules:**
- **Touch target:** at least 48x48dp, even when the visible icon is 24dp.
- **Focus:** keyboard and D-pad focus show the M3 focus indicator (2dp outline, `primary`). Never removed.
- **TalkBack:**
  - Icon-only buttons get a `contentDescription`.
  - Decorative icons get `null`.
  - Rows merge descendants, so a row reads as one item.
- **Font scale:** text must survive 200% font scale. Rows grow taller and never clip; meta lines may wrap to 2 lines.
- **Motion:** respect "Remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) by skipping non-essential transitions.

---

```
TopBar
  variants  default (title + nav icon + actions), search (field replaces title), contextual (selection)
  sizes     height top-bar 64dp; collapses to 64dp from a large variant on S01 only (could)
  states    default, scrolled (container tonal elevation bar 3dp), contextual (bg primaryContainer, onPrimaryContainer)
  tokens    bg surface/bg, title titleLarge, icons onSurfaceVariant 24dp
  a11y      nav icon "Open menu" / "Back"; title is a heading (semantics heading()); contextual title "3 selected" is a live region (polite)
  used on   all screens
```

```
BreadcrumbBar
  variants  path chips separated by chevrons; leading volume chip ("Internal storage", "SD card")
  sizes     height breadcrumb-bar 40dp, chip padding space.xs horizontal
  states    default, overflow (horizontally scrollable, auto-scrolls to the last chip), last chip = current (onSurface, weight 500), others onSurfaceVariant
  tokens    labelLarge, chevron icon-sm, bg bg
  a11y      each chip is a button "Go to Download"; current chip has stateDescription "current folder" and is not clickable; long-press on the bar copies the full path (toast "Path copied")
  used on   S03, S08
```

```
FileRow
  variants  list (row 56dp single line for folders with no meta / row-two-line 72dp with meta), grid tile (min grid-tile-min 104dp, square thumb + 2-line name)
  anatomy   leading Thumbnail 40dp | name (titleSmall, 1 line, middle-ellipsis to keep the extension) | meta (bodySmall onSurfaceVariant: "2.4 MB · 3 Nov" or "12 items") | trailing: overflow icon (list) or check (selection mode)
  states    default, pressed (ripple), focused, selected (bg primaryContainer, check icon primary, thumb gets a check overlay), cut-pending (alpha 0.5 during move picking), hidden (dot-prefixed: alpha 0.6), missing (favourites: name strikethrough + "Not found" meta in error)
  gestures  tap = open / enter folder; long-press = enter selection mode; tap in selection mode = toggle
  tokens    padding horizontal space.md, gap space.md, thumb radius sm
  a11y      merged node: "report.pdf, PDF, 2.4 megabytes, modified 3 November"; selected exposes toggleableState; custom actions (TalkBack actions menu): Select, Rename, Share, Delete, Properties, so long-press is not the only way
  used on   S03, S04, S05, S06, S08, S13, S14, S15
```

```
Thumbnail
  variants  image (Coil, centerCrop), video (frame + small play badge bottom-end), APK (app icon), type glyph (icon on category container colour), folder (folder glyph, accent tint)
  sizes     thumb 40dp list, fills tile in grid
  states    loading (glyph shown, no shimmer: lists scroll too fast for it), loaded (crossfade fast 100ms), error / unsupported (glyph)
  tokens    container FileboxTheme.extended.category[c].container, glyph category[c].icon, radius sm
  a11y      decorative (contentDescription null); the row carries the type
  used on   FileRow, S20
```

```
StorageCard
  variants  internal, SD card, USB (icon + label differ)
  anatomy   label "Internal storage" | headline "48.2 GB used" | "of 128 GB" bodyMedium muted | LinearProgress | "1,204 files · 86 apps" bodySmall muted | chevron
  states    loading (counts show "–", progress indeterminate), filled, near-full >= 90% (progress + headline in warning, meta "Running low on space"), unmounted (card removed)
  tokens    bg surfaceContainer, radius md, padding space.md, progress track outlineVariant, indicator primary
  a11y      one merged node: "Internal storage, 48 of 128 gigabytes used, 38 percent"; whole card is a button "Open internal storage"
  used on   S01, S16 (compact row variant)
```

```
CategoryTile
  variants  Images, Video, Audio, Docs, APKs, Downloads, Archives
  sizes     category-tile 88dp tall; 4 columns compact, 7 on medium+
  anatomy   48dp circle (category container) with 24dp icon (category icon colour) | label labelMedium | count labelSmall muted
  states    default, pressed, loading count ("–"), zero count (still tappable, shows empty state)
  tokens    FileboxTheme.extended.category[c], radius md for the press ripple bounds
  a11y      "Images, 3,412 files", role button
  used on   S01
```

```
SectionHeader
  variants  plain (date group on S05), with action ("Recent" + "See all" text button)
  states    default, sticky (S05 date headers stick under the top bar)
  tokens    titleMedium onSurface, action labelLarge primary, padding space.md / space.xs
  a11y      heading(); action button "See all recent files"
  used on   S01, S05
```

```
FabMenu
  variants  single (S03: "New") expanding to speed dial: New folder, New text file
  sizes     fab 56dp, mini items 40dp with labels
  states    closed, open (scrim at 32%, items fade+slide base 200ms), hidden on scroll down / shown on scroll up, hidden in selection mode, disabled in read-only volume (hidden instead)
  tokens    bg primaryContainer, icon onPrimaryContainer, radius lg, elevation fab
  a11y      "Create new", expanded state announced; items are buttons; Back closes the menu
  used on   S03
```

```
SelectionActionBar
  variants  bottom bar with 4 primary actions + More: Copy, Move, Share, Delete, More (Rename, Hide, Add to favourites, Add shortcut, Properties)
  sizes     bottom-actions 80dp, icon 24 + labelSmall label
  states    enabled; per-action disabled (Rename when >1 selected; Share when a folder is selected) with text-disabled colour; slides up base 200ms on entering selection
  tokens    bg surfaceContainer, icon/label onSurface, Delete icon error
  a11y      every action labelled; disabled actions stay focusable with stateDescription "unavailable: select one item" so the reason is discoverable
  used on   S07 (over S03, S04, S05, S06)
```

```
PasteBar
  variants  "Copy 3 items here" / "Move 3 items here" + Cancel
  states    enabled, disabled (destination is the source or inside it → helper text "Can't paste into the same folder"), busy (button shows progress briefly before S09 opens)
  tokens    bg surfaceContainer, primary filled button + text button, padding space.md
  a11y      the disabled reason is read with the button
  used on   S08
```

```
Dialog
  variants  confirm (title, body, 2 buttons), input (title + TextField), conflict, progress
  sizes     max width dialog-max 560dp, radius xl, padding space.xl
  states    default, error (input: supportingText in error), busy (confirm button shows a spinner, both buttons disabled)
  tokens    bg surfaceContainerLow, title titleLarge, body bodyMedium onSurfaceVariant, destructive confirm uses error colour
  a11y      focus moves into the dialog and returns to the trigger on close; Back dismisses (except progress, where Back = Hide); destructive button is never the default focus
  used on   S09, S10, S12

  Confirm delete (S12)
    title  "Delete 3 items?"
    body   "They'll go to the recycle bin for 30 days." / with bin off: "This can't be undone."
    option checkbox "Delete permanently" (shown only when the bin is on)
    buttons Cancel | Delete (error)

  Input (S10)
    TextField pre-filled; on open, select the name without its extension
    errors  "Name can't be empty" · "Can't contain / \ : * ? \" < > |" · "An item with this name already exists"
    warning (not error) on extension change: "Changing the extension may make the file unusable." Confirm with "Use .txt"
    IME action Done = submit

  Conflict (S09)
    title  "report.pdf already exists"
    body   side-by-side meta: existing vs incoming (size, date)
    options radio Skip / Replace / Keep both (renames to "report (1).pdf")
    checkbox "Do this for the remaining 4 conflicts"
    buttons Cancel operation | Apply

  Progress (S09)
    title "Copying 12 items"   body "photo_031.jpg · 14 of 120 · 2.1 GB of 5.0 GB · 38 MB/s"
    LinearProgress determinate (indeterminate while counting)
    buttons Hide (keeps running, notification continues) | Cancel (error text button, asks "Stop copying? Files already copied stay.")
    a11y   progress announced every 10% (not every tick), live region polite
```

```
BottomSheet
  variants  sort & view (S17), properties (S11), APK info (S20)
  states    partially expanded, expanded, dismissed by drag / scrim tap / Back
  tokens    bg surfaceContainerLow, top radius xl, drag handle outlineVariant 32x4dp
  a11y      drag handle has "Dismiss" action; content order: title, then controls

  Sort & view (S17)
    radio group Sort by: Name, Date modified, Size, Type
    segmented Ascending / Descending
    segmented View: List / Grid
    switch "Show hidden files"
    (could) checkbox "Only this folder"

  Properties (S11)
    rows: Name, Location (MonoPath, copy icon button "Copy path"), Size (computing: "Calculating… 1.2 GB so far"), Contains ("1,204 files, 33 folders"), Modified, Type
    multi-select: "3 items", total size, mixed types

  APK info (S20)
    app icon 48dp, label titleMedium, "Version 2.3.1 · 14.2 MB", status chip (Not installed / Installed / Update: newer than installed / Older than installed), primary button Install / Update; disabled + reason when install permission is missing → "Allow installs" button opens system settings
```

```
FilterChip
  variants  type (Images, Video, Audio, Docs, APKs, Archives), size (> 10 MB, > 100 MB, > 1 GB), date (Today, 7 days, 30 days, This year)
  states    off (outline outlineVariant), on (bg primaryContainer, check icon), disabled
  tokens    radius sm, labelLarge, height 32dp inside a 48dp touch target
  a11y      toggleable; selected state announced
  used on   S06
```

```
SearchField
  variants  in TopBar search variant
  states    empty (placeholder "Search files"), typing (debounced 250ms), clearable (X button "Clear search"), searching (thin progress under the bar)
  tokens    bodyLarge, bg surfaceContainer, radius pill
  a11y      autofocus on open with the keyboard shown; IME action Search; result count announced ("24 results")
  used on   S06
```

```
EmptyState
  variants  empty folder ("This folder is empty" + "New folder" button when writable), empty category ("No documents yet"), no results ("No files match" + "Clear filters" when filters are on), empty bin ("Recycle bin is empty"), no favourites ("Long-press any file or folder to add it here"), no hidden ("Nothing hidden"), inaccessible folder ("Android doesn't let apps open this folder")
  anatomy   64dp tinted circle with icon | titleMedium | bodyMedium muted | optional tonal button
  tokens    circle surfaceContainer, icon onSurfaceVariant, centred, max width 320dp
  a11y      title is a heading; the whole block is announced on arrival (live region polite, once)
  used on   S03, S04, S05, S06, S13, S14, S15
```

```
Snackbar
  variants  info ("Copied 3 items"), with action ("Moved to recycle bin" + Undo), error ("Couldn't rename: name exists")
  states    show 4s (with action: 6s, longer when TalkBack is on via accessibility timeout), swipe to dismiss
  tokens    bg inverseSurface, text inverseOnSurface, action inversePrimary, radius xs
  a11y      announced politely; action reachable before timeout (Compose respects the accessibility timeout)
  used on   after every operation
```

```
ProgressNotification
  variants  running (determinate, Cancel action), done ("Copied 120 items", tap opens destination), failed ("Copy stopped: not enough space", tap opens S09 details)
  tokens    system notification; small icon is a monochrome app glyph; channel "File operations" (importance low: no sound)
  a11y      system handles it; titles are complete sentences
  used on   S09
```

```
NavDrawer
  variants  modal (compact, medium), permanent (expanded >= 840dp)
  sections  Storage (Internal, SD card, USB with StorageCard compact rows) | Favourites | Recycle bin (count badge) | Hidden files | divider | Settings
  states    item default, active (bg secondaryContainer pill), SD/USB present or absent, ejected (item removed with a snackbar "SD card removed")
  tokens    width drawer 300dp, item height 56dp, radius pill, labelLarge
  a11y      drawer is a pane title "Navigation"; active item has selected state
  used on   S16
```

```
SettingsRow
  variants  switch (Show hidden files, Use recycle bin, Confirm before deleting, Show thumbnails), choice (Theme: System / Light / Dark; Default view; Bin retention: 7 / 30 / 90 days), link (About, Licences)
  states    default, disabled (with reason as supporting text)
  tokens    row-two-line 72dp, title bodyLarge, supporting bodyMedium muted
  a11y      whole row is the toggle target (not just the switch); choice rows open a dialog with radio buttons
  used on   S18
```

```
PermissionScreen (S02, S19)
  anatomy   illustration slot (icon in 96dp circle) | headline "Allow access to your files" | body explaining why, in 2 short sentences | primary button "Allow access" | text link "Why does a file manager need this?"
  states    default; returned denied ("Access is still off. Filebox can't show your files without it." + button again); legacy (Android 8-10: system dialog instead of settings handoff); S19 variant for SD/USB: "Choose the SD card's top folder, then tap Use this folder"
  a11y      primary button is the first focus after the headline
```

## Primitive build status

**Not built yet.** No Android SDK or JDK is installed on this machine, so the primitives can't be compiled or screenshotted. The theme (`compose/Color.kt`, `Type.kt`, `Dimens.kt`, `Theme.kt`) is written and ready to drop into `app/src/main/java/dev/yourname/filebox/ui/theme/`.

First task of `/replica-build`:
1. Create the Android project (Android Studio or the Gradle wrapper), copy the theme in.
2. Build every component above in `ui/components/` and a debug-only `DesignCatalogActivity`. It should show each component in every state, in light and dark, at font scale 1.0 and 2.0.
3. Screenshot the catalog into `replica/design/catalog/`. That screenshot set is the design-system check before any screen is built.
