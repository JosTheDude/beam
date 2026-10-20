# Beam

Android TV app for watching movies and shows. Made it because there's no app for my projector.

Search with the remote, pick an episode, and it plays.

- Metadata from TMDB, plays from VidSrc (IMDb ids), VidLink or EmbedWave. Pick the source on the home or show page.
- Continue watching, watchlist, and a coming up row for new episodes of shows you follow
- Recommendations based on what you watch and save
- Unaired episodes show with their air date
- Popups and ad redirects are blocked

## Build

Needs JDK 17, the Android SDK, and a free [TMDB](https://www.themoviedb.org/settings/api) API key in `local.properties`:

```
tmdb.key=YOUR_KEY
```

```
./gradlew assembleDebug
```

APK ends up in `app/build/outputs/apk/debug/`.

`./preview.sh` opens the UI in Chrome (arrows = D-pad, Enter = OK, Esc = back).

`./publish.sh` bumps the version, builds, and serves the APK on port 8000 so you can grab it with Downloader on the TV.

## Controls

- D-pad / OK to navigate
- Hold OK on a title for watchlist, move and remove
- Back to go back
- Menu, or the logo, to go home

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB.

## License

MIT
