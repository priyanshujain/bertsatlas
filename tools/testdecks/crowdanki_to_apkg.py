import json
import os
import shutil
import sys

from anki.collection import Collection, DeckIdLimit, ExportAnkiPackageOptions

source_dir, out_dir = sys.argv[1], sys.argv[2]
deck_json = json.load(open(os.path.join(source_dir, "deck.json"), encoding="utf-8"))
work = os.path.join(out_dir, "work")
os.makedirs(work, exist_ok=True)
path = os.path.join(work, "crowdanki.anki2")
for suffix in ("", "-wal", "-shm"):
    if os.path.exists(path + suffix):
        os.remove(path + suffix)
shutil.rmtree(os.path.join(work, "crowdanki.media"), ignore_errors=True)
col = Collection(path)

notetypes = {}
for model in deck_json["note_models"]:
    notetype = col.models.new(model["name"])
    for field in model["flds"]:
        col.models.add_field(notetype, col.models.new_field(field["name"]))
    for template in model["tmpls"]:
        added = col.models.new_template(template["name"])
        added["qfmt"] = template["qfmt"]
        added["afmt"] = template["afmt"]
        col.models.add_template(notetype, added)
    notetype["css"] = model["css"]
    col.models.add(notetype)
    notetypes[model["crowdanki_uuid"]] = col.models.by_name(model["name"])

deck_id = col.decks.id(deck_json["name"])
deck = col.decks.get(deck_id)
deck["desc"] = deck_json.get("desc", "")
col.decks.save(deck)
for note_json in deck_json["notes"]:
    note = col.new_note(notetypes[note_json["note_model_uuid"]])
    note.guid = note_json["guid"]
    for index, value in enumerate(note_json["fields"]):
        note.fields[index] = value
    note.tags = note_json["tags"]
    col.add_note(note, deck_id)
media_dir = os.path.join(source_dir, "media")
for name in sorted(os.listdir(media_dir)):
    shutil.copy(os.path.join(media_dir, name), os.path.join(col.media.dir(), name))

name = os.path.basename(out_dir.rstrip("/"))
for legacy in (False, True):
    options = ExportAnkiPackageOptions(with_scheduling=True, with_deck_configs=True, with_media=True, legacy=legacy)
    target = os.path.join(out_dir, "ultimate-geography" + (".legacy" if legacy else "") + ".apkg")
    col.export_anki_package(out_path=target, options=options, limit=DeckIdLimit(deck_id=deck_id))
    print(target, os.path.getsize(target))
col.close()
