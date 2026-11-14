# Fazail-e-Amaal — offline Android reader

Offline reader for the English Fazail-e-Amaal PDF: contents, go-to-page, text search,
bookmarks, continue-reading, night mode, pinch/double-tap zoom, swipe to turn pages.

The book opens as **text** in a book typeface (Gentium Book Plus), with adjustable size.
Arabic passages are shown exactly as printed, as clear pictures cut from the scan, so no
letter of Qur'an or hadith is ever re-typed. Every page can still be opened as the original
scan from the menu ("Show scanned page").
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

## How the text version is made

The PDF is a scan, so the build reads it again by machine (`tools/text/ocr.py`, Tesseract,
English + Arabic) and then `tools/text/build.py`:

- removes running heads and margin labels, and rebuilds paragraphs, quotations and headings;
- checks every doubtful word against the PDF's own hidden text layer and a dictionary;
- repairs common scanning mistakes (broken honorifics, look-alike letters, stray marks);
- cuts each Arabic passage out as an image; pages that are tables link to the scan.

This is automatic, so some mistakes remain. To correct one by hand, add a line to
`tools/text/fixes.tsv`: section id (for example `s0018`, the section's first PDF page),
a tab, the wrong text exactly as it appears, a tab, the right text. Names the checker
should accept go in `tools/text/words.json`. Push, and the next build applies them.
The first build takes about 15 minutes longer while it reads all pages; later builds reuse it.

To build the text version locally: `python3 tools/text/ocr.py book.pdf ocrcache 1 all`,
then `OCR_CACHE=ocrcache ./build.sh`. Needs `tesseract` with `eng` and `ara`, `pillow`
and `pyspellchecker`. Without `OCR_CACHE` the app is built with scanned pages only.

## Notes

- `release.keystore` (password `fazail123`) signs every build so updates install over
  each other. Replace it with your own key before publishing to a store.
- Contents come from `tools/toc.json` (parts and the chapters that could be verified), since this PDF has no bookmarks of its own.
- Search needs a text layer in the PDF; a scanned PDF will read fine but not be searchable.
