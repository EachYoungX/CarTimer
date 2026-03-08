# CarTimer - Automatic Vehicle Timer

<div align="center">

[🇨🇳 中文](README.md) | [🇺🇸 English](README_en.md)

</div>

**An automatic timing and driving record management tool designed for Android in-vehicle systems**

[Features](#features) | [Core Functions](#core-functions) | [Technical Architecture](#technical-architecture) | [Changelog](#changelog)

---

## Application Overview

CarTimer is an automatic timing application optimized for in-vehicle central control systems, providing driving records, statistical analysis, and data management capabilities. Built with Material Design 3, landscape-optimized, with support for dark mode, system theme following, and multiple theme color options, offering a safe, convenient, and elegant experience for drivers.

---

## Features

### Core Functions

#### 1. Automatic Timing Service

- Auto-starts on boot, no manual intervention required
- Real-time timer display in floating window
- Supports manual start/pause/resume control
- Continuous background operation, no interruption when screen is off
- Optional persistent notification bar display

#### 2. Driving Record Management

- Automatically records start time, end time, and duration for each trip
- View records by day/week/month/custom range
- Smart collapsible display (landscape mode shows first 3 records by default)
- Quick navigation (calendar jump)
- Bottom boundary indicator to prevent infinite scrolling
- Smooth expand/collapse animations

#### 3. Statistical Analysis

- **Overview**: Summary of all historical data
- **Monthly Trends**: Bar charts showing monthly driving patterns
- **Weekly Distribution**: Analysis of weekly driving habits
- **Custom Range**: Flexible time period selection for statistics
- Dynamic bottom layout that automatically adapts to content height
- Chart colors that adapt to theme

#### 4. Data & Privacy Management

- **Export**: Export all records to standard CSV format (UTF-8 with BOM, Excel-compatible)
- **Import**: Restore records from CSV files with automatic deduplication and transaction protection
- **Auto Cleanup**: Configurable automatic deletion of expired records (1/2/3 years options)
- **Manual Delete**: Supports deletion by year/month/week with secondary confirmation and 3-second countdown to prevent accidental touches

#### 5. Personalization Settings

- **4 Theme Colors**: Classic Blue, Vibrant Orange, Minimalist Gray, Deep Purple
- **System Following**: Supports following system dark mode
- **Behavior Settings**: Auto-start on boot, auto-timing, auto-minimize
- All settings saved in real-time, no need for repeated configuration

---

### In-Vehicle Optimization

#### Landscape Adaptation

- All pages forced to landscape orientation
- Automatic layout adjustment in landscape mode (e.g., Today page shows 3 records)
- Automatic adaptation during screen rotation (configuration change monitoring)
- **Portrait mode planned**: Future versions will support portrait usage scenarios

#### Touch Optimization

- Minimum touch height of 64dp to prevent accidental touches
- Large card design with 16dp spacing
- Large fonts (main title 18sp bold)
- Clearly identified buttons and clickable areas

#### Visual Optimization

- Material Design 3 design specifications
- Unified color system and rounded corner style
- Full dark mode support
- Status bar color adapts to theme
- Clear loading and empty state indicators

#### Performance Optimization

- Asynchronous data loading without blocking UI
- RecyclerView smart recycling
- No Activity reconstruction on configuration changes
- Database operations use transactions
- Background service priority optimization

---

## Technical Architecture

### Application Structure

```
com.EachYoungX.timer/
├── activities/          # Activity layer
│   ├── MainActivity
│   ├── LogDetailActivity
│   ├── SettingsActivity
│   ├── DataPrivacyActivity
│   └── ManualDeleteActivity
│
├── fragments/           # Fragment layer
│   ├── HomeFragment
│   ├── LogFragment
│   ├── StatisticsFragment
│   ├── MonthlyTrendFragment
│   ├── WeeklyDistributionFragment
│   ├── MonthlyDeleteFragment
│   └── WeeklyDeleteFragment
│
├── services/            # Service layer
│   ├── TimerService          # Timing service
│   └── FloatingWindowService # Floating window service
│
├── adapters/            # Adapter layer
│   ├── LogAdapter
│   └── ManualDeletePagerAdapter
│
├── models/              # Data models
│   └── LogEntry
│
├── database/            # Database layer
│   └── LogDatabaseHelper
│
├── ui/                  # UI components
│   ├── ThemeManager
│   └── MonthCalendarView
│
└── utils/               # Utilities
    ├── DateUtils
    └── ChartColorHelper
```

### Core Technology Stack

- **Development Language**: Java 8+
- **Minimum Version**: Android 7.0 (API 24)
- **Target Version**: Android 13 (API 33)
- **UI Framework**: Material Components
- **Data Storage**: SQLite + SharedPreferences
- **Architecture Pattern**: MVC + Service architecture

### Key Designs

#### 1. Foreground Service Architecture

- `TimerService` uses `startForeground()` to maintain foreground operation
- Declares `foregroundServiceType="specialUse"` to meet Android 10+ requirements
- Client counting mechanism (`activeClients`) manages service lifecycle
- Self-destruct mechanism (stops after 2 seconds without clients) to avoid resource waste

#### 2. Database Design

- Single table design (`logs` table)
- Index optimization (`start_time`, `date_key`, `month_key`)
- Transaction protection (import/delete operations)
- Deduplication logic (based on `start_time`)

#### 3. Theme System

- `ThemeManager` singleton manages global theme
- Supports 4 theme colors + system following
- Full dark mode compatibility
- Real-time switching without app restart

#### 4. Adaptive Layout

- Automatic landscape/portrait adaptation
- `configChanges` prevents Activity reconstruction
- `layout_weight` for dynamic space allocation
- Custom View supports configuration changes

---

## Key Highlights

### 1. Safety First

- Large fonts and buttons for easy operation while driving
- Landscape optimization for in-vehicle control screens
- Dark mode reduces nighttime glare
- Anti-accidental touch design (3-second countdown, large spacing)

### 2. Data Security

- Local storage, no network permissions required
- Standard CSV format for data portability
- Transaction protection prevents data corruption
- Automatic deduplication avoids duplicate records

### 3. Excellent Performance

- Auto-start on boot, no manual launch needed
- Continuous background operation, no interruption when locked
- Asynchronous processing for smooth UI
- Smart collapsing reduces memory usage

### 4. Great User Experience

- Material Design 3 specifications
- 4 theme color options
- System dark mode following
- Real-time settings saving

---

## Installation Instructions

### System Requirements

- Android 7.0 (API 24) and above
- Android 10+ recommended for full feature support
- Landscape display device (in-vehicle central control)

### Installation Steps

1. Go to the GitHub releases page
2. Download the latest APK file
3. Allow "Install unknown apps" permission
4. Click to install
5. Grant necessary permissions (notifications, floating window, auto-start)

### Permissions Explanation

- **Notification Permission**: For foreground service notification bar display
- **Floating Window Permission**: For floating window timer display
- **Auto-start Permission**: For automatic timer service launch on boot
- **Storage Permission**: For CSV import/export (optional)

---

## User Guide

### Quick Start

1. **First Launch**: All shortcut features are disabled by default and need to be enabled in settings
2. **View Records**: Click the "Logs" tab in the bottom navigation bar
3. **View Statistics**: Click the "Statistics" tab to view analysis charts
4. **Settings**: Click the "Settings" tab to customize behavior

### Common Operations

- **Start Timing**: Click the "Start" button on the home page (or auto-start)
- **Pause Timing**: Click the "Pause" button on the home page
- **Resume Timing**: Click the "Resume" button on the home page
- **Minimize**: Click the "Minimize" button on the home page to enter floating window mode
- **Expand Records**: Click the date header or "Expand More" button
- **Export Data**: Settings > Data & Privacy > Export Records

---

## Changelog

For detailed update records, please see [CHANGELOG.md](CHANGELOG.md)

---

## Roadmap

### Coming Soon

1. **Portrait Mode Support**
   - Support free switching between portrait and landscape modes
   - Optimized layout for portrait mode

2. **Multi-language Support**
   - Internationalization (i18n) framework
   - Initial support: Simplified Chinese, English
   - Automatic switching based on system language

---

## Technical Notes

### Foreground Service Type

This app uses `foregroundServiceType="specialUse"` for:
- User-visible timer display
- Floating window display

Complies with Android 10+ foreground service requirements.

### Data Storage

- **Record Data**: SQLite database (`logs` table)
- **Settings Data**: SharedPreferences
- **Export Files**: CSV format (UTF-8 with BOM)

### Compatibility Notes

- **Android 7.0-9.0**: Full basic feature support
- **Android 10+**: Full support including foreground service type declaration
- **Android 12+**: Adapts to new notification permission requirements
- **Android 13+**: Adapts to new notification permissions and language settings

### Internationalization

- **Current Language**: Simplified Chinese
- **Planned**: Multi-language support (i18n) will be implemented in future versions

---

## Developer Information

- **Package Name**: `com.EachYoungX.timer`
- **Application Name**: CarTimer
- **Version**: 2.0.0
- **Build Tools**: Gradle 8.0+

---

## License

This project is for learning and reference purposes only.

---

## Feedback & Support

Questions or suggestions are welcome.

---

<div align="center">

**CarTimer** - Automatic timing tool designed for in-vehicle systems

Made with care for Android In-Vehicle Systems

</div>
