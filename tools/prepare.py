#!/usr/bin/env python3
"""Turn the book PDF into app assets: book.pdf, toc.json, pages.txt."""
import json, os, re, shutil, subprocess, sys
import pypdf

pdf, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
reader = pypdf.PdfReader(pdf)
if reader.is_encrypted:
    reader.decrypt("")
n = len(reader.pages)

toc = []
def walk(items, level):
    for it in items:
        if isinstance(it, list):
            walk(it, level + 1)
            continue
        try:
            page = reader.get_destination_page_number(it) + 1
            title = re.sub(r"\s+", " ", str(it.title or "")).strip()
            if title and 1 <= page <= n:
                toc.append({"title": title, "page": page, "level": min(level, 4)})
        except Exception:
            pass
try:
    walk(reader.outline, 0)
except Exception as e:
    print("outline unreadable:", e)
override = os.path.join(os.path.dirname(os.path.abspath(__file__)), "toc.json")
if len(toc) < 3 and os.path.exists(override):  # hand-checked contents for this edition
    toc = [e for e in json.load(open(override, encoding="utf-8")) if 1 <= e["page"] <= n]
if len(toc) < 3:  # no usable bookmarks in the PDF: fall back to page ranges
    toc = [{"title": "Pages %d - %d" % (s, min(s + 24, n)), "page": s, "level": 0}
           for s in range(1, n + 1, 25)]

try:
    raw = subprocess.run(["pdftotext", "-enc", "UTF-8", pdf, "-"],
                         capture_output=True, check=True).stdout.decode("utf-8", "replace")
    pages = raw.split("\f")
except Exception as e:
    print("text extraction failed:", e)
    pages = []
pages = [re.sub(r"\s+", " ", p).strip() for p in pages[:n]]
pages += [""] * (n - len(pages))
clean = os.path.join(out, "text", "pagetext.json")   # corrected text, when the text build ran
if os.path.exists(clean):
    for k, v in json.load(open(clean, encoding="utf-8")).items():
        if 1 <= int(k) <= n and v.strip():
            pages[int(k) - 1] = re.sub(r"\s+", " ", v).strip()
    os.remove(clean)

shutil.copyfile(pdf, os.path.join(out, "book.pdf"))
with open(os.path.join(out, "toc.json"), "w", encoding="utf-8") as f:
    json.dump(toc, f, ensure_ascii=False)
with open(os.path.join(out, "pages.txt"), "w", encoding="utf-8") as f:
    f.write("\f".join(pages))
print("pages=%d toc=%d text_chars=%d" % (n, len(toc), sum(map(len, pages))))
