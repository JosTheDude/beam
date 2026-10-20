#!/bin/sh
# Open the home screen in Chrome with the TMDB key from local.properties.
# (macOS `open` drops the ?query from file URLs, so call Chrome directly.)
cd "$(dirname "$0")"
KEY=$(sed -n 's/^tmdb.key=//p' local.properties)
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" "file://$PWD/app/src/main/assets/home.html?k=$KEY" >/dev/null 2>&1 &
