"""Retire Isa/Sinni training dialogue without changing either quest's Peerless reward."""
import csv
import hashlib
import io
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODS = ROOT.parent
BACKUP = ROOT / "build" / "story-training-backups"


def remove_rows(path, predicate):
    data = path.read_bytes()
    text = data.decode("utf-8-sig")
    lines = text.splitlines(keepends=True)
    reader = csv.reader(io.StringIO(text, newline=""))
    start, pieces, removed = 0, [], []
    for row in reader:
        end = reader.line_num
        raw = "".join(lines[start:end])
        if row and predicate(row[0]):
            removed.append(row[0])
        else:
            pieces.append(raw)
        start = end
    if removed:
        BACKUP.mkdir(parents=True, exist_ok=True)
        (BACKUP / (path.parent.parent.parent.name + "-" + hashlib.sha256(data).hexdigest()[:12] + ".csv")).write_bytes(data)
        path.write_bytes((b"\xef\xbb\xbf" if data.startswith(b"\xef\xbb\xbf") else b"") + "".join(pieces).encode("utf-8"))
    print(path.parent.parent.parent.name, json.dumps(removed))


cn = MODS / "Chief Navigator/data/campaign/rules.csv"
if cn.exists():
    remove_rows(cn, lambda rule: rule.startswith("chiefNavigatorSinniPeerless"))
    mirror = MODS / "Chief Navigator/dialogue/sinni_peerless.ink"
    if mirror.exists():
        new = ("// Peerless training is available through the player's own Contacts entry.\n"
               "// Sinni's retired training menu has no runtime rules or contact button.\n"
               "// The final Labor debrief still awards its once-per-campaign Peerless point.\n"
               "-> DONE\n")
        if mirror.read_text(encoding="utf-8-sig") != new:
            BACKUP.mkdir(parents=True, exist_ok=True)
            old = mirror.read_bytes()
            (BACKUP / ("sinni-peerless-" + hashlib.sha256(old).hexdigest()[:12] + ".ink")).write_bytes(old)
            mirror.write_text(new, encoding="utf-8")

hot = MODS / "HallOfTriumph/data/campaign/rules.csv"
if hot.exists():
    remove_rows(hot, lambda rule: rule.startswith("shipTrophyIsaPeerless") and rule != "shipTrophyIsaPeerlessAward")
