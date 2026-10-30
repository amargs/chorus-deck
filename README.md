# chorus-deck

eCoral prepares the PowerPoint for Mass. It writes a cover, the readings, the prayers that stay the same, and the songs.

- **Eucaristia** builds a Sunday Mass. The date fills the name, the season, the year, and the reading sentences, and a Sunday it has not seen before is fetched and remembered. Each moment of the Mass can take a song. Advent and Lent leave out the Glória, and in Lent the Alleluia is the Louvor verse. You can choose the Creed, a response for the prayer of the faithful, the pause between slides, and the highlight color. A Sunday that is also a fixed solemnity can stay the Sunday or use that solemnity. An unfinished Mass is kept on this computer.
- **Lista de cânticos** is a deck of songs in the order you choose, with an optional cover.
- **Cancioneiro** is where you add or correct a song: the title, the songbook number, and the verses, including bold, italic, and underline. A saved song is then available in either deck.

## Launch the application

The page is built with Node.js 20, and the backend runs on Java 20. Build the pages first, then start eCoral.

From `frontend`:

```bash
npm run build
```

From `backend`:

```bash
./gradlew run --args "serve"
```

eCoral will open in its own window. The window needs the Microsoft Edge WebView2 runtime, which current Windows already includes.

To work on the page with live reload, leave `serve` running and, from `frontend`, run `npm run dev`. `./gradlew packageApp`, after `npm run build`, writes the Windows installer to `backend/build/distributions/`.

## Command line

Run these from `backend`. `config.json` points at the files, which normally all live in `data/`: the source PowerPoint, the song index, the template, and the two songbooks. When a value has spaces, put single quotes around `--args` so the sentence stays in one piece.

`index` refreshes the song list. The lyrics come from `data/input.pptx`, and the numbers from `data/songbook_default.docx` and `data/songbook_rites.docx`. If the PowerPoint has changed, the lyrics are rebuilt and each song keeps its id. If only a songbook has changed, the numbers are updated and the lyrics stay as they are. When both are unchanged, it says `Sources unchanged`.

```bash
./gradlew run --args "index"
```

`diff-sources` lists slides and songbook headings that do not match, and leaves the files alone. `generate` will mention it too, if one of those files has changed since the last `index`.

```bash
./gradlew run --args "diff-sources"
```

`generate` builds a deck from the songs you name and the Sunday you describe. Use the songbook number (`2`, `A1`, `RP3`) or the id stored on the song, and write the Sunday name, the sentence, and the verse as you want them read. eCoral adds the «» around the sentence, and the season line (`Tempo do Advento · Ano B`).

```bash
./gradlew run --args 'generate --book-ids 2,1 --season advento --ano B --domingo "Domingo II" --frase "Dai a César o que é de César e a Deus o que é de Deus" --versiculo "Mt 3, 15-21"'
```

The season is `comum`, `advento`, `natal`, `quaresma`, `ramos`, `pascoa`, or `pentecostes`, and the year is `A`, `B`, or `C`. `--cover "Capa - Domingo III Advento"` uses that cover for a special Sunday. `--mode songs` makes a deck that is only those songs.

A full Mass can also sit in a small YAML file, such as `backend/domingo.yml`. `--date` picks a different day from the one written there. The Sunday texts come from `data/liturgy.json`, next to the index.

```bash
./gradlew run --args "generate --mass domingo.yml"
```

The deck is saved beside `config.json`. Name it with `outputFile` in that file, or with `output` in the Mass file. Close it in PowerPoint before generating it again.

`./gradlew test` runs the tests.
