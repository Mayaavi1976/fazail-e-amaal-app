#!/usr/bin/env python3
"""Turn OCR'd pages of the scanned book into clean reading text for the app.

usage: build.py book.pdf cache_dir out_dir [--review review.txt]

Sections come from ../toc.json. For each one this writes out_dir/<id>.html,
cuts every Arabic passage out of the scan as a transparent PNG in out_dir/ar/,
and writes out_dir/index.json and out_dir/pagetext.json (clean text per page,
used by search).

English is taken from the fresh OCR (ocr.py), cross-checked word by word
against the PDF's own hidden text layer, then cleaned by the rules below and
by fixes.tsv (optional hand corrections: section-id <tab> old <tab> new).
Arabic is never typed: it stays a picture, so no letter of it can be wrong.
"""
import csv, difflib, html, json, os, re, subprocess, sys, unicodedata
from collections import Counter
from PIL import Image
from spellchecker import SpellChecker

HERE = os.path.dirname(os.path.abspath(__file__))
pdf, cache, out = sys.argv[1], sys.argv[2], sys.argv[3]
review_path = sys.argv[sys.argv.index("--review") + 1] if "--review" in sys.argv else None
os.makedirs(os.path.join(out, "ar"), exist_ok=True)

MARK = "␂%d␃"           # page-break marker carried inside block text
MARK_RE = re.compile("␂(\\d+)␃")
AR = re.compile(r"[؀-ۿﭐ-﷿ﹰ-﻿]")
LAT = re.compile(r"[A-Za-z]")
WORD = re.compile(r"[A-Za-z][A-Za-z']*[A-Za-z]|[A-Za-z]")

HONORIFICS = ["Sallallaho alaihe wasallam", "Radhiyallaho anho", "Radhiyallaho anha",
              "Radhiyallaho anhum", "Radhiyallaho anhuma", "Rahmatullah alaih",
              "Alayhis salaam", "Alayhis salam", "Alaihis salaam", "Alaihis salam"]

# ---------------------------------------------------------------- vocabulary
spell = SpellChecker()
EXTRA = set(json.load(open(os.path.join(HERE, "words.json"), encoding="utf-8")))
CASEFREQ = Counter()

def book_words():
    """Words frequent in the book's own text layer (names, Arabic terms)."""
    path = os.path.join(cache, "layer.txt")
    if not os.path.exists(path):
        subprocess.run(["pdftotext", "-enc", "UTF-8", pdf, path], check=True,
                       stderr=subprocess.DEVNULL)
    words = WORD.findall(open(path, encoding="utf-8").read())
    CASEFREQ.update(words)
    freq = Counter(w.lower() for w in words)
    groups = {}
    for w, n in freq.items():
        if n >= 15 and len(w) >= 4 and w not in spell:
            groups.setdefault(re.sub("[il1]", "l", w), []).append((n, w))
    keep = set()
    for forms in groups.values():
        top = max(forms)[0]
        keep.update(w for n, w in forms if n >= 0.3 * top)
    return keep

BOOK = book_words()
for h in HONORIFICS:
    BOOK.update(h.lower().split())

def known(w):
    w = w.lower().strip("'")
    return (not w) or w in spell or w in BOOK or w in EXTRA or w.rstrip("s'") in BOOK \
        or (w.endswith("'s") and (w[:-2] in spell or w[:-2] in BOOK or w[:-2] in EXTRA))

CONFUSE = [("i", "l"), ("l", "i"), ("t", "l"), ("l", "t"), ("1", "l"), ("I", "l"), ("c", "e"),
           ("e", "c"), ("o", "a"), ("a", "o"), ("u", "n"), ("n", "u"), ("h", "b"), ("b", "h"),
           ("rn", "m"), ("m", "rn"), ("cl", "d"), ("vv", "w"), ("0", "o"), ("5", "s"),
           ("J", "l"), ("]", "l"), ("g", "q"), ("gq", "q"), ("e", "a"), ("f", "t"), ("t", "f"),
           ("g", "o"), ("v", "y"), ("y", "v")]

def unconfuse(w):
    """A single look-alike substitution that turns a non-word into a known word."""
    found = set()
    for a, b in CONFUSE:
        i = w.find(a)
        while i >= 0:
            c = w[:i] + b + w[i + len(a):]
            if c.isalpha() and known(c):
                found.add(c.lower())
            i = w.find(a, i + 1)
    return found.pop() if len(found) == 1 else None

# ---------------------------------------------------------------- OCR input
def page_image(p):
    base = os.path.join(cache, "p%04d" % p)
    if not os.path.exists(base + ".png"):
        subprocess.run(["pdftoppm", "-f", str(p), "-l", str(p), "-r", "300", "-gray", "-png",
                        "-singlefile", pdf, base], check=True, stderr=subprocess.DEVNULL)
    return Image.open(base + ".png").convert("L")

def read_lines(p, tag):
    rows = csv.reader(open(os.path.join(cache, "p%04d.%s.tsv" % (p, tag)), encoding="utf-8"),
                      delimiter="\t", quoting=csv.QUOTE_NONE)
    lines = {}
    for r in rows:
        if r[0] == "5" and len(r) > 11 and r[11].strip():
            lines.setdefault((int(r[2]), int(r[3]), int(r[4])), []).append(
                dict(t=re.sub("[‘’`´]", "'", re.sub("[“”„]", '"', r[11])), x=int(r[6]),
                     y=int(r[7]), x2=int(r[6]) + int(r[8]), y2=int(r[7]) + int(r[9]),
                     c=float(r[10])))
    return [box(ws) for ws in lines.values()]

def box(ws):
    return dict(w=ws, x=min(a["x"] for a in ws), y=min(a["y"] for a in ws),
                x2=max(a["x2"] for a in ws), y2=max(a["y2"] for a in ws),
                c=sum(a["c"] for a in ws) / len(ws))

OKPUNCT = set(".,;:!?-—()\"'")

def clean_words(ws):
    keep = []
    for i, a in enumerate(ws):
        t = a["t"]
        if not re.search(r"[A-Za-z0-9]", t) and not AR.search(t):
            edge = i == 0 or i == len(ws) - 1
            if not set(t) <= OKPUNCT or a["c"] < (88 if edge else 60):
                continue
        keep.append(a)
    return keep

def layer_tokens(p):
    txt = subprocess.run(["pdftotext", "-enc", "UTF-8", "-f", str(p), "-l", str(p), pdf, "-"],
                         capture_output=True).stdout.decode("utf-8", "replace")
    return txt.split()

def core(t):
    m = re.search(r"[A-Za-z](?:[A-Za-z']*[A-Za-z])?", t)
    return m.group(0) if m else ""

def weight(w):
    return CASEFREQ.get(w, 0) + (0.5 if w.lower() in spell else 0)

def vote(lines, p, notes):
    """Use the PDF's own text layer as a second opinion on doubtful words."""
    words = [a for ln in lines for a in ln["w"]]
    lay = layer_tokens(p)
    sm = difflib.SequenceMatcher(None, [core(a["t"]).lower() for a in words],
                                 [core(t).lower() for t in lay], autojunk=False)
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op != "replace" or i2 - i1 != j2 - j1:
            continue
        for a, e in zip(words[i1:i2], lay[j1:j2]):
            ct, ce = core(a["t"]), core(e)
            if not ct or not ce or ct.lower() == ce.lower() or not known(ce):
                continue
            if sum(c.isalpha() for c in a["t"]) != sum(c.isalpha() for c in ct) or a["t"].endswith("-"):
                continue                       # more than one word, or a hyphenated line end
            take = False
            if not known(ct):
                take = difflib.SequenceMatcher(None, ct, ce).ratio() > 0.5
            elif len(ct) > 1 or len(ce) > 1:
                wt, we = weight(ct), weight(ce)
                if ct.lower() in ce.lower() and len(ce) - len(ct) >= 2 and a["c"] < 93:
                    take = True                # OCR lost part of the word
                elif we >= 8 * wt + 4:
                    take = True
                elif not wt >= 8 * we + 4:
                    notes.append("p%d two readings: %s / %s" % (p, ct, ce))
            if take:
                a["t"] = a["t"].replace(ct, ce, 1)

# ---------------------------------------------------------------- page layout
def robust_edge(vals, lo):
    vals = sorted(vals, reverse=not lo)
    for v in vals:
        if sum(1 for u in vals if abs(u - v) <= 12) >= max(2, len(vals) // 7):
            return v
    return vals[0]

def column(longs, H):
    """Left/right text margins as a function of y (scans are slightly skewed)."""
    gl = robust_edge([l["x"] for l in longs], True)
    gr = robust_edge([l["x2"] for l in longs], False)
    pts = []
    for k in range(3):
        band = [l for l in longs if k * H / 3 <= (l["y"] + l["y2"]) / 2 < (k + 1) * H / 3]
        if len(band) >= 4:
            bl = robust_edge([l["x"] for l in band], True)
            br = robust_edge([l["x2"] for l in band], False)
            pts.append((sum(l["y"] for l in band) / len(band),
                        bl if abs(bl - gl) <= 30 else gl, br if abs(br - gr) <= 30 else gr))
    def at(y):
        if len(pts) < 2:
            return gl, gr
        for (y0, l0, r0), (y1, l1, r1) in zip(pts, pts[1:]):
            if y <= y1 or (y1, l1, r1) == pts[-1]:
                f = min(1.3, max(-0.3, (y - y0) / max(1.0, y1 - y0)))
                return l0 + f * (l1 - l0), r0 + f * (r1 - r0)
    return at, gl, gr

def analyse(p, notes):
    img = page_image(p)
    W, H = img.size
    eng, mix = read_lines(p, "eng"), read_lines(p, "mix")
    tab = lambda l: (l["y2"] - l["y"]) > 1.5 * (l["x2"] - l["x"]) and (l["y2"] - l["y"]) > 60
    tabs = [l for l in eng + mix if tab(l)]
    eng = [l for l in eng if not tab(l)]
    mix = [l for l in mix if not tab(l)]
    for l in eng:
        ws = clean_words(l["w"])
        l.update(box(ws) if ws else dict(w=[]))
    head_y = 0.115 * H
    eng = [l for l in eng if l["w"] and l["y2"] >= head_y]
    mix = [l for l in mix if l["y2"] >= head_y]

    def is_ar(l):
        s = " ".join(a["t"] for a in l["w"])
        na, nl = len(AR.findall(s)), len(LAT.findall(s))
        return na >= 2 and na >= 0.35 * (na + nl)
    seeds = [l for l in mix if is_ar(l)]

    def overlaps(l):
        h = max(1, l["y2"] - l["y"])
        return any(min(l["y2"], s["y2"]) - max(l["y"], s["y"]) >= 0.5 * h
                   and min(l["x2"], s["x2"]) > max(l["x"], s["x"]) for s in seeds)
    text, pics = [], list(seeds)
    for l in eng:
        if overlaps(l):
            continue
        letters = sum(len(LAT.findall(a["t"])) for a in l["w"])
        digits = sum(len(re.findall(r"\d", a["t"])) for a in l["w"])
        if (l["c"] < 50 and letters < 40) or letters + digits == 0:
            pics.append(l)
        else:
            text.append(l)
    text.sort(key=lambda l: l["y2"])
    if not text and not pics:
        return dict(p=p, items=[], toc=False)
    vote(text, p, notes)
    leaders = sum(1 for l in text if re.search(r"\.{3,}|(\. ){3,}|\s\d{1,3}$", " ".join(a["t"] for a in l["w"])))
    if len(text) >= 8 and leaders >= 0.4 * len(text):
        return dict(p=p, items=[], toc=True)            # a printed table of contents

    longs = [l for l in text if len(l["w"]) >= 6 and l["c"] >= 70] or text or pics
    col, gl, gr = column(longs, H)
    prev = None
    for l in text:
        l["L"], l["R"] = col((l["y"] + l["y2"]) / 2)
        l["gap"] = 99 if prev is None else l["y"] - prev["y2"]
        prev = l

    pics.sort(key=lambda l: (l["y"] + l["y2"]) / 2)
    groups = []
    for s in pics:
        mid = (s["y"] + s["y2"]) / 2
        key = sum(1 for l in text if (l["y"] + l["y2"]) / 2 < mid)
        if groups and groups[-1]["key"] == key:
            g = groups[-1]
            g["y"], g["y2"] = min(g["y"], s["y"]), max(g["y2"], s["y2"])
        else:
            groups.append(dict(key=key, y=s["y"], y2=s["y2"]))
    ink = img.point(lambda v: 255 if v < 140 else 0)
    tabx = min([t["x"] for t in tabs if t["x"] > W / 2] + [W]) - 8
    tabl = max([t["x2"] for t in tabs if t["x"] < W / 2] + [0]) + 8
    items = [("t", l) for l in text]
    n = 0
    for g in groups:
        top = max([l["y2"] for l in text[:g["key"]]][-1:] + [int(head_y)]) + 2
        bot = min([l["y"] for l in text[g["key"]:]][:1] + [H - 40]) - 2
        top, bot = min(top, g["y"]), max(bot, g["y2"])
        x0, x1 = int(max(tabl, gl - 60, 0)), int(min(tabx, gr + 60, W))
        if x1 - x0 < 60 or bot - top < 14:
            continue
        bb = ink.crop((x0, top, x1, bot)).getbbox()
        if not bb or bb[2] - bb[0] < 40 or bb[3] - bb[1] < 14:
            continue
        n += 1
        cx0, cy0 = max(0, x0 + bb[0] - 6), max(0, top + bb[1] - 6)
        cx1, cy1 = min(W, x0 + bb[2] + 6), min(H, top + bb[3] + 6)
        name = "p%04d_%d.png" % (p, n)
        save_arabic(img.crop((cx0, cy0, cx1, cy1)), os.path.join(out, "ar", name))
        width = max(8, min(100, round(100.0 * (cx1 - cx0) / max(1, gr - gl))))
        items.append(("a", dict(y2=(cy0 + cy1) / 2, name=name, width=width)))
    items.sort(key=lambda it: it[1]["y2"])
    return dict(p=p, items=items, toc=False)

def save_arabic(crop, path):
    """Black ink on transparent, 4 levels, so night mode can simply invert it."""
    q = crop.point(lambda v: 0 if v < 70 else 1 if v < 130 else 2 if v < 190 else 3).convert("P")
    q.putpalette([0, 0, 0] * 4 + [0] * (252 * 3))
    q.save(path, optimize=True, bits=2, transparency=bytes([255, 170, 80, 0]))

# ---------------------------------------------------------------- blocks
def line_text(l):
    return " ".join(a["t"] for a in l["w"])

def join(prev, mark, nxt):
    """Append a line to block text, undoing end-of-line hyphenation."""
    m = re.search(r"([A-Za-z']+)-$", prev)
    n = re.match(r"[A-Za-z']+", nxt)
    if m and n:
        a, b = m.group(1), n.group(0)
        glue = "" if known(a + b) or not (known(a) and known(b)) or len(a) < 3 else "-"
        return prev[:m.start()] + a + glue + nxt[:n.end()] + mark + nxt[n.end():]
    if prev.endswith("—"):
        return prev + mark + nxt
    return prev + " " + mark + nxt

QUOTE_END = re.compile(r"[\"']\s*$")

def blocks_of(pages):
    blocks, cur = [], None
    def close():
        nonlocal cur
        if cur:
            if cur["kind"] == "?":
                cur["kind"] = "q" if MARK_RE.sub("", cur["text"]).lstrip()[:1] in "\"'" else "p"
            blocks.append(cur)
        cur = None
    for pg in pages:
        mark = MARK % pg["p"]
        if pg["toc"] or not pg["items"]:
            close()
            blocks.append(dict(kind="scan" if pg["toc"] else "a", text=mark, name=None, width=0))
            continue
        first = True
        for kind, l in pg["items"]:
            m = mark if first else ""
            first = False
            if kind == "a":
                close()
                blocks.append(dict(kind="a", text=m, name=l["name"], width=l["width"]))
                continue
            t = line_text(l)
            lg, rg = l["x"] - l["L"], l["R"] - l["x2"]
            caps = sum(c.isupper() for c in t) > 0.7 * max(1, sum(c.isalpha() for c in t))
            centred = abs(lg - rg) < 90 and (lg > 100 or (caps and lg > 30)) and len(t) < 60
            if cur and cur["kind"] == "h3n" and l["gap"] <= 8 and cur["n"] < 3 \
                    and not cur["text"].rstrip().endswith(":"):
                cur["text"] = join(cur["text"], m, t)         # second line of a numbered title
                cur["n"] += 1
            elif re.match(r"\d{1,2}\. ?[A-Z(]", t) and l["gap"] >= 22 and len(t) < 70:
                close()
                cur = dict(kind="h3n", text=m + t, n=1)
            elif centred:
                close()
                blocks.append(dict(kind="h2" if caps else "h3", text=m + t))
            elif lg >= 22:                                   # indented line
                if cur and (cur["kind"] == "q" and not QUOTE_END.search(cur["text"])
                            or cur["kind"] == "?" and cur["full"]):
                    cur["kind"] = "q"
                    cur["text"] = join(cur["text"], m, t)
                else:
                    close()
                    cur = dict(kind="?", text=m + t, full=rg < 30)
            else:                                            # flush-left line
                if cur and cur["kind"] == "?" and not cur["full"] \
                        and MARK_RE.sub("", cur["text"])[:1] in "\"'" and QUOTE_END.search(cur["text"]):
                    close()                                  # a one-line quotation
                if cur and cur["kind"] in ("?", "p"):
                    cur["kind"] = "p"
                    cur["text"] = join(cur["text"], m, t)
                else:
                    close()
                    cur = dict(kind="p", text=m + t, full=True, flush=True)
    close()
    for b in blocks:
        if b["kind"] == "h3n":
            b["kind"] = "h3"
    return blocks

# ---------------------------------------------------------------- text rules
def canon_honorific(s):
    key = re.sub(r"[^a-z]", "", s.lower())
    best, score = None, 0.0
    for h in HONORIFICS:
        r = difflib.SequenceMatcher(None, key, h.replace(" ", "").lower()).ratio()
        if r > score:
            best, score = h, r
    return best if score >= 0.8 else None

STOPS = ("the a an of to in and or for with by on at from that which who is was are were be "
         "as his her their its our my your this these those he she it they we you not").split()
STOP = set(STOPS)
ABBR = re.compile(r"(?:\b[A-Za-z]\.|\b(?:etc|viz|vol|ch|no|cf|pp|ibid|e\.g|i\.e))$", re.I)

def single_quotes(t):
    """Drop stray single quote marks (scanner specks) that have no partner."""
    toks = t.split(" ")
    opened = False
    for i, tok in enumerate(toks):
        if tok.startswith("'") and len(tok) > 1 and tok[1].isalnum():
            ahead = toks[i:i + 14]
            if any(re.search(r"\w[.,!?;:]*'[.,!?;:)]*$", a) for a in ahead):
                opened = True
            else:
                tok = tok[1:]
        m = re.search(r"(\w[.,!?;:]*)'([.,!?;:)]*)$", tok)
        if m:
            if opened:
                opened = False
            elif not re.search(r"s'[.,!?;:)]*$", tok):
                tok = tok[:m.start()] + m.group(1) + m.group(2)
        toks[i] = tok
    return " ".join(toks)

def tidy(t, notes, where):
    t = "".join(c for c in unicodedata.normalize("NFD", t) if unicodedata.category(c) != "Mn")
    t = t.replace("{", "(").replace("}", ")")
    t = re.sub(r"[|~°^*_\\<>=§¢£¥©®»«·•]", "", t)
    t = re.sub(r"[‘’`´]", "'", t)
    t = re.sub(r"[“”„]|''", '"', t)
    t = re.sub(r'"{2,}', '"', t)
    t = re.sub(r"\s+", " ", t)
    def paren(m):
        h = canon_honorific(m.group(1))
        return "(" + h + ")" if h else m.group(0)
    t = re.sub(r"\(([^()␂␃]{8,34})\)", paren, t)
    toks = t.split(" ")                    # honorific word runs that lost a bracket
    i = 0
    while i < len(toks):
        for n in (3, 2):
            run = " ".join(toks[i:i + n])
            body = run.strip("().,;:!?\"'")
            if len(toks[i:i + n]) == n and "␂" not in run and len(body) > 12:
                h = canon_honorific(body)
                if h and len(h.split()) == n and h != body:
                    lead = run[:len(run) - len(run.lstrip("(\"'"))]
                    tail = run[len(run.rstrip(").,;:!?\"'")):]
                    toks[i:i + n] = (lead + h + tail).split(" ")
                    break
        i += 1
    t = " ".join(toks)
    t = re.sub(r"\bQO\b", "O", t)
    t = re.sub(r'(^|[\s"])Q,', r"\1O,", t)
    t = re.sub(r"(?<![\w.:])(?:1|l|\]|J|T)(?= [a-z])", "I", t)       # stand-alone I
    t = re.sub(r"\b(\w*ll)[\]!](?= [a-z])", r"\1", t)                 # will] shall!
    t = re.sub(r"([a-z]{2,})\.([A-Za-z]{2,})", r"\1 \2", t)           # were.in
    t = re.sub(r"([a-z]{3,})-(of|him|to|the|in|and|it|is|he|his|a)\b(?!-)", r"\1 \2", t)
    t = re.sub(r"\b(%s): (?=[a-z])" % "|".join(STOPS), r"\1 ", t)
    def word(m):
        w = m.group(0)
        if known(w) or len(w) < 3:
            return w
        c = unconfuse(w)
        if c:
            return c[0].upper() + c[1:] if w[0].isupper() else c
        notes.append("%s unknown word: %s" % (where, w))
        return w
    t = re.sub(r"[A-Za-z\]][A-Za-z\]01']*[A-Za-z\]]", word, t)
    def stop(m):                           # a full stop in mid-sentence: speck or faint comma
        before = m.group(1)
        if ABBR.search(before) or before.isdigit():
            return m.group(0)
        return before + (" " if before.lower() in STOP else ", ") + m.group(2)
    t = re.sub(r"(\S+)\. ([a-z])", stop, t)
    t = single_quotes(t)
    t = re.sub(r" ([.,;:!?)])", r"\1", t)
    t = re.sub(r"\( ", "(", t)
    t = re.sub(r"([,;:])\1+", r"\1", t)
    t = re.sub(r"(?<!\.)\.\.(?!\.)", ".", t)
    t = re.sub(r",\.|\.,|:\.", lambda m: ":" if ":" in m.group(0) else ".", t)
    return t.strip()

def curl(t):
    t = re.sub(r'(^|[\s(␃—-])"', r"\1“", t).replace('"', "”")
    t = re.sub(r"(^|[\s(␃“—-])'", r"\1‘", t).replace("'", "’")
    return t

# ---------------------------------------------------------------- sections
def sections():
    toc = json.load(open(os.path.join(HERE, "..", "toc.json"), encoding="utf-8"))
    n = int(re.search(r"Pages:\s+(\d+)", subprocess.run(
        ["pdfinfo", pdf], capture_output=True).stdout.decode()).group(1))
    out_, book = [], ""
    for i, e in enumerate(toc):
        last = (toc[i + 1]["page"] - 1) if i + 1 < len(toc) else n
        if e["level"] == 0:
            book = e["title"]
        skip = e["title"].startswith(("Contents", "Title page")) or \
            (e["level"] == 0 and last - e["page"] < 3)
        if not skip and last >= e["page"]:
            out_.append(dict(id="s%04d" % e["page"], book=book, title=e["title"],
                             first=e["page"], last=last))
    return out_

fixes = {}
fpath = os.path.join(HERE, "fixes.tsv")
if os.path.exists(fpath):
    for n, row in enumerate(open(fpath, encoding="utf-8").read().split("\n"), 1):
        if row.strip() and not row.startswith("#"):
            sid, old, new = row.split("\t")
            fixes.setdefault(sid, []).append((old, new, n))

index, pagetext, review = [], {}, []
for sec in sections():
    notes, pages = [], []
    for p in range(sec["first"], sec["last"] + 1):
        try:
            pages.append(analyse(p, notes))
        except Exception as e:                   # never let one odd page stop the build
            print("page", p, "failed:", repr(e))
            pages.append(dict(p=p, items=[], toc=True))
    blocks = blocks_of(pages)
    for b in blocks:
        if b["kind"] not in ("a", "scan"):
            b["text"] = tidy(b["text"], notes, sec["id"])
    for old, new, n in fixes.get(sec["id"], []):
        hit = [b for b in blocks if b["kind"] not in ("a", "scan") and old in b["text"]]
        if len(hit) != 1 or hit[0]["text"].count(old) != 1:
            print("fixes.tsv line %d: %r not found exactly once in %s; skipped" % (n, old, sec["id"]))
            continue
        hit[0]["text"] = hit[0]["text"].replace(old, new)

    lead = next((b for b in blocks if b["kind"] not in ("a", "scan")), None)
    parts = [] if lead and lead["kind"] in ("h2", "h3") else ["<h1>%s</h1>" % html.escape(sec["title"])]
    page = sec["first"]
    for b in blocks:
        if b["kind"] in ("a", "scan"):
            for x in MARK_RE.findall(b["text"]):
                page = int(x)
                parts.append('<a class="pg" id="pg%d"></a>' % page)
            if b["kind"] == "scan":
                parts.append('<p class="scan"><a href="scan:%d">This page is a table or list. '
                             'Tap to see the scanned page.</a></p>' % page)
            elif b["name"]:
                parts.append('<img class="ar" style="width:%d%%" src="ar/%s" alt="Arabic text">'
                             % (b["width"], b["name"]))
            continue
        text = curl(b["text"])
        chunks = MARK_RE.split(text)
        for i in range(0, len(chunks), 2):
            if i:
                page = int(chunks[i - 1])
            pagetext[page] = (pagetext.get(page, "") + " " + chunks[i]).strip()
        body = MARK_RE.sub(lambda m: '<a class="pg" id="pg%s"></a>' % m.group(1), html.escape(text))
        tag = {"p": "p", "q": "blockquote", "h2": "h2", "h3": "h3"}[b["kind"]]
        cls = ' class="flush"' if b.get("flush") and b["kind"] == "p" else ""
        parts.append("<%s%s>%s</%s>" % (tag, cls, body, tag))
        review.append("[%s|%s] %s" % (sec["id"], b["kind"], MARK_RE.sub(r"<<p\1>>", text)))
    with open(os.path.join(out, sec["id"] + ".html"), "w", encoding="utf-8") as f:
        f.write("\n".join(parts) + "\n")
    index.append(sec)
    review.extend("  ! " + n for n in notes)
    print(sec["id"], "pages", sec["first"], "-", sec["last"], "blocks", len(blocks), "notes", len(notes),
          flush=True)

json.dump(index, open(os.path.join(out, "index.json"), "w", encoding="utf-8"), ensure_ascii=False)
json.dump({str(k): v for k, v in sorted(pagetext.items())},
          open(os.path.join(out, "pagetext.json"), "w", encoding="utf-8"), ensure_ascii=False)
if review_path:
    open(review_path, "w", encoding="utf-8").write("\n".join(review) + "\n")
