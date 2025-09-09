from pptx import Presentation
from .models import Song

TITLE_PREFIX = "Cântico:"

def read_song_titles(file_path: str) -> list[str]:
    ppt = Presentation(file_path)
    slide_titles = []

    for slide in ppt.slides:
        title_shape = slide.shapes.title
        if title_shape and title_shape.text:
            slide_titles.append(clean_title(title_shape.text))

    return slide_titles

def index_songs(file_path: str) -> list[Song]:
    ppt = Presentation(file_path)
    song_id = 1
    song_data = []

    for i, slide in enumerate(ppt.slides, start=0):
        title_shape = slide.shapes.title

        if title_shape and title_shape.text:
            song_data.append(Song(id=song_id, title=clean_title(title_shape.text), slide_start=i, slide_end=i))
            song_id += 1
        elif slide_is_empty(slide) and len(song_data) > 0:
            song_data[-1].slide_end = i-1
        
    if len(song_data) > 0:
        song_data[-1].slide_end = len(ppt.slides) - 1

    return song_data

def clean_title(title: str) -> str:
    return title.replace(TITLE_PREFIX, "").strip()

def slide_is_empty(slide) -> bool:
    for shape in slide.shapes:
        if shape.has_text_frame and shape.text.strip():
            return False
    return True
