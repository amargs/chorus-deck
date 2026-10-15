# Agents

Java 20 Gradle app in `backend/`. Run from that directory: `./gradlew test` and `./gradlew run --args '...'`. On the shell, keep single quotes around `--args` so text flags are not split into extra Gradle tasks. IntelliJ uses the same program arguments with working directory `backend`.

Paths are in `backend/config.json` and are relative to that file. Data lives in `backend/data/`: `input.pptx` (source songs, do not commit), `index.json` (commit), `template_4x3.pptx` (commit), `fit.json` (fitted font sizes by song id and slide, generated, do not commit). A deck file names the generated pptx with `output`, or, when that is omitted, `{YYYY-MM-DD} {Domingo} {season} - Ano {A|B|C}.pptx` beside the deck file (`2026-12-13 Domingo III Advento - Ano B.pptx`). Without a date the name omits that prefix (`Domingo XXIX Tempo Comum - Ano A.pptx`). A feast whose name already includes the season keeps that name (`Domingo de Ramos - Ano A.pptx`). Close that file in PowerPoint before generating again.

`generate --ids` takes 1-based positions from `index.json`. `generate --book-ids` takes songbook ids (`bookId`: `118`, `A1`, `RP3`). Either list is the deck order, and the two flags are not combined. `index` rebuilds that file from `input.pptx`. A song title is two lines (a label such as `Cântico:` or a number, then the name); following slides that use the lyric placeholder are more verses. A single-line title, and a slide that is only a free text box, is not a song. Bold runs are stored as `**...**` and drawn in IBM Plex Sans SemiBold. `generate` stops at the start if IBM Plex Sans SemiBold, Playfair Display, or Playfair Display Bold is not installed. A song that is a single slide and entirely bold is stored as plain text.

`generate` writes one cover, then each song: first verse on a title layout, later verses on `Letra`.

| Season or `--cover` | Cover layout | Title layout | Header |
|---|---|---|---|
| `comum` | `Capa - Tempo Comum` | `Título e letra` | `accent1` |
| `advento` | `Capa - Advento` | `Título e letra` | `accent2` |
| `quaresma` | `Capa - Quaresma` | `Título e letra` | `accent2` |
| `ramos` | `Capa - Ramos` | `Título e letra` | `accent5` |
| `pentecostes` | `Capa - Pentecostes` | `Título e letra` | `accent5` |
| `Capa - Domingo III Advento` | that cover | `Título e letra` | `accent3` |
| `Capa - Domingo IV Quaresma` | that cover | `Título e letra` | `accent3` |
| `natal` | `Capa - Natal` | `Título e letra - Natal e Páscoa` | white bar, gold title, already on the layout |
| `pascoa` | `Capa - Páscoa` | `Título e letra - Natal e Páscoa` | white bar, gold title, already on the layout |

`Tempo` is generated, not typed: `Tempo Comum`, `Tempo do Advento`, `Tempo do Natal`, `Tempo de Quaresma` (also Ramos and Domingo IV Quaresma), `Tempo Pascal` (also Pentecostes). Domingo III Advento stays `Tempo do Advento`. The line is `{that} · Ano {A\|B\|C}`. `--frase` is wrapped in «» when the user did not already include them. `--domingo` and `--versiculo` are copied as given.

Cover fields the code fills: `Domingo`, `Frase`, `Versiculo`, `Tempo`. Only `Frase` is fitted. The other cover fields keep the layout size. Song fields: `Título`, `Letra`. `Domingo` is Playfair Display Bold on the layout; keep that bold. Do not rewrite `Header` (colored bar; recolor its scheme fill on the title layout in the generated file only) or `Picture 4` (season image on the cover). Continuation verses stay on the shared `Letra` layout. Lyric fitting reads the live text box and measures semibold wider than regular.

Theme accents: `accent1` `#377D49` green, `accent2` `#A461AF` purple, `accent3` `#F99595` rose, `accent4` `#8C6A00` gold, `accent5` `#B92323` red (Pentecost, Palm Sunday, Confirmation). Easter season is gold, not red.

Stage 1 (index, generate, cover, season header) is implemented. Do not start song numbers, response slides, liturgia.pt, or a UI unless the user asks for that stage.
