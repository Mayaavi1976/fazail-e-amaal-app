# Fazail-e-Amaal — offline Android reader

Offline reader for the English Fazail-e-Amaal PDF: contents, go-to-page, text search,
bookmarks, continue-reading, night mode, pinch/double-tap zoom, swipe to turn pages.
Plain Java, no libraries, Android 5.0+ (minSdk 21, targetSdk 34).

## Get the APK (GitHub builds it for you)

1. Create a new repository on github.com (private is fine).
2. On a PC, inside this folder:

       git init -b main
       git add -A
       git commit -m "Fazail-e-Amaal reader"
       git remote add origin https://github.com/<you>/<repo>.git
       git push -u origin main

3. Open the repo's **Actions** tab and wait for "Build APK" to finish (about 2 minutes).
4. Open **Releases** and download `FazailEAmaal.apk` to your phone.

The build downloads the book from the URL in `.github/workflows/build.yml`.
If that download fails, put the PDF in this folder as `book.pdf`, commit, and push again.

## Build locally instead

Needs the Android SDK (`ANDROID_HOME`), JDK 17+, Python 3 with `pypdf`, `pdftotext`, `zip`.
Put the PDF here as `book.pdf`, then run `./build.sh`.

## Notes

- `release.keystore` (password `fazail123`) signs every build so updates install over
  each other. Replace it with your own key before publishing to a store.
- Contents come from `tools/toc.json` (parts and the chapters that could be verified), since this PDF has no bookmarks of its own.
- Search needs a text layer in the PDF; a scanned PDF will read fine but not be searchable.
