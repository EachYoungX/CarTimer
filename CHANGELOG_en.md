# CHANGELOG

<div align="center">

[🇨🇳 中文](CHANGELOG.md) | [🇺🇸 English](CHANGELOG_en.md)

</div>

This project follows [Semantic Versioning 2.0.0](https://semver.org/) specifications.

---

## [2.0.0] - 2026-03-07

### Major Updates

#### New Features

##### 1. Data & Privacy Management Module

Added complete data management features including export, import, auto cleanup, and manual deletion.

**Feature Details**:

- CSV Export: Supports exporting all records to standard CSV format (UTF-8 with BOM, Excel-compatible), uses SAF framework to select save location, asynchronous export without blocking UI
- CSV Import: Supports standard CSV format files, automatically validates header fields, smart deduplication (based on start_time), database transaction protection, automatic rollback on import failure
- Auto Cleanup: Configurable automatic deletion of expired records (Never/1 Year/2 Years/3 Years), automatically checks on first launch each day
- Manual Delete: Secondary confirmation dialog, 3-second countdown to prevent accidental touches, red border warning

##### 2. Driving Logs Optimization

**Smart Collapsible Feature**:

- Portrait mode shows 5 records by default, landscape mode shows 3 records by default
- Excess records automatically collapsed with "Expand More" button
- Click Header or button to expand/collapse with visual feedback

**Quick Navigation**:

- Added calendar button on month page using MaterialDatePicker native component
- Date selection range limited to current month, automatically scrolls to target position after selection

**Bottom Boundary Optimization**:

- Added Footer ("Reached the bottom") to all lists
- Prevents over-scrolling and avoids being obscured by system gesture areas

##### 3. Statistics Page Optimization

**Dynamic Bottom Layout**:

- Monthly/Weekly statistics pages use layout_weight to automatically fill remaining space
- Bottom summary information automatically follows without manual scrolling

**Today Statistics Layout Refactor**:

- Changed from dual-column to single-column with three statistics side-by-side
- Three metrics: Today's duration, count, average duration
- Font size increased to 20sp for better clarity

#### Bug Fixes

##### 1. Client Connection Management Issue

**Problem**: Fragment switching caused TimerService self-destruction, timer reset to zero

**Solution**:

- Elevated client connection management to Activity level
- Added anti-repeated registration mechanism (isUpdating flag)
- Only unregister client when Activity is truly destroyed
- Fragment switching no longer affects service connection

**Impact**: All Fragment switching scenarios (Home→Statistics, Expand Logs, etc.)

##### 2. Crash from Rapid Clicks

**Problem**: Rapid clicking of expand/collapse buttons caused RecyclerView concurrent modification exceptions, triggering app crash and timer reset

**Solution**:

- Added click debounce (300ms interval)
- Added concurrent update lock (isUpdating flag)
- Used asynchronous updates (background thread rebuilds list, Handler updates UI)
- Improved exception handling to prevent crash propagation

**Impact**: LogAdapter, MonthlyDeleteFragment, WeeklyDeleteFragment

##### 3. Dark Mode Switch Crash

**Problem**: Switching "Follow System" dark mode caused InflateException crash due to missing colorOutline attribute in dark theme

**Solution**:

- Added colorOutline attribute to all 4 dark themes
- Defined corresponding dark mode outline color resources

**Impact**: All dark mode themes (Classic Blue, Vibrant Orange, Minimalist Gray, Deep Purple)

##### 4. Theme Detection False Trigger

**Problem**: Passive theme change detection during Fragment switching triggered Activity recreate, causing service disconnection

**Solution**:

- Removed passive theme check in onResume
- Theme changes only actively triggered by settings page

##### 5. ANR Issue

**Problem**: FloatingWindowService did not call startForeground() within 5 seconds after launch, causing ANR

**Solution**:

- Added notification channel creation
- Ensured startForeground() is called within 5 seconds

##### 6. Infinite Scrolling Issue

**Problem**: RecyclerView could scroll down infinitely without clear bottom boundary

**Solution**:

- Added Footer to all RecyclerViews
- Set overScrollMode="never"
- Added visual bottom boundary

#### Recent Fixes (Post 2.0.0 Updates)

##### 1. Monthly Statistics Calendar Feature

Fixed the built-in calendar in the monthly statistics page (Monthly Trends) where dates with records still showed as no-record status, unable to quickly navigate to specific dates via calendar.

##### 2. Log Detail Page Calendar Navigation

Fixed the issue where clicking the calendar icon in the top-right corner of the log detail page (LogDetailActivity) did not correctly scroll to the specified date.

##### 3. Dark Theme Crash Issue

Fixed the app crash after enabling dark mode by adding colorSurfaceVariant attribute to all 4 dark themes.

##### 4. Minimalist Gray Theme Color Consistency

Fixed the issue where the Minimalist Gray theme icon color changed after enabling dark mode, maintaining theme color consistency across light/dark modes.

##### 5. Data Privacy Page Layout Optimization

Fixed the "Clear Driving Records" card layout inconsistency in the Data & Privacy page, changed to horizontal layout for visual consistency with other cards.

##### 6. Settings Page Toast Optimization

Removed all redundant Toast prompts from the settings page (theme switching, follow system, dark mode, behavior settings), visual feedback is sufficient to indicate successful operation.

##### 7. Follow System and Dark Mode Mutual Exclusion

Added dark mode switch, implemented mutual exclusion logic between follow system and dark mode, allowing users to force always-on dark background.

#### Technical Optimizations

##### 1. App Size Optimization

- App size increased from 3.75MB to 4.98MB
- New features: dark mode switch, calendar quick navigation, layout optimizations
- Additional resource files: dark theme color resources, icon resources, etc.

##### 2. Foreground Service Compliance

- Added foregroundServiceType="specialUse" declaration for TimerService and FloatingWindowService
- Meets mandatory Android 10+ requirements

##### 3. Performance Optimization

- Asynchronous rebuildDisplayList to avoid blocking main thread
- RecyclerView smart recycling to reduce memory usage
- No Activity reconstruction on configuration changes for smoother rotation
- Database operations use transactions to improve batch operation efficiency

##### 4. Resource Management Optimization

- Fixed LayoutInflater to use parent.getContext() to ensure theme attributes take effect
- Unified Footer style using concrete color values instead of theme attributes
- Optimized color resources to distinguish light/dark modes
- Added complete colorSurfaceVariant attribute to all dark themes

##### 5. File Structure Optimization

- Organized Java files by functional modules (activities/, fragments/, services/, etc.)
- Categorized drawable and layout resources
- Updated all reference paths

##### 6. Theme System Enhancement (New)

- Added dark mode switch supporting forced always-on dark background
- Implemented mutual exclusion logic between follow system and dark mode
- Maintained Minimalist Gray theme color consistency across light/dark modes
- Optimized theme switching experience by removing redundant Toast prompts

##### 7. Layout Optimization (New)

- Changed "Clear Driving Records" to horizontal layout in Data Privacy page
- Maintained visual consistency with Export/Import cards
- Simplified layout hierarchy to improve rendering performance

#### UI/UX Improvements

##### 1. Font and Layout Adjustments

- Today information font size increased (12-14sp → 24sp)
- Statistics metrics font size increased (14sp → 20sp)
- Removed redundant labels to simplify layout
- Optimized visual hierarchy

##### 2. Anti-Accidental Touch Design

- Added 3-second countdown to delete buttons
- Button disabled during countdown
- Dynamic button text changes
- Increased minimum touch height

##### 3. Icon Optimization

- Redesigned log icon (more like document/log shape)
- Unified icon style

---

### Performance Impact

#### Memory Optimization

- Footer uses fixed colors to reduce theme attribute lookup overhead
- Asynchronous export/import to avoid main thread blocking
- Smart collapsible reduces initial render item count

#### Launch Speed

- No Activity reconstruction on configuration changes for smoother rotation
- Auto cleanup delayed execution to not affect launch speed

#### Scrolling Performance

- OVER_SCROLL_NEVER reduces over-scroll animation overhead
- RecyclerView smart recycling

---

### Known Issues

1. Manual Delete: Currently only supports clearing all records; deletion by year/month selection is under development
2. Import Function: Large file imports may be slow (>10,000 records)
3. Dark Mode: Some custom View dark mode adaptations are incomplete
4. Landscape Mode: Current version mainly optimized for landscape; portrait experience needs improvement (planned)
5. Internationalization: Currently only supports Simplified Chinese; multi-language support is planned

---

## [1.0.0] - 2026-02-XX (Initial Release)

### Basic Features

- Automatic timing service
- Floating window display
- Auto-start on boot
- Manual control (start/pause/resume)
- Local record storage
- Basic settings page
- Theme switching (4 colors)

---

## Version Notes

### Version Number Rules

- **Major Version**: Major feature updates or incompatible API changes
- **Minor Version**: New features, backward compatible
- **Patch Version**: Bug fixes and small optimizations

### Update Type Labels

- **New**: New features or major improvements
- **Fixed**: Bug fixes
- **Optimized**: Performance optimizations or refactoring
- **UI/UX**: Interface or user experience improvements
- **Docs**: Documentation updates
- **Known Issues**: Known but unfixed issues

---

<div align="center">

**CarTimer** - Automatic timing tool designed for in-vehicle systems

</div>
