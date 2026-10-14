# chorus-deck

Chorus Deck builds a PowerPoint for Mass: a cover slide, then the songs you pick.

The files it uses are listed in `backend/config.json`. By default the song source, the index, the template, and the generated deck all live in `backend/data/`.

Open a terminal in `backend` and use Java 20.

To refresh the song list from the source PowerPoint:

```bash
./gradlew run --args "index"
```

To build a deck, name the songs by their position in the index and say which Sunday it is. The season is one of `comum`, `advento`, `natal`, `quaresma`, `ramos`, `pascoa`, or `pentecostes`. The year is `A`, `B`, or `C`. You do not type the «» around the sentence or the "Tempo … · Ano …" line; the program adds those.

```bash
./gradlew run --args 'generate --ids 8,98 --season advento --ano B --domingo "Domingo II" --frase "Dai a César o que é de César e a Deus o que é de Deus" --versiculo "Mt 3, 15-21"'
```

Keep the single quotes. Without them the terminal splits the sentence and Gradle treats the leftover words as extra tasks.

For a special Sunday, add `--cover` with the layout name from the template, for example `Capa - Domingo III Advento`. That cover is used instead of the one for the season.
