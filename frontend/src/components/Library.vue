<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import VerseList from './VerseList.vue'
import { SEASONS, byBookId, languageName, matches, otherLanguage } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
})

const emit = defineEmits(['saved'])

const query = ref('')
const editing = ref(false)
const songId = ref(null)
const title = ref('')
const titleAlt = ref('')
const bookId = ref('')
const verses = ref([])
const altVerses = ref([])
const secondLanguage = ref(false)
const lang = ref('pt')
const langAlt = ref('la')
const defaultLyrics = ref('content')
const baseline = ref('')
const busy = ref(false)
const error = ref('')
const savedNotice = ref(false)
const section = ref('songs')
const sundays = ref([])
const sundayQuery = ref('')
const sundayOrder = ref('liturgical')
const sundayFill = ref('all')
const sundayKind = ref('all')
const years = reactive({ A: true, B: true, C: true })
const sunday = ref(null)
const primeira = ref('')
const segunda = ref('')
const salmo = ref('')
const evangelho = ref('')
const louvor = ref('')
const sundayBaseline = ref('')
const savedMessage = ref('')
let nextKey = 1

const inEditor = computed(() => editing.value || sunday.value)

const seasonLabel = computed(() => {
  const id = sunday.value?.season
  return SEASONS.find((item) => item.id === id)?.label || id || ''
})

const sundayDirty = computed(() => sunday.value && sundaySnapshot() !== sundayBaseline.value)

const canSaveSunday = computed(() => sundayDirty.value && !busy.value)

const SEASON_ORDER = ['advento', 'natal', 'quaresma', 'ramos', 'pascoa', 'pentecostes', 'comum']

const visibleSundays = computed(() => {
  const q = sundayQuery.value.trim().toLocaleLowerCase('pt')
  const anyYear = years.A || years.B || years.C
  const rows = sundays.value.filter((day) => {
    if (day.ano) {
      if (!years[day.ano]) return false
    } else if (!anyYear) {
      return false
    }
    if (sundayKind.value === 'sunday' && day.solemnity) return false
    if (sundayKind.value === 'solemnity' && !day.solemnity) return false
    if (sundayFill.value === 'empty' && !day.incomplete) return false
    if (sundayFill.value === 'filled' && day.incomplete) return false
    if (q && !(day.name || '').toLocaleLowerCase('pt').includes(q)) return false
    return true
  })
  const copy = rows.slice()
  if (sundayOrder.value === 'name') {
    copy.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'pt'))
  } else if (sundayOrder.value === 'season') {
    copy.sort((a, b) => {
      const left = SEASON_ORDER.indexOf(a.season)
      const right = SEASON_ORDER.indexOf(b.season)
      const bySeason = (left < 0 ? 99 : left) - (right < 0 ? 99 : right)
      return bySeason || liturgicalKey(a) - liturgicalKey(b)
    })
  } else {
    copy.sort((a, b) => liturgicalKey(a) - liturgicalKey(b))
  }
  return copy
})

function liturgicalKey(day) {
  const cycle = day.ano || (years.A ? 'A' : years.B ? 'B' : 'C')
  const rank = cycle === 'B' ? 1 : cycle === 'C' ? 2 : 0
  return rank * 1000 + (Number(day.place) || 0)
}

const visible = computed(() =>
  props.songs.filter((song) => matches(song, query.value)).sort(byBookId))

const dirty = computed(() => formSnapshot() !== baseline.value)

const canSave = computed(() => dirty.value && title.value.trim() && !busy.value)

function verse(markup) {
  return { key: nextKey++, markup }
}

function formSnapshot() {
  return JSON.stringify({
    title: title.value,
    titleAlt: titleAlt.value,
    bookId: bookId.value,
    verses: verses.value.map((item) => item.markup),
    secondLanguage: secondLanguage.value,
    lang: secondLanguage.value ? lang.value : '',
    langAlt: secondLanguage.value ? langAlt.value : '',
    defaultLyrics: secondLanguage.value ? defaultLyrics.value : 'content',
    altVerses: secondLanguage.value ? altVerses.value.map((item) => item.markup) : [],
  })
}

function remember() {
  baseline.value = formSnapshot()
}

function sundaySnapshot() {
  const body = {
    primeira: primeira.value,
    segunda: segunda.value,
    salmo: salmo.value,
    evangelho: evangelho.value,
  }
  if (sunday.value?.lenten) body.louvor = louvor.value
  return JSON.stringify(body)
}

function rememberSunday() {
  sundayBaseline.value = sundaySnapshot()
}

function seasonName(id) {
  return SEASONS.find((item) => item.id === id)?.label || id || ''
}

async function loadSundays() {
  const response = await fetch('/api/sundays')
  const payload = await response.json()
  if (!response.ok) {
    throw new Error(payload.error || 'Não foi possível ler os domingos.')
  }
  sundays.value = payload
}

function showSunday(payload) {
  sunday.value = payload
  primeira.value = payload.primeira || ''
  segunda.value = payload.segunda || ''
  salmo.value = payload.salmo || ''
  evangelho.value = payload.evangelho || ''
  louvor.value = payload.louvor || ''
  error.value = ''
  savedNotice.value = false
  rememberSunday()
}

function openSunday(day) {
  if (!day || busy.value) return
  showSunday(day)
}

function closeSunday() {
  sunday.value = null
  error.value = ''
  loadSundays().catch((e) => {
    error.value = e.message
  })
}

function leaveEditor() {
  if (editing.value) closeEditor()
  else closeSunday()
}

async function saveSunday() {
  if (!canSaveSunday.value || !sunday.value) return
  error.value = ''
  savedNotice.value = false
  busy.value = true
  const body = {
    key: sunday.value.key,
    primeira: primeira.value.trim(),
    segunda: segunda.value.trim(),
    salmo: salmo.value.trim(),
    evangelho: evangelho.value.trim(),
  }
  if (sunday.value.lenten) body.louvor = louvor.value.trim()
  try {
    const response = await fetch('/api/sundays', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível guardar o domingo.')
    }
    const index = sundays.value.findIndex((row) => row.key === payload.key)
    if (index >= 0) sundays.value[index] = payload
    sunday.value = null
    savedMessage.value = 'Domingo guardado.'
    savedNotice.value = true
    await loadSundays()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function startNew() {
  songId.value = null
  title.value = ''
  titleAlt.value = ''
  bookId.value = ''
  verses.value = [verse('')]
  altVerses.value = []
  secondLanguage.value = false
  lang.value = 'pt'
  langAlt.value = 'la'
  defaultLyrics.value = 'content'
  error.value = ''
  savedNotice.value = false
  editing.value = true
  remember()
}

async function openSong(song) {
  error.value = ''
  savedNotice.value = false
  try {
    const response = await fetch(`/api/songs/${song.id}`)
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível abrir o cântico.')
    }
    songId.value = payload.id
    title.value = payload.title || ''
    titleAlt.value = payload.titleAlt || ''
    bookId.value = payload.bookId || ''
    const content = Array.isArray(payload.content) && payload.content.length ? payload.content : ['']
    const alt = Array.isArray(payload.contentAlt) ? payload.contentAlt : []
    const hasAlt = alt.some((line) => String(line).trim())
    if (hasAlt) {
      const aligned = alignLanguages(content, alt)
      verses.value = aligned.primary
      altVerses.value = aligned.secondary
      secondLanguage.value = true
      lang.value = payload.lang || 'pt'
      langAlt.value = payload.langAlt && payload.langAlt !== lang.value
        ? payload.langAlt
        : otherLanguage(lang.value)
      defaultLyrics.value = payload.defaultLyrics === 'contentAlt' ? 'contentAlt' : 'content'
    } else {
      verses.value = content.map((markup) => verse(markup))
      altVerses.value = []
      secondLanguage.value = false
      lang.value = 'pt'
      langAlt.value = 'la'
      defaultLyrics.value = 'content'
    }
    editing.value = true
    remember()
  } catch (e) {
    error.value = e.message
  }
}

function alignLanguages(primaryLines, secondaryLines) {
  const primary = primaryLines.length ? primaryLines : ['']
  const secondary = secondaryLines.slice()
  const count = Math.max(primary.length, secondary.length)
  const primaryRows = []
  const secondaryRows = []
  for (let i = 0; i < count; i++) {
    primaryRows.push(verse(primary[i] ?? ''))
    secondaryRows.push(verse(secondary[i] ?? primary[i] ?? ''))
  }
  return { primary: primaryRows, secondary: secondaryRows }
}

function setSecondLanguage(on) {
  if (on) {
    if (!lang.value) lang.value = 'pt'
    if (!langAlt.value || langAlt.value === lang.value) langAlt.value = otherLanguage(lang.value)
    const aligned = alignLanguages(
      verses.value.map((item) => item.markup),
      altVerses.value.map((item) => item.markup),
    )
    verses.value = aligned.primary
    altVerses.value = aligned.secondary
    if (defaultLyrics.value !== 'contentAlt') defaultLyrics.value = 'content'
  } else {
    defaultLyrics.value = 'content'
  }
  secondLanguage.value = on
}

function closeEditor() {
  editing.value = false
  error.value = ''
}

function dismissSaved() {
  savedNotice.value = false
}

function onKeydown(event) {
  if (event.key === 'Escape' && savedNotice.value) dismissSaved()
}

async function save() {
  if (!canSave.value) return
  error.value = ''
  savedNotice.value = false
  busy.value = true
  const body = {
    title: title.value,
    content: verses.value.map((item) => item.markup),
  }
  const altTitle = titleAlt.value.trim()
  if (altTitle) {
    body.titleAlt = altTitle
  }
  const alt = secondLanguage.value ? altVerses.value.map((item) => item.markup) : []
  if (alt.some((line) => line.trim())) {
    body.contentAlt = alt
    body.lang = lang.value
    body.langAlt = langAlt.value
    if (defaultLyrics.value === 'contentAlt') body.defaultLyrics = 'contentAlt'
  }
  const number = bookId.value.trim()
  if (number) {
    body.bookId = number
  }
  if (songId.value != null) {
    body.id = songId.value
  }
  try {
    const response = await fetch('/api/songs', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível guardar o cântico.')
    }
    emit('saved')
    editing.value = false
    savedMessage.value = 'Cântico guardado.'
    savedNotice.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

watch(section, (value) => {
  if (value !== 'sundays' || sunday.value) return
  error.value = ''
  loadSundays().catch((e) => {
    error.value = e.message
  })
})

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <main class="page" :class="{ 'catalog-list': !inEditor }">
    <header class="catalog-head">
      <h1>Biblioteca</h1>
      <button v-if="inEditor" type="button" class="text-action" @click="leaveEditor">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 6 9 12l6 6" />
        </svg>
        Voltar
      </button>
    </header>
    <section v-if="editing" class="panel catalog-editor">
      <h2>{{ songId == null ? 'Novo cântico' : 'Editar cântico' }}</h2>
      <div class="catalog-fields">
        <label class="field">
          <span>Título</span>
          <input v-model="title" type="text" />
        </label>
        <label class="field">
          <span>Número do cancioneiro</span>
          <input v-model="bookId" type="text" placeholder="118, A1, RP3" />
        </label>
        <label class="field title-alt-field">
          <span>Título alternativo (opcional)</span>
          <input v-model="titleAlt" type="text" />
        </label>
      </div>
      <div class="field verses-field">
        <span>Estrofes e refrões</span>
        <div class="verse-language">
          <label class="cover-toggle">
            <input
              class="switch small"
              type="checkbox"
              :checked="secondLanguage"
              @change="setSecondLanguage($event.target.checked)"
            />
            Língua secundária
          </label>
          <div v-if="secondLanguage" class="default-language">
            <span id="default-language-label">Língua por omissão</span>
            <div class="default-choices" role="radiogroup" aria-labelledby="default-language-label">
              <label>
                <input v-model="defaultLyrics" type="radio" value="content" />
                {{ languageName(lang) }}
              </label>
              <label>
                <input v-model="defaultLyrics" type="radio" value="contentAlt" />
                {{ languageName(langAlt) }}
              </label>
            </div>
          </div>
        </div>
        <VerseList
          v-model="verses"
          :alt="secondLanguage ? altVerses : null"
          :lang="lang"
          :lang-alt="langAlt"
          @update:alt="altVerses = $event"
          @update:lang="lang = $event"
          @update:lang-alt="langAlt = $event"
        />
      </div>
      <div class="catalog-actions">
        <button type="button" :disabled="busy" @click="closeEditor">Cancelar</button>
        <button class="primary" type="button" :disabled="!canSave" @click="save">
          {{ busy ? 'A guardar…' : 'Guardar' }}
        </button>
      </div>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
    <section v-else-if="sunday" class="panel catalog-editor">
      <h2>Frases</h2>
      <p class="sunday-title">{{ sunday.name }}<template v-if="sunday.ano"> · Ano {{ sunday.ano }}</template></p>
      <p class="sunday-note">
        <template v-if="seasonLabel">{{ seasonLabel }}. </template>
        <template v-if="sunday.ano">Estas frases ficam neste domingo do ano {{ sunday.ano }}, para qualquer data em que ele calhe.</template>
        <template v-else>Estas frases ficam nesta solenidade, para qualquer ano.</template>
        A próxima Eucaristia lê-as, salvo se as substituir só para essa apresentação.
      </p>
      <div class="readings">
        <label class="reading">
          <span>Primeira leitura <em v-if="sunday.primeiraRef">{{ sunday.primeiraRef }}</em></span>
          <input v-model="primeira" type="text" aria-label="Primeira leitura" />
        </label>
        <label class="reading">
          <span>Segunda leitura <em v-if="sunday.segundaRef">{{ sunday.segundaRef }}</em></span>
          <input v-model="segunda" type="text" aria-label="Segunda leitura" />
        </label>
        <label class="reading">
          <span>Salmo</span>
          <input v-model="salmo" type="text" aria-label="Salmo" />
        </label>
        <label v-if="sunday.lenten" class="reading">
          <span>Louvor</span>
          <input v-model="louvor" type="text" aria-label="Louvor" />
        </label>
        <label class="reading">
          <span>Evangelho <em v-if="sunday.evangelhoRef">{{ sunday.evangelhoRef }}</em></span>
          <input v-model="evangelho" type="text" aria-label="Evangelho" />
        </label>
      </div>
      <div class="catalog-actions">
        <button type="button" :disabled="busy" @click="closeSunday">Cancelar</button>
        <button class="primary" type="button" :disabled="!canSaveSunday" @click="saveSunday">
          {{ busy ? 'A guardar…' : 'Guardar' }}
        </button>
      </div>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
    <section v-else class="panel">
      <div class="panel-head">
        <div class="kind catalog-lists" role="radiogroup" aria-label="Listas da biblioteca">
          <label>
            <input v-model="section" type="radio" name="library-list" value="songs" />
            <span>Cânticos</span>
          </label>
          <label>
            <input v-model="section" type="radio" name="library-list" value="sundays" />
            <span>Liturgia</span>
          </label>
        </div>
        <button v-if="section === 'songs'" class="primary" type="button" @click="startNew">Novo cântico</button>
      </div>
      <template v-if="section === 'songs'">
        <input v-model="query" class="search" type="search" placeholder="Título ou número" />
        <ul class="songs">
          <li v-for="song in visible" :key="song.id">
            <button type="button" class="catalog-song" @click="openSong(song)">
              <span class="song-label">
                <span class="book-badge">{{ song.bookId || '#' }}</span>
                <span class="song-name">
                  {{ song.title }}
                  <span class="title-alt">{{ song.titleAlt ? `(${song.titleAlt})` : '' }}</span>
                </span>
              </span>
              <span v-if="!song.hasVerses" class="missing">sem letra</span>
              <span v-else-if="!song.bookId" class="missing">sem número</span>
            </button>
          </li>
        </ul>
      </template>
      <template v-else>
        <div class="sunday-filters">
          <input v-model="sundayQuery" class="search" type="search" placeholder="Nome do domingo ou solenidade" />
          <div class="year-toggles" role="group" aria-label="Anos">
            <button type="button" :aria-pressed="years.A" @click="years.A = !years.A">Ano A</button>
            <button type="button" :aria-pressed="years.B" @click="years.B = !years.B">Ano B</button>
            <button type="button" :aria-pressed="years.C" @click="years.C = !years.C">Ano C</button>
          </div>
          <label>
            <span>Ordem</span>
            <span class="select-size">
              <select v-model="sundayOrder" aria-label="Ordem">
                <option value="liturgical">Ano litúrgico</option>
                <option value="name">Nome</option>
                <option value="season">Tempo</option>
              </select>
              <span aria-hidden="true">Ano litúrgico</span>
            </span>
          </label>
          <label>
            <span>Leituras</span>
            <span class="select-size">
              <select v-model="sundayFill" aria-label="Leituras">
                <option value="all">Todas</option>
                <option value="empty">Por preencher</option>
                <option value="filled">Preenchidas</option>
              </select>
              <span aria-hidden="true">Por preencher</span>
            </span>
          </label>
          <label>
            <span>Tipo</span>
            <span class="select-size">
              <select v-model="sundayKind" aria-label="Tipo">
                <option value="all">Tudo</option>
                <option value="sunday">Domingos</option>
                <option value="solemnity">Solenidades</option>
              </select>
              <span aria-hidden="true">Solenidades</span>
            </span>
          </label>
        </div>
        <ul class="songs">
          <li v-for="day in visibleSundays" :key="day.key">
            <button type="button" class="catalog-song" @click="openSunday(day)">
              <span class="sunday-label">
                <span>{{ day.name }}</span>
                <span class="sunday-meta">{{ seasonName(day.season) }}<template v-if="day.ano"> · Ano {{ day.ano }}</template></span>
              </span>
              <span v-if="day.solemnity" class="solemnity-badge">Solenidade</span>
              <span v-if="day.incomplete" class="missing">por preencher</span>
            </button>
          </li>
        </ul>
        <p v-if="!visibleSundays.length && !error" class="sunday-note">Nenhum domingo com estes filtros.</p>
      </template>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
    <div v-if="savedNotice" class="modal-backdrop" @click.self="dismissSaved">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="song-saved">
        <button class="icon close" type="button" aria-label="Fechar" @click="dismissSaved">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
        <div class="done-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="9" />
            <path d="m8 12.2 2.6 2.6L16.2 9.2" />
          </svg>
        </div>
        <p id="song-saved">{{ savedMessage }}</p>
        <div class="open-actions">
          <button class="primary" type="button" @click="dismissSaved">Fechar</button>
        </div>
      </div>
    </div>
  </main>
</template>
