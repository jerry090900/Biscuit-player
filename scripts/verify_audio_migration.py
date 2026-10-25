"""Exercise the actual additive migration SQL against exported Room schemas with stdlib SQLite."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schemas = root / "app/schemas/xyz.mpv.rex.database.MpvExDatabase"
old = json.loads((schemas / "18.json").read_text())["database"]
new = json.loads((schemas / "19.json").read_text())["database"]
db = sqlite3.connect(":memory:")
for entity in old["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for index in entity.get("indices", []):
        db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
# Seed real playback/history data, including the legacy external-source string and saved position.
for table in ("PlaybackStateEntity", "RecentlyPlayedEntity"):
    columns = db.execute(f'PRAGMA table_info("{table}")').fetchall()
    names = [c[1] for c in columns]
    values = ["preserve:" + c[1] if c[2] == "TEXT" else 42 for c in columns]
    quoted = ",".join('"' + name + '"' for name in names)
    db.execute(f'INSERT INTO "{table}" ({quoted}) VALUES ({",".join("?" for _ in names)})', values)
before = {e["tableName"]: db.execute(f'SELECT * FROM "{e["tableName"]}"').fetchall() for e in old["entities"]}
source = (root / "app/src/main/kotlin/xyz/mpv/rex/database/AudioPersistenceMigration.kt").read_text()
statements = re.findall(r'db\.execSQL\("""(.*?)"""\)', source, re.S)
assert len(statements) == 3
for sql in statements:
    db.execute(sql)
for entity in new["entities"]:
    name = entity["tableName"]
    actual = {row[1]: (row[2], bool(row[3]), row[5]) for row in db.execute(f'PRAGMA table_info("{name}")')}
    keys = entity["primaryKey"]["columnNames"]
    expected = {f["columnName"]: (f["affinity"], f.get("notNull", False), keys.index(f["columnName"]) + 1 if f["columnName"] in keys else 0) for f in entity["fields"]}
    assert actual == expected, (name, actual, expected)
for name, rows in before.items():
    assert db.execute(f'SELECT * FROM "{name}"').fetchall() == rows, name
print("Migration 18→19: all Room column/type/nullability/PK definitions match; existing playback/history and all old tables preserved.")
