# chorus-deck

eCoral prepares the PowerPoint for Mass. It writes a cover, the readings, the prayers that stay the same, and the songs.

The bar is **Início**, **Apresentações**, **Cancioneiro**, **Leccionário**, and **Configuração**. Apresentações opens **Eucaristia** or **Cânticos**.

- **Eucaristia** builds a Sunday Mass. The date fills the name, the season, the year, and the reading sentences, and a Sunday it has not seen before is fetched and remembered. Each moment of the Mass can take a song. Glória and Pai Nosso can stay spoken (**Rezado**). Advent and Lent leave out the Glória, and in Lent the Alleluia is the Louvor verse. You can choose the Creed, a response for the prayer of the faithful, the pause between slides, and the highlight color. A Sunday that is also a fixed solemnity can stay the Sunday or use that solemnity. The cover and the reading sentences can be edited for this presentation. An unfinished Mass is kept on this computer, and the next visit asks whether to continue it.
- **Cânticos** is a deck of songs in the order you choose, with an optional cover. The file name starts as `cânticos.pptx`.
- **Cancioneiro** is where you add or correct a song: the title, an optional alternative title, the songbook number, and the verses, including bold, italic, and underline. A song can keep a second language beside the first, and one of the two is the usual language. A saved song is then available in either deck. The pickers find a song by its alternative title and show it in parentheses. The slide title stays the main title.
- **Leccionário** keeps the reading sentences for a Sunday or a solemnity. The next Eucaristia reads them, unless that presentation replaces them.
- **Configuração** keeps the song index and the liturgy on this computer, or in a folder you share, for example on Dropbox. **Importar** replaces those files with a copy from another folder. **Exportar** saves a copy of the ones in use.

A song with two languages is sung in one of them. On Eucaristia the moment name shows that language, as in Cântico de entrada · Inglês. On Cânticos the same name sits under the song title. A translation button, the first action on the row, switches to the other language. A song with one language has no button and no language line.

When a song has a refrain and more than one estrofe, a button opens a menu of those estrofes. Each one can be left out on its own. The last one stays on. The menu lists an estrofe by its first line and the two words that follow, and one Refrão line says where it is sung. The button is tinted while the menu is open, and it returns to an ordinary button when the menu closes, even if some estrofes were left out. On Eucaristia the moment name carries the count, as in Cântico de entrada · 2 estrofes, after the language when the song has one. On Cânticos that same line sits under the title, as in Português · 2 estrofes. A chosen song with only a title uses the same row height, and the title stays centered. A rite, a song of one estrofe, and a song with no refrain have no estrofe choice. The alternative title is not repeated on the chosen row.

On both screens the control that takes the song off the row is a gray X with no border. Hovering it darkens the mark. It does not take the season color.

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

eCoral will open in its own window. The window needs the Microsoft Edge WebView2 runtime, which current Windows already includes. The first visit asks where to keep your changes: only on this computer, or in a folder you choose.

To work on the page with live reload, leave `serve` running and, from `frontend`, run `npm run dev`. `./gradlew packageApp`, after `npm run build`, writes the Windows installer to `backend/build/distributions/`.

`generate` needs IBM Plex Sans SemiBold, Livvic SemiBold, Playfair Display, and Playfair Display Bold installed. If one is missing, eCoral says so and does not write the deck.

## Command line

Run these from `backend`. `config.json` points at the files, which normally all live in `data/`: the source PowerPoint, the song index, the template, and the two songbooks. When a value has spaces, put single quotes around `--args` so the sentence stays in one piece.

`index` refreshes the song list. The lyrics come from `data/input.pptx`, and the numbers from `data/songbook_default.docx` and `data/songbook_rites.docx`. If the PowerPoint has changed, the lyrics are rebuilt and each song keeps its id. If only a songbook has changed, the numbers are updated and the lyrics stay as they are. When both are unchanged, it says `Sources unchanged`. A title, a second language, and an alternative title already saved in the app stay on the song.

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

A full Mass can also sit in a small YAML file, such as `backend/domingo.yml`. `--date` picks a different day from the one written there. The Sunday texts come from `data/liturgy.json`, next to the index. A song in that file can name its language and the estrofes to keep. When those are omitted, the deck uses the song’s usual language and every estrofe.

```bash
./gradlew run --args "generate --mass domingo.yml"
```

The deck is saved beside `config.json`. Name it with `outputFile` in that file, or with `output` in the Mass file. Close it in PowerPoint before generating it again.

`./gradlew test` runs the tests.
