<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import ChosenSongs from './components/ChosenSongs.vue'
import CoverFields from './components/CoverFields.vue'
import Eucharist from './components/Eucharist.vue'
import SongList from './components/SongList.vue'
import { THEME, deckFileName, occasionFileName } from './songs.js'

const screen = ref('home')
const eucharistMounted = ref(false)
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

watch(screen, (value) => {
  if (value === 'eucharist') {
    eucharistMounted.value = true
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

function onKeydown(event) {
  if (event.key === 'Escape' && savedPath.value) {
    dismissSaved()
  }
}

onMounted(() => {
  loadLibrary()
  loadFonts()
  loadTheme()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

async function loadLibrary() {
  try {
    const response = await fetch('/api/library')
    if (!response.ok) {
      throw new Error(await errorText(response))
    }
    const payload = await response.json()
    indexMissing.value = payload.missing === true
    if (payload.index) {
      indexPath.value = payload.index
      if (!folderEdited.value && payload.directory) {
        folder.value = payload.directory
      }
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
  chosen.value = [...chosen.value, song]
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

async function chooseIndex() {
  error.value = ''
  try {
    const response = await fetch('/api/choose-index', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher o índice.')
    }
    if (!payload.index) {
      return
    }
    indexPath.value = payload.index
    indexMissing.value = false
    if (!folderEdited.value && payload.directory) {
      folder.value = payload.directory
    }
    chosen.value = []
    loadError.value = ''
    await loadSongs()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher o índice.'
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
    ids: chosen.value.map((song) => song.id),
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
    <button type="button" :class="{ current: screen === 'home' }" @click="screen = 'home'">Início</button>
    <button type="button" :class="{ current: screen === 'eucharist' }" @click="screen = 'eucharist'">Eucaristia</button>
    <button type="button" :class="{ current: screen === 'songs' }" @click="screen = 'songs'">Cânticos</button>
    <button type="button" :class="{ current: screen === 'config' }" @click="screen = 'config'">Configuração</button>
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
    <h1>Olá! 👋</h1>
    <p v-if="indexMissing">O índice guardado já não está nesse sítio. Escolha-o outra vez.</p>
    <p>Para começar a gestão de cânticos, é necessária a localização do ficheiro de índice de cânticos.</p>
    <div class="path-row">
      <input type="text" readonly placeholder="Nenhum ficheiro escolhido" />
      <button
        class="browse"
        type="button"
        title="Escolher ficheiro"
        aria-label="Escolher ficheiro"
        @click="chooseIndex"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
        </svg>
      </button>
    </div>
    <p v-if="error" class="status error">{{ error }}</p>
  </main>
  <main v-else-if="indexPath && screen === 'home'" class="welcome home-screen">
    <h1>O que vamos preparar?</h1>
    <div class="choice-row">
      <button class="choice" type="button" @click="screen = 'eucharist'">
        <img src="/logo_comunhao.png" alt="" />
        <strong>Eucaristia</strong>
        <span>Missa de domingo, com um cântico para cada momento.</span>
      </button>
      <button class="choice" type="button" @click="screen = 'songs'">
        <img src="/logo_luz.png" alt="" />
        <strong>Lista de cânticos</strong>
        <span>Uma apresentação só com cânticos, pela ordem escolhida.</span>
      </button>
    </div>
  </main>
  <main v-else-if="indexPath && screen === 'config'" class="page">
    <header>
      <h1>Configuração</h1>
      <p class="lede">O índice de cânticos usado nesta aplicação.</p>
    </header>
    <section class="panel">
      <div class="field">
        <span>Índice</span>
        <div class="path-row">
          <input :value="indexPath" type="text" readonly />
          <button class="browse" type="button" title="Escolher índice" aria-label="Escolher índice" @click="chooseIndex">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
            </svg>
          </button>
        </div>
      </div>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
  </main>
  <Eucharist
    v-if="indexPath && eucharistMounted"
    v-show="screen === 'eucharist'"
    :songs="library"
    :folder="folder"
    @choose-folder="chooseFolder"
    @generated="savedPath = $event"
  />
  <main v-if="indexPath && screen === 'songs'" class="page">
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
      <ChosenSongs :songs="chosen" @remove="remove" @up="move($event, -1)" @down="move($event, 1)" @clear="clearChosen" />
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
