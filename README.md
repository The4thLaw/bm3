# bm3
MP3 playlist and album art processor for cars and nomad devices (originally for BMW's iDrive system).

## Features
- Copy files from your local library to an USB stick, SD card or any local folder
- Integrate album covers from jpg and png files directly in the copied MP3 (does not affect the local library)
- Copy and verify the playlists on the target device so that they are recognised by the media player
- Handle exclusion playlists so that only parts of the library are copied (handy if you have more music than what fits on your device)
- Synchronization mode to incrementally update the target collection

## Compatibility
The output has been tested to work on:
- BMW iDrive 5.x (e.g. 1 series F20 2016)
- BMW iDrive 8.x (e.g. 3 series G21 2022)
- VLC for Android 3.5.x

This is pretty generic software. It could very well work on other cars and players as long as they support M3U playlists (2020 Kia's don't, for example). If it works for you, drop an issue to add documentation.

## System Requirements

Java 8 or later.

The software has been tested to work on:
- Windows (Java 11)
- MacOS (Java 8)
- Linux (Java 11)
- Synology
  - Requires a Java 8 package for DSM 6
  - Requires a community Java 11 package for DSM 7 (Java 8 seems to be bugged)