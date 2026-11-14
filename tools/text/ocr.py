#!/usr/bin/env python3
"""OCR a page range of the scanned book into a cache folder.

usage: ocr.py book.pdf cache_dir FIRST LAST
Per page writes pNNNN.png (300 dpi grey), pNNNN.eng.tsv (English words) and
pNNNN.mix.tsv (Arabic+English, used only to find the Arabic lines).
Needs pdftoppm and tesseract with the eng and ara language data.
"""
import os, subprocess, sys
from concurrent.futures import ThreadPoolExecutor

pdf, cache, first = sys.argv[1], sys.argv[2], int(sys.argv[3])
if sys.argv[4] == "all":
    import re
    last = int(re.search(r"Pages:\s+(\d+)", subprocess.run(["pdfinfo", pdf], capture_output=True)
                         .stdout.decode()).group(1))
else:
    last = int(sys.argv[4])
os.makedirs(cache, exist_ok=True)

def one(p):
    base = os.path.join(cache, "p%04d" % p)
    if os.path.exists(base + ".eng.tsv") and os.path.exists(base + ".mix.tsv"):
        return p
    if not os.path.exists(base + ".png"):
        subprocess.run(["pdftoppm", "-f", str(p), "-l", str(p), "-r", "300", "-gray", "-png",
                        "-singlefile", pdf, base], check=True, stderr=subprocess.DEVNULL)
    for tag, lang in (("eng", "eng"), ("mix", "ara+eng")):
        if not os.path.exists("%s.%s.tsv" % (base, tag)):
            subprocess.run(["tesseract", base + ".png", "%s.%s" % (base, tag), "-l", lang,
                            "--psm", "3", "tsv"], check=True, stderr=subprocess.DEVNULL,
                           env=dict(os.environ, OMP_THREAD_LIMIT="1"))
    return p

with ThreadPoolExecutor(max_workers=os.cpu_count() or 2) as ex:
    for p in ex.map(one, range(first, last + 1)):
        if p % 25 == 0 or p == last:
            print("ocr", p, flush=True)
