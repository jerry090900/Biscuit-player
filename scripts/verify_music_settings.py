#!/usr/bin/env python3
"""Exercise Room-exported bookmark SQL with SQLite; never touches user databases."""
import json
from pathlib import Path
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / 'app/schemas/xyz.mpv.rex.ui.browser.miniplayer.settings.MusicBookmarksDatabase/1.json'


def create_schema(db, schema):
    for entity in schema['database']['entities']:
        table = entity['tableName']
        db.execute(entity['createSql'].replace('${TABLE_NAME}', table))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', table))


def verify():
    schema = json.loads(SCHEMA.read_text())
    assert schema['database']['version'] == 1
    with tempfile.TemporaryDirectory() as directory:
        filename = str(Path(directory) / 'bookmarks.db')
        with sqlite3.connect(filename) as db:
            create_schema(db, schema)
            db.execute('INSERT INTO music_bookmarks VALUES (?, ?, ?, ?, ?)', ('a', 'source-one', 42000, 'Chorus', 1))
            db.execute('INSERT INTO music_bookmarks VALUES (?, ?, ?, ?, ?)', ('b', 'source-two', 9000, 'Intro', 2))
        with sqlite3.connect(filename) as db:
            assert db.execute('SELECT name, positionMs FROM music_bookmarks WHERE mediaKey=?', ('source-one',)).fetchall() == [('Chorus', 42000)]
            db.execute('UPDATE music_bookmarks SET name=? WHERE id=? AND mediaKey=?', ('Wrong', 'a', 'source-two'))
            assert db.execute('SELECT name FROM music_bookmarks WHERE id=?', ('a',)).fetchone() == ('Chorus',)
            db.execute('UPDATE music_bookmarks SET name=? WHERE id=? AND mediaKey=?', ('New chorus', 'a', 'source-one'))
            db.execute('DELETE FROM music_bookmarks WHERE id=? AND mediaKey=?', ('a', 'source-two'))
            assert db.execute('SELECT COUNT(*) FROM music_bookmarks').fetchone() == (2,)
        with sqlite3.connect(filename) as db:
            assert db.execute('SELECT name FROM music_bookmarks WHERE id=?', ('a',)).fetchone() == ('New chorus',)
            db.execute('DELETE FROM music_bookmarks WHERE id=? AND mediaKey=?', ('a', 'source-one'))
            assert db.execute('SELECT id FROM music_bookmarks').fetchall() == [('b',)]
        # Existing playlist schema: atomic rollback must preserve old entries/history.
        with sqlite3.connect(':memory:') as db:
            create_schema(db, json.loads((ROOT / 'app/schemas/xyz.mpv.rex.database.MpvExDatabase/19.json').read_text()))
            db.execute("INSERT INTO PlaylistEntity (id,name,createdAt,updatedAt,isM3uPlaylist) VALUES (1,'Existing',1,1,0)")
            db.commit()
            try:
                with db:
                    db.execute("INSERT INTO PlaylistEntity (id,name,createdAt,updatedAt,isM3uPlaylist) VALUES (2,'New',2,2,0)")
                    raise RuntimeError('simulate interrupted queue save')
            except RuntimeError:
                pass
            assert db.execute('SELECT name FROM PlaylistEntity').fetchall() == [('Existing',)]
    print('PASS: Room bookmark schema, reopen persistence, scoped rename/delete, source isolation, playlist transaction rollback')


if __name__ == '__main__':
    verify()
