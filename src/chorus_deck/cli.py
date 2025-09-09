import click
import json
from .ppt_handler import read_song_titles, index_songs

INDEXED_SONGS_FILE = 'indexed_song_data.json'

@click.group()
def cli():
    pass

@click.command()
@click.argument("ppt_file")
def titles(ppt_file):
    slide_titles = read_song_titles(ppt_file)
    song_id = 1

    for title in slide_titles:
        print(f"Song {song_id}: {title}")
        song_id += 1


@click.command()
@click.argument("ppt_file")
def index(ppt_file):
    song_data = index_songs(ppt_file)

    with open(INDEXED_SONGS_FILE, 'w', encoding='utf-8') as f:
        json.dump([song.to_dict() for song in song_data], f, ensure_ascii=False, indent=4)
        
    print(f'Indexed {len(song_data)} songs and saved to {INDEXED_SONGS_FILE}')


cli.add_command(titles)
cli.add_command(index)

if __name__ == "__main__":
    cli()