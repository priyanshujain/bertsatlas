import json
import os
import shutil
import sys
import tempfile
import time

from anki.collection import Collection, ImportAnkiPackageOptions, ImportAnkiPackageRequest

MEDIA_PATTERN = None


def counts_per_top_level(col):
    result = {}
    for deck in col.decks.all_names_and_ids():
        if "::" in deck.name:
            continue
        ids = [deck.id] + [child[1] for child in col.decks.children(deck.id)]
        id_list = ",".join(str(i) for i in ids)
        cards = col.db.scalar(f"select count() from cards where did in ({id_list}) or odid in ({id_list})")
        if not cards:
            continue
        notes = col.db.scalar(f"select count(distinct nid) from cards where did in ({id_list}) or odid in ({id_list})")
        revlog = col.db.scalar(f"select count() from revlog where cid in (select id from cards where did in ({id_list}) or odid in ({id_list}))")
        sections = sorted(name[len(deck.name) + 2:] for name, _ in col.decks.children(deck.id))
        result[deck.name] = {"notes": notes, "cards": cards, "revlog": revlog, "sections": sections}
    return result


def reference(package):
    directory = tempfile.mkdtemp(prefix="ankiref-")
    try:
        path = os.path.join(directory, "collection.anki2")
        started = time.time()
        if package.endswith(".colpkg"):
            col = Collection(path)
            col.close(downgrade=False)
            col._backend.import_collection_package(col_path=path, backup_path=package, media_folder=os.path.join(directory, "collection.media"), media_db=os.path.join(directory, "collection.media.db2"))
            col = Collection(path)
        else:
            col = Collection(path)
            request = ImportAnkiPackageRequest(package_path=package, options=ImportAnkiPackageOptions(with_scheduling=True, with_deck_configs=True))
            col.import_anki_package(request)
        decks = counts_per_top_level(col)
        media = len(os.listdir(col.media.dir()))
        col.close()
        return {"decks": decks, "media": media, "seconds": round(time.time() - started, 2)}
    finally:
        shutil.rmtree(directory, ignore_errors=True)


output = {}
for package in sys.argv[1:]:
    output[package] = reference(package)
    print(package, json.dumps(output[package]["decks"])[:200], output[package]["media"], file=sys.stderr, flush=True)
json.dump(output, open("/tmp/ankidecks/reference.json", "w"), indent=1)
