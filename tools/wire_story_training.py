"""Route installed story contacts to the shared text dialogue, preserving every other CSV record byte-for-byte."""
import csv, io, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODS = ROOT.parent

def patch(path, updates):
    data = path.read_bytes()
    text = data.decode('utf-8-sig')
    lines = text.splitlines(keepends=True)
    reader = csv.reader(io.StringIO(text, newline=''))
    start = 0
    pieces = []
    changed = []
    for row in reader:
        end = reader.line_num
        raw = ''.join(lines[start:end])
        if row and row[0] in updates:
            for index, value in updates[row[0]].items(): row[index] = value
            output = io.StringIO(newline='')
            csv.writer(output, lineterminator='\r\n').writerow(row)
            replacement = output.getvalue()
            if raw != replacement: changed.append(row[0])
            pieces.append(replacement)
        else: pieces.append(raw)
        start = end
    if changed:
        backup = ROOT / 'build' / 'story-training-backups'
        backup.mkdir(parents=True, exist_ok=True)
        (backup / (path.parent.parent.parent.name + '-' + hashlib.sha256(data).hexdigest()[:12] + '.csv')).write_bytes(data)
        path.write_bytes((b'\xef\xbb\xbf' if data.startswith(b'\xef\xbb\xbf') else b'') + ''.join(pieces).encode('utf-8'))
    print(path.parent.parent.parent.name, json.dumps(changed))

cn = MODS / 'Chief Navigator/data/campaign/rules.csv'
if cn.exists():
    patch(cn, {
        'chiefNavigatorSinniPeerlessOpen': {3: 'ChiefNavigatorPeopleCMD show sinni\nPeerlessSkillsCMD open'},
        'chiefNavigatorPeerlessAvailable': {3: 'PeerlessSkillsCMD open', 4: ''},
        'chiefNavigatorPeerlessSelected': {3: 'PeerlessSkillsCMD open', 4: '', 5: ''},
    })
    # This entry now delegates all content and choices to the independent mod.
    mirror = MODS / 'Chief Navigator/dialogue/sinni_peerless.ink'
    new = ('// Runtime authority: data/campaign/rules.csv, chiefNavigatorSinniPeerlessOpen.\n'
           '// Entry: Contacts > Sinni > Peerless training.\n'
           '// ChiefNavigatorPeopleCMD show sinni\n'
           '// PeerlessSkillsCMD open delegates to the shared standard text/options dialogue.\n'
           '// Definitions and choices: Peerless Skills/src/peerless/api/SkillCatalog.java\n'
           '// and Peerless Skills/src/peerless/campaign/TrainingDialog.java.\n'
           '// One point per upgrade; Elite required; purchased training survives respecs.\n'
           '-> DONE\n')
    if mirror.read_text(encoding='utf-8-sig') != new:
        backup = ROOT / 'build/story-training-backups/sinni_peerless-before.ink'
        if not backup.exists(): backup.write_bytes(mirror.read_bytes())
        mirror.write_text(new, encoding='utf-8')
    mirror = MODS / 'Chief Navigator/dialogue/menelaus_peerless.ink'
    text = mirror.read_text(encoding='utf-8-sig')
    marker = '=== peerless_available ==='
    new = text.split(marker)[0] + marker + '\n// PeerlessSkillsCMD open: shared text training dialogue.\n-> DONE\n\n=== peerless_selected ===\n// PeerlessSkillsCMD open: shared text training dialogue.\n-> DONE\n'
    if text != new:
        backup = ROOT / 'build/story-training-backups/menelaus_peerless-before.ink'
        if not backup.exists(): backup.write_bytes(mirror.read_bytes())
        mirror.write_text(new, encoding='utf-8')

hot = MODS / 'HallOfTriumph/data/campaign/rules.csv'
if hot.exists():
    patch(hot, {
        'shipTrophyIsaPeerlessOpen': {3: 'PeerlessSkillsCMD open'},
        'shipTrophyIsaPeerlessSpent': {3: ''},
    })
