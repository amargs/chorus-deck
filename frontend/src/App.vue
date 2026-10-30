<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import ChosenSongs from './components/ChosenSongs.vue'
import CoverFields from './components/CoverFields.vue'
import Eucharist from './components/Eucharist.vue'
import Library from './components/Library.vue'
import SongList from './components/SongList.vue'
import { THEME, deckFileName, occasionFileName, slideChoice, withShow } from './songs.js'

const screen = ref('home')
const lastDeck = ref('')
const songsMounted = ref(false)
const deckMenuHover = ref(false)
const deckMenuPinned = ref(false)
const presentations = ref(null)
const eucharistMounted = ref(false)
const libraryMounted = ref(false)
const lectionaryMounted = ref(false)
const eucharistDraft = ref(null)
const hasEucharistDraft = ref(false)
const draftChecked = ref(false)
const openEucharistWhenReady = ref(false)
const continueDraftButton = ref(null)
const library = ref([])
const chosen = ref([])
const query = ref('')
const coverOn = ref(false)
const cover = reactive({
  kind: 'celebracao',
  season: 'comum',
  ano: 'A',
  domingo: '',
  frase: '',
  versiculo: '',
  layout: 'Capa - Tempo Comum',
  header: '',
})
const output = ref('cânticos.pptx')
const outputEdited = ref(false)
const outputInput = ref(null)
const replaceName = ref('')
const folder = ref('')
const folderEdited = ref(false)
const savedPath = ref('')
const error = ref('')
const busy = ref(false)
const loadError = ref('')
const missingFonts = ref([])
const indexPath = ref('')
const indexMissing = ref(false)
const libraryMode = ref('')
const libraryFolder = ref('')
const libraryNote = ref('')
const libraryBusy = ref(false)
const importDirectory = ref('')
const syncChoice = ref('local')
const draftFolder = ref('')
const libraryReady = ref(false)

const coverReady = computed(() => {
  const text = (value) => value && value.trim()
  if (!text(cover.domingo)) {
    return false
  }
  if (cover.kind === 'outro') {
    return cover.layout && text(cover.header)
  }
  return cover.season && cover.ano
})

const canGenerate = computed(() =>
  chosen.value.length > 0 && output.value.trim() && (!coverOn.value || coverReady.value))

const chosenIds = computed(() => chosen.value.map((song) => song.id))

const savedName = computed(() => {
  const path = savedPath.value
  const name = path.split(/[/\\]/).pop()
  return name || output.value.trim()
})

const draftPrompt = ref(false)
const openingDraft = ref(false)
const deckMenuOpen = computed(() => deckMenuHover.value || deckMenuPinned.value)
const presentationsCurrent = computed(() =>
  screen.value === 'eucharist' || screen.value === 'songs' || screen.value === 'decks')

const draftTitle = computed(() => {
  const name = eucharistDraft.value?.form?.domingo
  return typeof name === 'string' && name.trim() ? name.trim() : 'Eucaristia'
})

watch(draftPrompt, (open) => {
  if (open) {
    nextTick(() => continueDraftButton.value?.focus())
  }
})

watch([coverOn, () => cover.kind, () => cover.season, () => cover.ano, () => cover.domingo], () => {
  if (outputEdited.value) {
    return
  }
  if (!coverOn.value || !cover.domingo.trim()) {
    output.value = 'cânticos.pptx'
    return
  }
  output.value = cover.kind === 'outro'
    ? occasionFileName(cover.domingo)
    : deckFileName(cover.domingo, cover.season, cover.ano)
})

function dismissSaved() {
  savedPath.value = ''
}

function showEucharist() {
  eucharistMounted.value = true
  lastDeck.value = 'eucharist'
  screen.value = 'eucharist'
}

function openEucharist() {
  deckMenuPinned.value = false
  if (openingDraft.value) {
    return
  }
  if (!draftChecked.value) {
    openEucharistWhenReady.value = true
    return
  }
  if (eucharistDraft.value && !eucharistMounted.value) {
    draftPrompt.value = true
    return
  }
  showEucharist()
}

function openSongs() {
  deckMenuPinned.value = false
  songsMounted.value = true
  lastDeck.value = 'songs'
  screen.value = 'songs'
}

function openLibrary() {
  deckMenuPinned.value = false
  libraryMounted.value = true
  screen.value = 'library'
}

function openLectionary() {
  deckMenuPinned.value = false
  lectionaryMounted.value = true
  screen.value = 'lectionary'
}

function openPresentations() {
  deckMenuPinned.value = false
  if (lastDeck.value === 'eucharist') {
    openEucharist()
    return
  }
  if (lastDeck.value === 'songs') {
    openSongs()
    return
  }
  screen.value = 'decks'
}

function goHome() {
  deckMenuPinned.value = false
  screen.value = 'home'
}

function openConfig() {
  deckMenuPinned.value = false
  screen.value = 'config'
}

function toggleDeckMenu() {
  deckMenuPinned.value = !deckMenuPinned.value
}

function onDocumentPointer(event) {
  if (!presentations.value?.contains(event.target)) {
    deckMenuPinned.value = false
  }
}

function continueDraft() {
  draftPrompt.value = false
  openingDraft.value = true
  eucharistMounted.value = true
  lastDeck.value = 'eucharist'
}

function onEucharistRestored() {
  if (!openingDraft.value) {
    return
  }
  openingDraft.value = false
  screen.value = 'eucharist'
}

watch(screen, (value) => {
  if (openingDraft.value && value !== 'home' && value !== 'decks') {
    openingDraft.value = false
  }
})

async function discardDraft() {
  draftPrompt.value = false
  eucharistDraft.value = null
  hasEucharistDraft.value = false
  showEucharist()
  try {
    await fetch('/api/eucharist-draft', { method: 'DELETE' })
  } catch {
    // The fresh screen clears the file again if it is still there.
  }
}

async function loadEucharistDraft() {
  try {
    const response = await fetch('/api/eucharist-draft')
    if (!response.ok) {
      return
    }
    const payload = await response.json()
    if (!payload.date) {
      return
    }
    eucharistDraft.value = payload
    hasEucharistDraft.value = true
  } catch {
    // Home opens without the draft note when the file cannot be read.
  } finally {
    draftChecked.value = true
    if (openEucharistWhenReady.value) {
      openEucharistWhenReady.value = false
      openEucharist()
    }
  }
}

function onEucharistDraft(saved) {
  hasEucharistDraft.value = saved === true
}

function onKeydown(event) {
  if (event.key !== 'Escape') {
    return
  }
  deckMenuPinned.value = false
  if (savedPath.value) {
    dismissSaved()
  }
}

onMounted(() => {
  loadLibrary()
  loadFonts()
  loadTheme()
  loadEucharistDraft()
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('pointerdown', onDocumentPointer)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('pointerdown', onDocumentPointer)
})

async function loadLibrary() {
  try {
    const response = await fetch('/api/library')
    if (!response.ok) {
      throw new Error(await errorText(response))
    }
    const payload = await response.json()
    applyLibrary(payload)
    if (payload.index) {
      await loadSongs()
    }
  } catch (e) {
    loadError.value = e.message
  } finally {
    libraryReady.value = true
  }
}

async function loadSongs() {
  try {
    const response = await fetch('/api/songs')
    if (!response.ok) {
      throw new Error(await errorText(response))
    }
    library.value = await response.json()
    const byId = new Map(library.value.map((song) => [song.id, song]))
    chosen.value = chosen.value.flatMap((song) => {
      const next = byId.get(song.id)
      return next ? [withShow(next, song)] : []
    })
  } catch (e) {
    loadError.value = e.message
  }
}

async function loadFonts() {
  try {
    const response = await fetch('/api/fonts')
    if (!response.ok) {
      return
    }
    const payload = await response.json()
    missingFonts.value = Array.isArray(payload.missing) ? payload.missing : []
  } catch {
    // Generating still checks the fonts when this request fails.
  }
}

async function loadTheme() {
  try {
    const response = await fetch('/api/today')
    if (!response.ok) {
      return
    }
    const today = await response.json()
    const color = THEME[today.season]
    if (color) {
      document.documentElement.style.setProperty('--accent', color)
    }
  } catch {
    // Keep the green theme when the calendar is unreachable.
  }
}

function add(song) {
  if (!song.hasVerses || chosenIds.value.includes(song.id)) {
    return
  }
  chosen.value = [...chosen.value, withShow(song)]
}

function showSong(index, patch) {
  const song = chosen.value[index]
  if (!song) return
  const next = chosen.value.slice()
  next[index] = { ...song, ...patch }
  chosen.value = next
}

function removeSong(id) {
  chosen.value = chosen.value.filter((song) => song.id !== id)
}

function remove(index) {
  chosen.value = chosen.value.filter((_, i) => i !== index)
}

function clearChosen() {
  chosen.value = []
}

function move(index, delta) {
  const next = index + delta
  if (next < 0 || next >= chosen.value.length) {
    return
  }
  const copy = [...chosen.value]
  const [song] = copy.splice(index, 1)
  copy.splice(next, 0, song)
  chosen.value = copy
}

function editOutput(value) {
  outputEdited.value = true
  output.value = value
}

function applyLibrary(payload) {
  indexMissing.value = payload.missing === true
  libraryMode.value = payload.mode || ''
  libraryFolder.value = payload.folder || ''
  syncChoice.value = payload.mode === 'folder' ? 'folder' : 'local'
  draftFolder.value = libraryFolder.value
  applyOutputFolder(payload)
  if (payload.index) {
    indexPath.value = payload.index
    indexMissing.value = false
  }
}

async function useLocalLibrary() {
  error.value = ''
  libraryNote.value = ''
  libraryBusy.value = true
  try {
    const response = await fetch('/api/library', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'local' }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível guardar a biblioteca neste computador.')
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível guardar a biblioteca neste computador.'
  } finally {
    libraryBusy.value = false
  }
}

async function chooseLibraryFolder() {
  error.value = ''
  libraryNote.value = ''
  libraryBusy.value = true
  try {
    const response = await fetch('/api/choose-library-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta.')
    }
    if (payload.cancelled || !payload.index) {
      return
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a pasta.'
  } finally {
    libraryBusy.value = false
  }
}

async function chooseSync(mode) {
  if (libraryBusy.value) {
    return
  }
  if (mode === 'folder') {
    syncChoice.value = 'folder'
    draftFolder.value = libraryFolder.value || draftFolder.value
    return
  }
  syncChoice.value = 'local'
  draftFolder.value = libraryFolder.value
  if (libraryMode.value !== 'local') {
    await useLocalLibrary()
  }
}

async function pickSyncFolder() {
  error.value = ''
  libraryNote.value = ''
  try {
    const response = await fetch('/api/choose-directory', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta.')
    }
    if (payload.directory) {
      draftFolder.value = payload.directory
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a pasta.'
  }
}

async function applySyncFolder() {
  if (!draftFolder.value) {
    return
  }
  error.value = ''
  libraryNote.value = ''
  libraryBusy.value = true
  try {
    const response = await fetch('/api/library', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'folder', directory: draftFolder.value }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível usar essa pasta.')
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível usar essa pasta.'
  } finally {
    libraryBusy.value = false
  }
}

async function chooseImportFolder() {
  error.value = ''
  libraryNote.value = ''
  try {
    const response = await fetch('/api/choose-import-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta.')
    }
    if (payload.directory) {
      importDirectory.value = payload.directory
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a pasta.'
  }
}

function cancelImport() {
  importDirectory.value = ''
}

async function confirmImport() {
  const directory = importDirectory.value
  importDirectory.value = ''
  error.value = ''
  libraryNote.value = ''
  try {
    const response = await fetch('/api/import-library', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ directory }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível importar a biblioteca.')
    }
    applyLibrary(payload)
    chosen.value = []
    loadError.value = ''
    await loadSongs()
    libraryNote.value = 'A biblioteca foi substituída pelos ficheiros dessa pasta.'
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível importar a biblioteca.'
  }
}

async function exportLibrary() {
  error.value = ''
  libraryNote.value = ''
  try {
    const response = await fetch('/api/choose-export-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível exportar a biblioteca.')
    }
    if (payload.directory) {
      libraryNote.value = 'A biblioteca foi copiada para essa pasta.'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível exportar a biblioteca.'
  }
}

function applyOutputFolder(payload) {
  if (!folderEdited.value && payload.outputDirectory) {
    folder.value = payload.outputDirectory
  }
}

async function chooseFolder() {
  folderEdited.value = true
  error.value = ''
  try {
    const response = await fetch('/api/choose-folder', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a pasta.')
    }
    if (payload.directory) {
      folder.value = payload.directory
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a pasta.'
  }
}

async function openSaved(what) {
  error.value = ''
  try {
    const response = await fetch('/api/open', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: savedPath.value, what }),
    })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível abrir.')
    }
  } catch (e) {
    error.value = e.message
  }
}

async function pickAnotherName() {
  replaceName.value = ''
  await nextTick()
  outputInput.value?.focus()
  outputInput.value?.select()
}

async function generate(replace = false) {
  error.value = ''
  savedPath.value = ''
  replaceName.value = ''
  busy.value = true
  const body = {
    ids: chosen.value.map((song) => {
      const choice = slideChoice(song)
      return Object.keys(choice).length ? { id: song.id, ...choice } : song.id
    }),
    output: output.value.trim(),
  }
  if (folder.value) {
    body.directory = folder.value
  }
  if (replace === true) {
    body.replace = true
  }
  if (coverOn.value) {
    if (cover.kind === 'outro') {
      body.cover = {
        kind: 'outro',
        layout: cover.layout,
        domingo: cover.domingo.trim(),
        header: cover.header.trim(),
        frase: cover.frase.trim(),
        versiculo: cover.versiculo.trim(),
      }
    } else {
      body.cover = {
        kind: 'celebracao',
        season: cover.season,
        ano: cover.ano,
        domingo: cover.domingo.trim(),
        frase: cover.frase.trim(),
        versiculo: cover.versiculo.trim(),
      }
    }
  }
  try {
    const response = await fetch('/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const payload = await response.json()
    if (payload.exists) {
      replaceName.value = payload.name || output.value.trim()
      return
    }
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível gerar.')
    }
    savedPath.value = payload.output
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function errorText(response) {
  try {
    const payload = await response.json()
    return payload.error || 'Não foi possível ler os cânticos.'
  } catch {
    return 'Não foi possível ler os cânticos.'
  }
}
</script>

<template>
  <div class="app">
  <nav v-if="indexPath" class="topbar">
    <button type="button" class="nav-link" :class="{ current: screen === 'home' }" :aria-current="screen === 'home' ? 'page' : undefined" @click="goHome">
      <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M4 11.2 12 4l8 7.2" />
        <path stroke-linecap="round" stroke-linejoin="round" d="M7 10.2V20h10v-9.8" />
      </svg>
      <span class="nav-label" data-text="Início"><span>Início</span></span>
    </button>
    <div
      ref="presentations"
      class="nav-split"
      :class="{ current: presentationsCurrent }"
      @mouseenter="deckMenuHover = true"
      @mouseleave="deckMenuHover = false"
    >
      <button
        type="button"
        class="nav-link"
        :aria-current="presentationsCurrent ? 'page' : undefined"
        @click="openPresentations"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <rect x="3.5" y="4" width="17" height="11.5" rx="1.5" />
          <path stroke-linecap="round" d="M8 20h8M12 15.5V20" />
        </svg>
        <span class="nav-label" data-text="Apresentações"><span>Apresentações</span></span>
      </button>
      <button
        type="button"
        class="nav-caret"
        aria-label="Eucaristia e Cânticos"
        aria-haspopup="menu"
        aria-controls="deck-menu"
        :aria-expanded="deckMenuOpen"
        @click="toggleDeckMenu"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="m6 9 6 6 6-6" />
        </svg>
      </button>
      <div v-if="deckMenuOpen" id="deck-menu" class="nav-menu" role="menu">
        <button type="button" role="menuitem" :aria-current="screen === 'eucharist' ? 'page' : undefined" @click="openEucharist">Eucaristia</button>
        <button type="button" role="menuitem" :aria-current="screen === 'songs' ? 'page' : undefined" @click="openSongs">Cânticos</button>
      </div>
    </div>
    <button type="button" class="nav-link" :class="{ current: screen === 'library' }" :aria-current="screen === 'library' ? 'page' : undefined" @click="openLibrary">
      <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <ellipse cx="6.5" cy="18" rx="4" ry="2.7" transform="rotate(-20 6.5 18)" />
        <path stroke-linecap="round" d="M10 16.4V3" />
        <path stroke-linecap="round" d="M13 5h8.2M13 9.6h6M13 14.2h8.2" />
      </svg>
      <span class="nav-label" data-text="Cancioneiro"><span>Cancioneiro</span></span>
    </button>
    <button type="button" class="nav-link" :class="{ current: screen === 'lectionary' }" :aria-current="screen === 'lectionary' ? 'page' : undefined" @click="openLectionary">
      <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
        <path stroke-linecap="round" stroke-linejoin="round" d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
        <path stroke-linecap="round" d="M8.5 7h7M8.5 11h5" />
      </svg>
      <span class="nav-label" data-text="Leccionário"><span>Leccionário</span></span>
    </button>
    <button type="button" class="nav-cog" :class="{ current: screen === 'config' }" aria-label="Configuração" :aria-current="screen === 'config' ? 'page' : undefined" @click="openConfig">
      <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" />
        <circle cx="12" cy="12" r="3" />
      </svg>
    </button>
  </nav>
  <div v-if="missingFonts.length" class="font-banner">
    <p>
      Identificados tipos de letra em falta. A apresentação pode não corresponder ao modelo até os instalar:
      <template v-for="(font, index) in missingFonts" :key="font.family">
        <a :href="font.url" target="_blank" rel="noopener noreferrer">{{ font.family }}</a><span v-if="index < missingFonts.length - 1">, </span>
      </template>.
    </p>
  </div>
  <main v-if="libraryReady && !indexPath" class="welcome">
    <h1>Bem-vindo! 👋</h1>
    <p v-if="indexMissing">A biblioteca guardada já não está nesse sítio.</p>
    <p>O eCoral já vem pré-configurado com um índice de cânticos e da liturgia de alguns Domingos e solenidades. Onde pretende guardar as suas alterações neste índice?</p>
    <div class="choice-row">
      <button class="choice compact" type="button" :disabled="libraryBusy" @click="useLocalLibrary">
        <strong>Só neste computador</strong>
        <span>As alterações ficam persistidas apenas localmente, na pasta pré-definida.</span>
      </button>
      <button class="choice compact" type="button" :disabled="libraryBusy" @click="chooseLibraryFolder">
        <strong>Sincronizar noutra pasta</strong>
        <span>Escolher outra pasta para guardar as alterações (por ex. uma pasta partilhada na Dropbox).</span>
      </button>
    </div>
    <p v-if="error" class="status error">{{ error }}</p>
  </main>
  <main v-else-if="indexPath && (screen === 'home' || screen === 'decks')" class="welcome home-screen">
    <h1 v-if="screen === 'home'">O que vamos preparar?</h1>
    <div class="choice-row" :class="{ 'with-library': screen === 'home' }">
      <button class="choice" type="button" @click="openEucharist">
        <img src="/logo_eucaristia.png" alt="" />
        <span class="choice-name">
          <strong>Eucaristia</strong>
          <span v-if="hasEucharistDraft" class="draft-chip">Rascunho guardado</span>
        </span>
        <span>Missa de domingo, com um cântico para cada momento.</span>
      </button>
      <button class="choice" type="button" @click="openSongs">
        <img src="/logo_canticos.png" alt="" />
        <strong>Lista de cânticos</strong>
        <span>Uma apresentação só com cânticos, pela ordem escolhida.</span>
      </button>
      <button v-if="screen === 'home'" class="choice" type="button" @click="openLibrary">
        <img src="/logo_cancioneiro.png" alt="" />
        <strong>Cancioneiro</strong>
        <span>Adicionar novos cânticos ou editar os existentes.</span>
      </button>
    </div>
  </main>
  <main v-else-if="indexPath && screen === 'config'" class="page">
    <header class="config-header">
      <h1>Configuração</h1>
    </header>
    <h2 class="config-title">Índice de cânticos e Domingos</h2>
    <section class="panel">
      <div class="config-fields">
        <h3 class="config-heading">Sincronização</h3>
        <p class="config-hint">As alterações podem ficar só neste computador, ou numa pasta que partilhe com os outros, por exemplo na Dropbox.</p>
        <div class="kind">
          <label>
            <input type="radio" name="sync" value="local" :checked="syncChoice === 'local'" :disabled="libraryBusy" @change="chooseSync('local')" />
            <span>Local</span>
          </label>
          <label>
            <input type="radio" name="sync" value="folder" :checked="syncChoice === 'folder'" :disabled="libraryBusy" @change="chooseSync('folder')" />
            <span>Outra pasta neste dispositivo</span>
          </label>
        </div>
        <div v-if="syncChoice === 'folder'" class="field">
          <span>Pasta</span>
          <div class="sync-apply">
            <div class="path-row">
              <input :value="draftFolder" type="text" readonly placeholder="Nenhuma pasta escolhida" />
              <button class="browse" type="button" title="Escolher pasta" aria-label="Escolher pasta" :disabled="libraryBusy" @click="pickSyncFolder">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
                </svg>
              </button>
            </div>
            <button class="primary" type="button" :disabled="!draftFolder || libraryBusy" @click="applySyncFolder">Aplicar</button>
          </div>
        </div>
        <h3 class="config-heading">Importar/exportar</h3>
        <p class="config-hint">Importar substitui os índices que estão a ser utilizados por uma cópia de outra pasta. Exportar guarda uma cópia dos índices atuais.</p>
        <div class="config-actions">
          <button type="button" @click="chooseImportFolder">Importar</button>
          <button type="button" @click="exportLibrary">Exportar</button>
        </div>
      </div>
      <p v-if="libraryNote" class="status">{{ libraryNote }}</p>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
    <div v-if="importDirectory" class="modal-backdrop" @click.self="cancelImport">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="import-library">
        <p id="import-library">Substituir os cânticos e a liturgia pelos ficheiros dessa pasta?</p>
        <div class="open-actions">
          <button type="button" @click="cancelImport">Cancelar</button>
          <button class="primary" type="button" @click="confirmImport">Importar</button>
        </div>
      </div>
    </div>
  </main>
  <Library
    v-if="indexPath && libraryMounted"
    v-show="screen === 'library'"
    :songs="library"
    @saved="loadSongs"
  />
  <Library
    v-if="indexPath && lectionaryMounted"
    v-show="screen === 'lectionary'"
    mode="lectionary"
    :songs="library"
  />
  <Eucharist
    v-if="indexPath && eucharistMounted"
    v-show="screen === 'eucharist'"
    :songs="library"
    :folder="folder"
    :draft="eucharistDraft"
    @choose-folder="chooseFolder"
    @generated="savedPath = $event"
    @draft="onEucharistDraft"
    @restored="onEucharistRestored"
  />
  <div v-if="draftPrompt" class="modal-backdrop">
    <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="draft-prompt">
      <p id="draft-prompt">Há um rascunho de {{ draftTitle }} por continuar. Quer retomá-lo?</p>
      <div class="open-actions">
        <button type="button" @click="discardDraft">Começar de novo</button>
        <button ref="continueDraftButton" class="primary" type="button" @click="continueDraft">Continuar</button>
      </div>
    </div>
  </div>
  <main v-if="indexPath && songsMounted" v-show="screen === 'songs'" class="page">
    <header>
      <h1>Cânticos</h1>
      <p class="lede">Criar apresentação com um conjunto de cânticos.</p>
    </header>
    <p v-if="indexPath && loadError" class="status error">{{ loadError }}</p>
    <div v-else-if="indexPath" class="columns">
      <SongList
        :songs="library"
        :query="query"
        :chosen-ids="chosenIds"
        @update:query="query = $event"
        @add="add"
        @remove-song="removeSong"
      />
      <ChosenSongs :songs="chosen" @remove="remove" @up="move($event, -1)" @down="move($event, 1)" @clear="clearChosen" @show="showSong" />
    </div>
    <CoverFields v-if="indexPath" v-model:enabled="coverOn" :cover="cover" />
    <section v-if="indexPath" class="panel export">
      <h2>Exportar</h2>
      <div class="export-fields">
        <label class="field">
          <span>Ficheiro</span>
          <input ref="outputInput" :value="output" type="text" @input="editOutput($event.target.value)" />
        </label>
        <div class="field">
          <span>Pasta</span>
          <div class="path-row">
            <input :value="folder" type="text" readonly placeholder="Nenhuma pasta escolhida" />
            <button
              class="browse"
              type="button"
              title="Escolher pasta"
              aria-label="Escolher pasta"
              @click="chooseFolder"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
              </svg>
            </button>
          </div>
        </div>
        <button class="primary" type="button" :disabled="!canGenerate || busy" @click="generate">
          <span v-if="busy" class="spinner" aria-hidden="true"></span>
          {{ busy ? 'A gerar…' : 'Gerar' }}
        </button>
      </div>
      <p v-if="error && !savedPath" class="status error">{{ error }}</p>
    </section>
    <div v-if="replaceName" class="modal-backdrop" @click.self="pickAnotherName">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="replace-file">
        <p id="replace-file">Já existe um ficheiro com o nome {{ replaceName }}. Quer substituí-lo?</p>
        <div class="open-actions">
          <button type="button" @click="pickAnotherName">Escolher outro nome</button>
          <button class="primary" type="button" @click="generate(true)">Substituir</button>
        </div>
      </div>
    </div>
  </main>
    <div v-if="savedPath" class="modal-backdrop" @click.self="dismissSaved">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="export-done">
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
        <p id="export-done">O ficheiro {{ savedName }} foi criado.</p>
        <div class="open-actions">
          <button type="button" @click="openSaved('folder')">Abrir localização</button>
          <button type="button" @click="openSaved('file')">Abrir ficheiro</button>
        </div>
        <p v-if="error" class="status error">{{ error }}</p>
      </div>
    </div>
  </div>
</template>
