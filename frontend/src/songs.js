export const COVERS = [
  { id: 'Capa - Tempo Comum', label: 'Tempo Comum', accent: 'accent1', season: 'comum' },
  { id: 'Capa - Advento', label: 'Advento', accent: 'accent2', season: 'advento', omitsGloria: true },
  { id: 'Capa - Natal', label: 'Natal', accent: 'accent4', season: 'natal' },
  { id: 'Capa - Quaresma', label: 'Quaresma', accent: 'accent2', season: 'quaresma', omitsGloria: true, lenten: true },
  { id: 'Capa - Domingo III Advento', label: 'Domingo III do Advento', accent: 'accent3', season: 'advento', omitsGloria: true },
  { id: 'Capa - Domingo IV Quaresma', label: 'Domingo IV da Quaresma', accent: 'accent3', season: 'quaresma', omitsGloria: true, lenten: true },
  { id: 'Capa - Ramos', label: 'Ramos', accent: 'accent5', season: 'ramos', omitsGloria: true, lenten: true },
  { id: 'Capa - Páscoa', label: 'Páscoa', accent: 'accent4', season: 'pascoa' },
  { id: 'Capa - Pentecostes', label: 'Pentecostes', accent: 'accent5', season: 'pentecostes' },
  { id: 'Capa - Solenidade', label: 'Solenidade', accent: 'accent4' },
  { id: 'Capa - Solenidade Pedro Paulo', label: 'Solenidade Pedro e Paulo', accent: 'accent5' },
  { id: 'Capa - Solenidade Paz', label: 'Paz', accent: 'accent6' },
  { id: 'Capa - Maria', label: 'Maria', accent: 'accent6' },
  { id: 'Capa - Taizé', label: 'Taizé' },
]

export const SEASONS = [
  { id: 'comum', label: 'Tempo Comum' },
  { id: 'advento', label: 'Advento' },
  { id: 'natal', label: 'Natal' },
  { id: 'quaresma', label: 'Quaresma' },
  { id: 'ramos', label: 'Ramos' },
  { id: 'pascoa', label: 'Páscoa' },
  { id: 'pentecostes', label: 'Pentecostes' },
]

const SEASON_NAMES = Object.fromEntries(SEASONS.map((season) => [season.id, season.label]))

export function deckFileName(domingo, season, ano) {
  let label = (domingo || '').trim()
  const seasonName = SEASON_NAMES[season] || (season || '').trim()
  if (label.toLowerCase().startsWith('domingo') && seasonName
      && !label.toLowerCase().includes(seasonName.toLowerCase())) {
    label = `${label} ${seasonName}`
  }
  const year = (ano || '').trim().toUpperCase()
  return `${label} - Ano ${year}.pptx`.replace(/[\\/:*?"<>|]/g, '')
}

export const MOMENTS = [
  { id: 'entrada', label: 'Cântico de entrada' },
  { id: 'kyrie', label: 'Kyrie' },
  { id: 'gloria', label: 'Glória' },
  { id: 'aleluia', label: 'Aleluia' },
  { id: 'dons', label: 'Apresentação dos dons' },
  { id: 'santo', label: 'Santo' },
  { id: 'paiNosso', label: 'Pai Nosso' },
  { id: 'cordeiro', label: 'Cordeiro' },
  { id: 'comunhao', label: 'Cântico de comunhão' },
  { id: 'acaoDeGracas', label: 'Ação de graças' },
  { id: 'final', label: 'Cântico final' },
]

export const ACCENTS = [
  { id: 'accent1', name: 'Verde', season: 'Tempo Comum', color: '#377D49' },
  { id: 'accent2', name: 'Roxo', season: 'Advento e Quaresma', color: '#A461AF' },
  { id: 'accent3', name: 'Rosa', season: 'Domingos Gaudate e Laetere', color: '#F99595' },
  { id: 'accent4', name: 'Dourado', season: 'Natal, Páscoa e solenidades', color: '#8C6A00' },
  { id: 'accent5', name: 'Vermelho', season: 'Ramos e Pentecostes', color: '#B92323' },
  { id: 'accent6', name: 'Azul', season: '', color: '#039BDF' },
]

export const THEME = {
  comum: '#377D49',
  advento: '#A461AF',
  quaresma: '#A461AF',
  rosa: '#F99595',
  natal: '#8C6A00',
  pascoa: '#8C6A00',
  ramos: '#B92323',
  pentecostes: '#B92323',
}

export function comingSunday(today = new Date()) {
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const delta = (7 - date.getDay()) % 7
  date.setDate(date.getDate() + delta)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function occasionFileName(title) {
  const label = (title || '').trim().replace(/[\\/:*?"<>|]/g, '')
  return label ? `${label}.pptx` : 'cânticos.pptx'
}

export const LANGUAGES = [
  { id: 'pt', label: 'Português' },
  { id: 'en', label: 'Inglês' },
  { id: 'es', label: 'Espanhol' },
  { id: 'fr', label: 'Francês' },
  { id: 'la', label: 'Latim' },
]

export function languageName(id) {
  return LANGUAGES.find((item) => item.id === id)?.label || ''
}

export function otherLanguage(taken) {
  return taken === 'la' ? 'pt' : 'la'
}

export function songText(song) {
  return `${song.bookId || ''} ${song.title || ''} ${song.titleAlt || ''}`.trim()
}

/** The language a new pick uses when the deck does not choose one. */
export function preferredLyrics(song) {
  const verses = song?.verses || 0
  const versesAlt = song?.versesAlt || 0
  if (song?.defaultLyrics === 'contentAlt' && versesAlt > 0) {
    return 'contentAlt'
  }
  if (verses > 0) {
    return 'content'
  }
  if (versesAlt > 0) {
    return 'contentAlt'
  }
  return 'content'
}

/** The slide choice stored on a picked song. */
export function slideChoice(song) {
  const choice = {}
  const preferred = preferredLyrics(song)
  const shown = song?.showLyrics || preferred
  if (shown !== preferred) {
    choice.lyrics = shown
  }
  return choice
}

export function withShow(song, previous) {
  const verses = song.verses || 0
  const versesAlt = song.versesAlt || 0
  let showLyrics = preferredLyrics(song)
  if (previous?.showLyrics === 'contentAlt' && versesAlt > 0) {
    showLyrics = 'contentAlt'
  } else if (previous?.showLyrics === 'content' && verses > 0) {
    showLyrics = 'content'
  }
  return { ...song, showLyrics }
}

const MASS_PREFIXES = ['RP', 'G', 'A', 'CR', 'S', 'P', 'C']

export function byBookId(a, b) {
  const left = massBookKey(a.bookId)
  const right = massBookKey(b.bookId)
  if (!left || !right) {
    if (!left && !right) {
      return byTitle(a, b)
    }
    return left ? -1 : 1
  }
  if (left.group !== right.group) {
    return left.group - right.group
  }
  if (left.prefixRank !== right.prefixRank) {
    return left.prefixRank - right.prefixRank
  }
  if (left.prefix !== right.prefix) {
    return left.prefix.localeCompare(right.prefix, 'pt', { sensitivity: 'base' })
  }
  if (left.n !== right.n) {
    return left.n - right.n
  }
  return byTitle(a, b)
}

function massBookKey(bookId) {
  const raw = (bookId || '').trim()
  if (!raw) {
    return null
  }
  const number = /^(\d+)$/.exec(raw)
  if (number) {
    return { group: 0, prefixRank: 0, prefix: '', n: Number(number[1]) }
  }
  const prefixed = /^([A-Za-z]+)(\d*)$/.exec(raw)
  if (!prefixed) {
    return { group: 1, prefixRank: MASS_PREFIXES.length, prefix: raw.toUpperCase(), n: -1 }
  }
  const prefix = prefixed[1].toUpperCase()
  const known = MASS_PREFIXES.indexOf(prefix)
  return {
    group: 1,
    prefixRank: known === -1 ? MASS_PREFIXES.length : known,
    prefix,
    n: prefixed[2] ? Number(prefixed[2]) : -1,
  }
}

function byTitle(a, b) {
  return (a.title || '').localeCompare(b.title || '', 'pt', { sensitivity: 'base' })
}

export function matches(song, query) {
  const needle = fold(query)
  if (!needle) {
    return true
  }
  return fold(song.title).includes(needle)
    || fold(song.titleAlt).includes(needle)
    || fold(song.bookId || '').includes(needle)
}

function fold(value) {
  return (value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
}
