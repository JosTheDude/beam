# vidsrc

Android TV app for [vidsrc.to](https://vidsrc.to). Made it because there's no app for my projector.

Search titles with the remote, pick an episode, and it plays. Keeps a continue watching list and blocks popups.

## Build

Needs JDK 17 and the Android SDK.

```
./gradlew assembleDebug
```

APK ends up in `app/build/outputs/apk/debug/`.

`./publish.sh` bumps the version, builds, and serves the APK on port 8000 so you can grab it with Downloader on the TV.

## Controls

- D-pad / OK to navigate
- Back to go back
- Menu to go home

## License

MIT
