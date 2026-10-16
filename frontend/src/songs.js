export const COVERS = [
  { id: 'Capa - Tempo Comum', label: 'Tempo Comum' },
  { id: 'Capa - Advento', label: 'Advento' },
  { id: 'Capa - Natal', label: 'Natal' },
  { id: 'Capa - Quaresma', label: 'Quaresma' },
  { id: 'Capa - Domingo III Advento', label: 'Domingo III do Advento' },
  { id: 'Capa - Domingo IV Quaresma', label: 'Domingo IV da Quaresma' },
  { id: 'Capa - Ramos', label: 'Ramos' },
  { id: 'Capa - Páscoa', label: 'Páscoa' },
  { id: 'Capa - Pentecostes', label: 'Pentecostes' },
  { id: 'Capa - Solenidade', label: 'Solenidade' },
  { id: 'Capa - Solenidade Pedro Paulo', label: 'Solenidade Pedro e Paulo' },
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

export function occasionFileName(title) {
  const label = (title || '').trim().replace(/[\\/:*?"<>|]/g, '')
  return label ? `${label}.pptx` : 'cânticos.pptx'
}

export function songText(song) {
  return `${song.bookId || ''} ${song.title || ''}`.trim()
}

export function byBookId(a, b) {
  const left = (a.bookId || '').trim()
  const right = (b.bookId || '').trim()
  if (!left || !right) {
    if (left === right) {
      return (a.title || '').localeCompare(b.title || '', 'pt', { sensitivity: 'base' })
    }
    return left ? -1 : 1
  }
  const compared = left.localeCompare(right, 'pt', { numeric: true, sensitivity: 'base' })
  if (compared !== 0) {
    return compared
  }
  return (a.title || '').localeCompare(b.title || '', 'pt', { sensitivity: 'base' })
}

export function matches(song, query) {
  const needle = fold(query)
  if (!needle) {
    return true
  }
  return fold(song.title).includes(needle) || fold(song.bookId || '').includes(needle)
}

function fold(value) {
  return (value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
}
