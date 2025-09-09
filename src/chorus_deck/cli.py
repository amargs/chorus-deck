import sys
from .ppt_reader import read_ppt

def main():
    if len(sys.argv) < 2:
        print("Usage: chorus-deck <pptx-source-file>")
        sys.exit(1)

    ppt_file = sys.argv[1]
    slide_titles = read_ppt(ppt_file)

    for i, title in enumerate(slide_titles, start=1):
        print(f"Slide {i}: {title}")
        print("-" * 40)
