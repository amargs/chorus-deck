from dataclasses import dataclass

@dataclass
class Song:
    id: int
    title: str
    slide_start: int
    slide_end: int

    def to_dict(self):
        return {
            "id": self.id,
            "title": self.title,
            "slide_start": self.slide_start,
            "slide_end": self.slide_end
        }
    
    def from_dict(data: dict):
        return Song(
            id=data["id"],
            title=data["title"],
            slide_start=data["slide_start"],
            slide_end=data["slide_end"]
        )
