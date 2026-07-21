# Changelog

All notable changes to JJ Launcher Classic Version are documented here. This fork is based on JJ Launcher `0.11`; this changelog covers only what changed in this fork on top of that base.

## [1.0.0] - 2026-07-21

First public release.

### Added
- Bundled "iPod Classic" theme:
  - Two-pane Main Menu with a slowly panning, full-bleed album cover on the right that extends up behind the status bar
  - Two-pane Music menu (Cover Flow, Playlists, Artists, Albums, Songs, Genres, Search) with the same cover panel
  - Redesigned Now Playing screen: angled cover with reflection, centered track info, thick square progress bar
  - Redesigned Artists/Albums lists: icon-free artist rows, large-cover two-line album rows (bold name + song count), "All Songs" shortcut per artist
  - New text search screen (title/artist/album)
  - System-wide bold Nimbus Sans typography, tightened list indents, consistent status bar color, gradient battery icon
- Last.fm scrobbling:
  - Local Rockbox-style `.scrobbler.log` (Audioscrobbler 1.1 format)
  - Live scrobbling via the Last.fm API with browser-based login (no on-device typing required)
- Music Quiz: a first version of the classic iPod Music Quiz mini-game built from your own library (10s clips, 5-answer rounds, lives, score), styled after the original's 2000s "Fruitiger Aero" look
- OGG Vorbis playback and library scanning, with automatic detection of Opus-encoded files mislabeled with an `.ogg` extension
- Main Menu shortcuts for Music Quiz, Podcasts, Audiobooks, Folders, Years, Recently Added, and My Favorites

### Fixed
- Duplicate songs appearing in album track lists after interrupted library scans
- Missing album art in Artist → Albums lists (now falls back to folder `cover.jpg`/`folder.jpg`)
- OGG files not appearing in the library at all
- Out-of-memory crash loop while scanning very large libraries on-device
- Album art forced to a true 1:1 crop regardless of source image aspect ratio

### Changed
- Last.fm API credentials are no longer hardcoded; they're read from a local, gitignored `local.properties` file (see README)
