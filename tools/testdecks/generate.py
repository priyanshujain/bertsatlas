import json
import os
import random
import shutil
import struct
import sys
import wave
import zipfile
import zlib

from anki.collection import Collection, DeckIdLimit, ExportAnkiPackageOptions

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ankidecks/generated"
WORK = os.path.join(OUT, "work")
rng = random.Random(7)


def png(width, height, seed):
    r = random.Random(seed)
    color = bytes([r.randrange(256), r.randrange(256), r.randrange(256)])
    row = b"\x00" + color * width
    raw = row * height
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def noisy_png(size_bytes, seed):
    r = random.Random(seed)
    width = 512
    height = max(1, size_bytes // (width * 3))
    raw = b"".join(b"\x00" + r.randbytes(width * 3) for _ in range(height))
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 0)) + chunk(b"IEND", b"")


def tone_wav(path, frequency):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(8000)
        import math
        frames = b"".join(struct.pack("<h", int(8000 * math.sin(2 * math.pi * frequency * i / 8000))) for i in range(4000))
        w.writeframes(frames)


def fresh_collection(name):
    path = os.path.join(WORK, name + ".anki2")
    for suffix in ("", "-wal", "-shm"):
        if os.path.exists(path + suffix):
            os.remove(path + suffix)
    media = os.path.join(WORK, name + ".media")
    shutil.rmtree(media, ignore_errors=True)
    col = Collection(path)
    col.set_config("fsrs", True)
    return col


def add_media(col, name, data):
    path = os.path.join(WORK, name)
    with open(path, "wb") as f:
        f.write(data)
    return col.media.add_file(path)


def review_some(col, deck_id, count):
    col.decks.select(deck_id)
    for _ in range(count):
        card = col.sched.getCard()
        if not card:
            break
        col.sched.answerCard(card, rng.choice([1, 3, 3, 4]))


def export(col, deck_id, name, legacy):
    options = ExportAnkiPackageOptions(with_scheduling=True, with_deck_configs=True, with_media=True, legacy=legacy)
    path = os.path.join(OUT, name + (".legacy" if legacy else "") + ".apkg")
    col.export_anki_package(out_path=path, options=options, limit=DeckIdLimit(deck_id=deck_id))
    return path


def languages():
    col = fresh_collection("languages")
    deck = col.decks.id("Spanish Basics")
    greetings = col.decks.id("Spanish Basics::Greetings")
    food = col.decks.id("Spanish Basics::Food")
    reversed_type = col.models.by_name("Basic (and reversed card)")
    typed = col.models.by_name("Basic (type in the answer)")
    words = [("hola", "hello"), ("adiós", "goodbye"), ("gracias", "thank you"), ("por favor", "please"), ("buenos días", "good morning")]
    for index, (es, en) in enumerate(words):
        sound = add_media(col, f"es_{index}.wav", b"")
        tone_wav(os.path.join(WORK, f"es_{index}.wav"), 300 + index * 40)
        sound = col.media.add_file(os.path.join(WORK, f"es_{index}.wav"))
        note = col.new_note(reversed_type)
        note["Front"] = f"<b>{es}</b> [sound:{sound}]"
        note["Back"] = en
        note.tags = ["greetings"]
        col.add_note(note, greetings)
    for es, en in [("manzana", "apple"), ("pan", "bread"), ("agua", "water"), ("queso", "cheese"), ("leche", "milk")]:
        note = col.new_note(typed)
        note["Front"] = f"Spanish for <i>{en}</i>"
        note["Back"] = es
        note.tags = ["food"]
        col.add_note(note, food)
    review_some(col, deck, 8)
    return col, deck


def code_and_tables():
    col = fresh_collection("code")
    deck = col.decks.id("Python Interview")
    basic = col.models.by_name("Basic")
    snippets = [
        ("What does this print?<pre><code class=\"language-python\">print(sum(x * x for x in range(4)))</code></pre>", "<code>14</code>"),
        ("Time complexity of <code>dict</code> lookup?", "Average O(1), worst O(n)"),
        ("Big O table<table><tr><th>Structure</th><th>Lookup</th></tr><tr><td>list</td><td>O(n)</td></tr><tr><td>set</td><td>O(1)</td></tr></table>", "Sets hash their members"),
        ("Reverse a list in place<pre><code>xs.reverse()</code></pre>", "<ul><li><code>xs.reverse()</code></li><li><code>xs[::-1]</code> makes a copy</li></ul>"),
        ("Keyword to define a generator?", "<span style=\"color: #c00\">yield</span>"),
        ("<h3>Mutable default</h3><pre><code>def f(x=[]):\n    x.append(1)\n    return x</code></pre>", "The list is shared between calls<hr>Use <code>None</code>"),
    ]
    for front, back in snippets:
        note = col.new_note(basic)
        note["Front"] = front
        note["Back"] = back
        note.tags = ["python", "interview::basics"]
        col.add_note(note, deck)
    review_some(col, deck, 3)
    return col, deck


def mathjax():
    col = fresh_collection("mathjax")
    deck = col.decks.id("Calculus")
    basic = col.models.by_name("Basic")
    cloze = col.models.by_name("Cloze")
    for front, back in [
        (r"Derivative of \(x^n\)", r"\(n x^{n-1}\)"),
        (r"Fundamental theorem", r"\[\int_a^b f'(x)\,dx = f(b) - f(a)\]"),
        (r"Euler identity", r"\(e^{i\pi} + 1 = 0\)"),
        (r"Quadratic formula", r"\[x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}\]"),
    ]:
        note = col.new_note(basic)
        note["Front"] = front
        note["Back"] = back
        col.add_note(note, deck)
    note = col.new_note(cloze)
    note["Text"] = r"The integral of \(\frac{1}{x}\) is {{c1::\(\ln|x| + C\)}}"
    col.add_note(note, deck)
    return col, deck


def occlusion():
    col = fresh_collection("occlusion")
    deck = col.decks.id("Anatomy Diagrams")
    notetype = col.models.by_name("Image Occlusion")
    for index in range(3):
        image = os.path.join(WORK, f"diagram_{index}.png")
        with open(image, "wb") as f:
            f.write(png(400, 300, index))
        occlusions = (
            "{{c1::image-occlusion:rect:left=.1:top=.1:width=.3:height=.2:oi=1}}<br>"
            "{{c2::image-occlusion:ellipse:left=.5:top=.4:rx=.1:ry=.15:oi=1}}<br>"
            "{{c3::image-occlusion:polygon:points=.2,.7 .4,.9 .1,.9:oi=1}}"
        )
        col.add_image_occlusion_note(notetype_id=notetype["id"], image_path=image, occlusions=occlusions, header=f"Diagram {index + 1}", back_extra="Labelled parts", tags=["diagrams"])
    for card_id in col.find_cards(""):
        card = col.get_card(card_id)
        card.did = deck
        col.update_card(card)
    return col, deck


def cloze_heavy():
    col = fresh_collection("cloze")
    deck = col.decks.id("History Cloze")
    sections = [col.decks.id(f"History Cloze::Century {c}") for c in (18, 19, 20)]
    cloze = col.models.by_name("Cloze")
    for index in range(600):
        year = 1700 + index % 300
        note = col.new_note(cloze)
        note["Text"] = (
            f"In {{{{c1::{year}::year}}}} the {{{{c2::event {index}}}}} happened in {{{{c3::city {index % 40}::city}}}}, "
            f"led by {{{{c1::person {index % 17}}}}}."
        )
        note["Back Extra"] = f"Source note {index}"
        note.tags = [f"century::{year // 100 + 1}"]
        col.add_note(note, sections[index % 3])
    review_some(col, deck, 40)
    return col, deck


def large():
    col = fresh_collection("large")
    deck = col.decks.id("Vocabulary 30k")
    basic = col.models.by_name("Basic")
    letters = "abcdefghijklmnopqrstuvwxyz"
    col.db.execute("begin")
    for index in range(30000):
        note = col.new_note(basic)
        word = "".join(rng.choice(letters) for _ in range(rng.randint(4, 10)))
        note["Front"] = f"{word} {index}"
        note["Back"] = f"meaning of {word}"
        col.add_note(note, deck)
    review_some(col, deck, 200)
    return col, deck


def many_media():
    col = fresh_collection("manymedia")
    deck = col.decks.id("Icons 66k")
    basic = col.models.by_name("Basic")
    media_dir = col.media.dir()
    for index in range(66000):
        name = f"icon_{index}.png"
        with open(os.path.join(media_dir, name), "wb") as f:
            f.write(png(2, 2, index))
        note = col.new_note(basic)
        note["Front"] = f"<img src=\"{name}\">"
        note["Back"] = f"Icon {index}"
        col.add_note(note, deck)
    return col, deck


def heavy_media():
    col = fresh_collection("heavymedia")
    deck = col.decks.id("Photo Gallery")
    basic = col.models.by_name("Basic")
    media_dir = col.media.dir()
    for index in range(300):
        name = f"photo_{index}.png"
        with open(os.path.join(media_dir, name), "wb") as f:
            f.write(noisy_png(1_000_000, index))
        note = col.new_note(basic)
        note["Front"] = f"<img src=\"{name}\">"
        note["Back"] = f"Photo {index}"
        col.add_note(note, deck)
    return col, deck


def multi_deck_colpkg():
    col = fresh_collection("multideck")
    basic = col.models.by_name("Basic")
    cloze = col.models.by_name("Cloze")
    for top, subs in [("Chemistry", ["Elements", "Bonds"]), ("Music Theory", ["Intervals"]), ("Capitals Europe", [])]:
        top_id = col.decks.id(top)
        targets = [top_id] + [col.decks.id(f"{top}::{s}") for s in subs]
        for index in range(12):
            note = col.new_note(basic if index % 3 else cloze)
            if index % 3:
                note["Front"] = f"{top} question {index}"
                note["Back"] = f"{top} answer {index}"
            else:
                note["Text"] = f"{top} fact {{{{c1::number {index}}}}}"
            col.add_note(note, targets[index % len(targets)])
        review_some(col, top_id, 5)
    col.decks.id("Empty Deck")
    path = os.path.join(OUT, "multideck.colpkg")
    col.export_collection_package(path, include_media=True, legacy=False)
    col.close()
    return path


def oldest_format(legacy_path):
    target = legacy_path.replace(".legacy.apkg", ".anki2only.apkg")
    with zipfile.ZipFile(legacy_path) as source, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
        for item in source.infolist():
            if item.filename in ("collection.anki2", "meta"):
                continue
            name = "collection.anki2" if item.filename == "collection.anki21" else item.filename
            out.writestr(name, source.read(item.filename))
    return target


def main():
    os.makedirs(WORK, exist_ok=True)
    only = set(sys.argv[2:])
    produced = []
    builders = [
        ("languages", languages, True),
        ("code", code_and_tables, True),
        ("mathjax", mathjax, True),
        ("occlusion", occlusion, True),
        ("cloze", cloze_heavy, True),
        ("large", large, False),
        ("manymedia", many_media, False),
        ("heavymedia", heavy_media, False),
    ]
    for name, builder, with_legacy in builders:
        if only and name not in only:
            continue
        col, deck = builder()
        produced.append(export(col, deck, name, legacy=False))
        if with_legacy:
            legacy = export(col, deck, name, legacy=True)
            produced.append(legacy)
            if name == "code":
                produced.append(oldest_format(legacy))
        col.close()
        print("built", name, flush=True)
    if not only or "multideck" in only:
        produced.append(multi_deck_colpkg())
    for path in produced:
        print(path, os.path.getsize(path))


main()
