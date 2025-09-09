import click
import json
from .ppt_handler import read_song_titles, index_songs, create_ppt
from .models import Song

INDEXED_SONGS_FILE = 'indexed_song_data.json'

@click.group()
def cli():
    pass

@click.command()
@click.option('--input', '-i')
def titles(input):
    slide_titles = read_song_titles(input)
    song_id = 1

    for title in slide_titles:
        print(f"Song {song_id}: {title}")
        song_id += 1


@click.command()
@click.option('--input', '-i')
def index(input):
    song_data = index_songs(input)

    with open(INDEXED_SONGS_FILE, 'w', encoding='utf-8') as f:
        json.dump([song.to_dict() for song in song_data], f, ensure_ascii=False, indent=4)

    print(f'Indexed {len(song_data)} songs and saved to {INDEXED_SONGS_FILE}')


@click.command()
@click.option('--input', '-i')
@click.option('--song', '-s', multiple=True, type=int)
def create(input, song):
    
    with open(INDEXED_SONGS_FILE, 'r', encoding='utf-8') as f:
        song_data = [Song.from_dict(item) for item in json.load(f)]
        songs = []

        for song_id in song:
            if len(song_data) < song_id or song_id < 1:
                print(f'Song ID {song_id} not found in indexed data.')
                return
            
            songs.append(song_data[song_id-1])

        output_ppt = create_ppt(input, songs)

        output_file = "output.pptx"
        output_ppt.save(output_file)
        print(f'Created new PowerPoint file with selected songs: {[song_data[s-1].title for s in song]}')


cli.add_command(titles)
cli.add_command(index)
cli.add_command(create)

if __name__ == "__main__":
    cli()