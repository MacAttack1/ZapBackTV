# Changelog

Each `## version` section becomes the release notes on GitHub and the "What's new" text in the app's update dialog.

## 2.3.3

- **Fixed channels showing "No information" in the TV guide**: on big guides, listings stopped partway through so later channels got nothing. Every channel now keeps its listings; if memory is tight the guide covers fewer days or skips descriptions instead
- Guide IDs now match regardless of upper/lower case (e.g. "ESPN.us" and "espn.us")
- For Xtream playlists, channels still missing from the guide file get their schedule straight from your provider while they're on screen
- **Watch history screen** (Settings › History & storage › Watch history, or "Edit" on Continue watching and Recently watched): see everything you've watched by day, filter by channels, movies, episodes or in progress, play anything again, and pick exactly what to remove: one at a time, or tick several with Select

## 2.3.2

- **Opens straight into your library**: the organized channel, movie and series list is saved, so the app no longer re-reads your whole playlist every time it starts
- Checks your provider for changes quietly in the background, at most every 12 hours (or when you press Refresh), and only rebuilds the list when something actually changed
- A short message tells you when a playlist was updated (for example "120 new movies"), or that it's already up to date after a manual refresh
- Changing the guide time shift no longer reloads the whole playlist

## 2.3.1

- Fixed a crash ("out of memory") on TV boxes while the TV guide loaded with large playlists
- The TV guide now only keeps listings for channels you can see (not hidden categories or other countries), so it loads faster and uses far less memory
- If a guide is still too big for the device, it's shortened to the next day instead of crashing (Settings › TV guide says so)

## 2.3.0

- **10 vibrant themes** that color the whole app, not just the buttons: ZapBack, Neon Arcade, Electric, Ocean Pop, Toxic, Lava, Sunset Pop, Grape Soda, Bubblegum, and Midnight (the old dark look). Bold flat colors instead of muted gradients, and switching applies instantly
- **Backgrounds**: pick a pattern drawn in your theme's colors (Glow, Starburst, Synthwave, Halftone, Waves, Bubbles, Confetti, Stripes, Solid), a photo from your device, or a picture from a link, with a color wash and optional blur
- **Reorganized Settings**: categories with their own pages (Appearance, Playlists, What you see, Playback, TV guide, Parental controls, Updates, History & storage, About). Side-by-side on TV and tablets, tap-through on phones
- Real on/off switches, and options shown side by side instead of cycling through them

## 2.2.1

- **Back from the player returns to where you were**: the guide, channel list or search, with the same spot and the channel you picked still selected
- Back on the TV remote now leaves the player in one press
- **Recent searches** on the Search screen; tap one to search again, hold to remove it
- Your last search and its results stay put while you watch something
- **Clear history**: "Clear" buttons on Continue watching and Recently watched, plus Settings › History & storage for watch and search history

## 2.2.0

- **New name: ZapBackTV**, with an early-2000s logo: a chunky CRT TV and a remote
- New default "Zap" color theme to match the logo (Dawn, Aurora and the others are still in Settings)
- Updates now come from the ZapBackTV GitHub repository

## 2.1.0

### New
- **Updates from GitHub**: the app checks for new versions, shows what changed, and installs with your OK (Settings › Updates)
- Setup now asks which categories you want, with your country's suggested first
- Adult categories are hidden automatically; a switch in Parental controls shows them

### Improved
- Country detection is more accurate for categories like "In Theaters", "Sci-Fi" and "It Collection"
- Kids and Documentary categories are recognized as genres
- Glass-style panels, glowing focus on TV, and a floating tab bar on phones

## 2.0.0

- Country-first browsing for live TV and the guide, with genre chips (Sports, News, Kids…)
- "Only show my country" for live TV, movies, series and search
- Step-by-step loading progress for channels, movies, series and the TV guide
- Faster search and fixes for crashes with very large or duplicate-heavy playlists
