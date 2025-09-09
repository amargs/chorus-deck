import click
import json
from .ppt_reader import read_ppt
from .models import Song

@click.group()
def cli():
    pass

@click.command()
@click.argument("ppt_file")
def titles(ppt_file):
    slide_titles = read_ppt(ppt_file)
    song_id = 1

    for title in slide_titles:
        if len(title) > 0:
            print(f"Song {song_id}: {title}")
            song_id += 1


@click.command()
@click.argument("ppt_file")
def index(ppt_file):
    slide_titles = read_ppt(ppt_file)
    song_id = 1
    song_data = []

    for i, title in enumerate(slide_titles, start=0):
        if len(song_data) > 0:
            song_data[-1].slide_end = i-1

        if len(title) > 0:
            song_data.append(Song(id=song_id, title=title, slide_start=i, slide_end=i))
            song_id += 1

    with open('indexed_song_data.json', 'w', encoding='utf-8') as f:
        json.dump([song.to_dict() for song in song_data], f, ensure_ascii=False, indent=4)
        print(f'Indexed {len(song_data)} songs and saved to {f.name}')


cli.add_command(titles)
cli.add_command(index)

if __name__ == "__main__":
    cli()