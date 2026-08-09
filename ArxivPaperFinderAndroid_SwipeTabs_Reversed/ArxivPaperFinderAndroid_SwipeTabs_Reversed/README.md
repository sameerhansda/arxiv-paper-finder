# arXiv Paper Finder Android — Custom Launcher Icon

The supplied icon has been added as the Android launcher icon.

Generated resources:
- mipmap-mdpi/ic_launcher.png
- mipmap-hdpi/ic_launcher.png
- mipmap-xhdpi/ic_launcher.png
- mipmap-xxhdpi/ic_launcher.png
- mipmap-xxxhdpi/ic_launcher.png
- matching ic_launcher_round.png files

AndroidManifest.xml now points to:
- @mipmap/ic_launcher
- @mipmap/ic_launcher_round

A 512×512 copy is also included at:
- res/drawable/app_icon.png
\n\n## Bookmark backup and transfer\n\nThe Bookmarks tab now contains a **Backup** menu with:\n\n- **Export bookmarks now** — creates a portable JSON backup through Android's system file picker.\n- **Import bookmarks** — imports a JSON backup and merges it with the bookmarks already on the device.\n- **Enable weekly backup** — choose a folder once; Android keeps persistent access and WorkManager updates `arxiv-bookmarks-latest.json` every 7 days.\n- **Change weekly backup folder** / **Disable weekly backup**.\n\nThe backup JSON contains the complete saved paper metadata (title, abstract, authors, affiliations, categories, publication date, arXiv ID, and URL). It can be copied to another Android device and imported there.\n

## Swipe tab navigation

The two main tabs can also be changed with horizontal gestures:

- On **Papers**, swipe **left** to open **Bookmarks**.
- On **Bookmarks**, swipe **right** to return to **Papers**.

The gesture uses Compose's horizontal-drag detector, so ordinary vertical
scrolling in the paper/bookmark lists remains available.
