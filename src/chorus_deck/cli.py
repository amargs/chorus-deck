import click
from .ppt_reader import read_ppt

@click.group()
def cli():
    pass

@click.command()
@click.argument("ppt_file")
def titles(ppt_file):
    slide_titles = read_ppt(ppt_file)
    for i, title in enumerate(slide_titles, start=1):
        print(f"Song {i}: {title}")

cli.add_command(titles)
cli.add_command(index)

if __name__ == "__main__":
    cli()