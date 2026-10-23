<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import SongList from './SongList.vue'
import { ACCENTS, COVERS, MOMENTS, SEASONS, comingSunday } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
  folder: { type: String, required: true },
})

const emit = defineEmits(['choose-folder', 'generated'])

const date = ref(comingSunday())
const liturgy = ref(null)
const liturgyError = ref('')
const loading = ref(false)
const form = reactive({
  domingo: '',
  frase: '',
  versiculo: '',
  primeira: '',
  primeiraRef: '',
  segunda: '',
  segundaRef: '',
  salmo: '',
  louvor: '',
})
const useSolemnity = ref(false)
const readingsOpen = ref(false)
const readingsDialog = ref(null)
const cover = ref('')
const accent = ref('accent1')
const accentTouched = ref(false)
const slots = reactive({})
const spoken = reactive({ gloria: false, paiNosso: false })
const active = ref('entrada')
const query = ref('')
const credo = ref('longo')
const oracao = ref('')
const interval = ref('vazio')
const intervalText = ref('')
const intervalImage = ref('')
const output = ref('')
const outputEdited = ref(false)
const outputInput = ref(null)
const error = ref('')
const busy = ref(false)
let liturgyRequest = 0
const missingPrompt = ref([])
const replaceName = ref('')

const rows = computed(() => {
  const list = []
  for (const moment of MOMENTS) {
    if (moment.id === 'gloria' && omitsGloria.value) {
      continue
    }
    if (moment.id === 'aleluia' && lenten.value) {
      list.push({ id: 'louvor', label: 'Louvor a vós', fixed: true })
      continue
    }
    list.push({
      ...moment,
      optional: moment.id === 'gloria' || moment.id === 'paiNosso',
    })
  }
  return list
})

const moments = computed(() => rows.value.filter((moment) => !moment.fixed))

const placedIds = computed(() =>
  Object.values(slots).filter(Boolean).map((song) => song.id))

const intervalReady = computed(() => {
  if (interval.value === 'imagem' || interval.value === 'texto+imagem') {
    if (!intervalImage.value) {
      return false
    }
  }
  if (interval.value === 'texto' || interval.value === 'texto+imagem') {
    if (!intervalText.value.trim()) {
      return false
    }
  }
  return true
})

const seasonLabel = computed(() => {
  const season = chosenCover.value?.season || liturgy.value?.season
  return SEASONS.find((item) => item.id === season)?.label || season || ''
})

const chosenCover = computed(() => COVERS.find((item) => item.id === cover.value) || null)

const omitsGloria = computed(() => (
  chosenCover.value?.season ? !!chosenCover.value.omitsGloria : !!liturgy.value?.omitsGloria
))

const lenten = computed(() => (
  chosenCover.value?.season ? !!chosenCover.value.lenten : !!liturgy.value?.lenten
))

const accentChoice = computed(() => ACCENTS.find((item) => item.id === accent.value) || ACCENTS[0])

const coverLabel = computed(() => COVERS.find((item) => item.id === cover.value)?.label || cover.value)

const liturgyUrl = computed(() => {
  const [year, month, day] = (date.value || '').split('-')
  if (!year || !month || !day) {
    return ''
  }
  return `https://www.liturgia.pt/liturgiadiaria/dia.php?data=${year}-${Number(month)}-${Number(day)}`
})

const canGenerate = computed(() =>
  liturgy.value && !liturgyError.value && output.value.trim() && intervalReady.value && !loading.value)

watch(rows, (list) => {
  if (!list.some((moment) => moment.id === active.value && !moment.fixed)) {
    active.value = list.find((moment) => !moment.fixed)?.id || 'entrada'
  }
})

watch(date, () => {
  loadLiturgy()
}, { immediate: true })

watch(useSolemnity, (on) => {
  const day = liturgy.value
  if (!day?.solemnity) {
    return
  }
  form.domingo = on ? day.solemnity.title : (day.domingo || '')
  if (!outputEdited.value) {
    output.value = (on ? day.solemnity.fileName : day.fileName) || output.value
  }
  cover.value = on ? day.solemnity.cover : (day.cover || '')
  if (!accentTouched.value) {
    showAccent(on ? day.solemnity.accent : day.accent)
  }
  if (on && day.solemnity.outranks) {
    clearReadings()
  } else {
    fillReadings(day)
  }
})

function clearReadings() {
  form.frase = ''
  form.versiculo = ''
  form.primeira = ''
  form.primeiraRef = ''
  form.segunda = ''
  form.segundaRef = ''
  form.salmo = ''
  form.louvor = ''
}

function fillReadings(payload) {
  form.frase = payload.frase || ''
  form.versiculo = payload.versiculo || ''
  form.primeira = payload.primeira?.citacao || ''
  form.primeiraRef = payload.primeira?.referencia || ''
  form.segunda = payload.segunda?.citacao || ''
  form.segundaRef = payload.segunda?.referencia || ''
  form.salmo = payload.salmo || ''
  form.louvor = payload.louvor || ''
}

function storedSentence(value) {
  return !!(value && String(value).trim())
}

function automaticCover() {
  const day = liturgy.value
  if (useSolemnity.value && day?.solemnity?.cover) {
    return day.solemnity.cover
  }
  return day?.cover || ''
}

function sameText(current, original) {
  return (current || '').trim() === (original || '').trim()
}

function shown(value) {
  const text = (value || '').trim()
  return text || '-'
}

const scripturesStored = computed(() => {
  const day = liturgy.value
  if (!day) {
    return false
  }
  if (day.primeira && storedSentence(day.primeira.citacao)) {
    return true
  }
  if (day.segunda && storedSentence(day.segunda.citacao)) {
    return true
  }
  if (storedSentence(day.salmo) || storedSentence(day.frase)) {
    return true
  }
  return !!(day.lenten && storedSentence(day.louvor))
})

const readingsDirty = computed(() => {
  const day = liturgy.value
  if (!day) {
    return false
  }
  if (cover.value !== automaticCover()) {
    return true
  }
  if (day.primeira && (!sameText(form.primeira, day.primeira.citacao) || !sameText(form.primeiraRef, day.primeira.referencia))) {
    return true
  }
  if (day.segunda && (!sameText(form.segunda, day.segunda.citacao) || !sameText(form.segundaRef, day.segunda.referencia))) {
    return true
  }
  if (!sameText(form.salmo, day.salmo) || !sameText(form.frase, day.frase) || !sameText(form.versiculo, day.versiculo)) {
    return true
  }
  return lenten.value && !sameText(form.louvor, day.louvor)
})

function onCoverPick() {
  if (accentTouched.value) {
    return
  }
  const chosen = COVERS.find((item) => item.id === cover.value)
  if (chosen) {
    showAccent(chosen.accent)
  }
}

function revertReadings() {
  const day = liturgy.value
  if (!day) {
    return
  }
  fillReadings(day)
  cover.value = automaticCover()
  if (!accentTouched.value) {
    showAccent(useSolemnity.value && day.solemnity ? day.solemnity.accent : day.accent)
  }
}

watch(readingsOpen, (open) => {
  if (!open) {
    return
  }
  nextTick(() => readingsDialog.value?.focus())
})

function showAccent(id) {
  const swatch = ACCENTS.find((item) => item.id === id)
  if (!swatch) {
    return
  }
  accent.value = swatch.id
  document.documentElement.style.setProperty('--accent', swatch.color)
}

function pickAccent(id) {
  accentTouched.value = true
  showAccent(id)
}

async function loadLiturgy() {
  const requested = date.value
  const token = ++liturgyRequest
  loading.value = true
  accentTouched.value = false
  readingsOpen.value = false
  useSolemnity.value = false
  liturgyError.value = ''
  error.value = ''
  try {
    const response = await fetch('/api/liturgy?date=' + encodeURIComponent(requested))
    const payload = await response.json()
    if (token !== liturgyRequest) {
      return
    }
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível ler a liturgia.')
    }
    liturgy.value = payload
    cover.value = payload.cover || ''
    showAccent(payload.accent)
    form.domingo = payload.domingo || ''
    fillReadings(payload)
    if (!outputEdited.value && payload.fileName) {
      output.value = payload.fileName
    }
    if (payload.solemnity?.outranks) {
      useSolemnity.value = true
    }
  } catch (e) {
    if (token !== liturgyRequest) {
      return
    }
    liturgy.value = null
    liturgyError.value = e instanceof Error ? e.message : 'Não foi possível ler a liturgia.'
  } finally {
    if (token === liturgyRequest) {
      loading.value = false
    }
  }
}

function pick(song) {
  if (!song.bookId || !song.hasVerses || rows.value.find((moment) => moment.id === active.value)?.fixed) {
    return
  }
  if (spoken[active.value]) {
    spoken[active.value] = false
  }
  slots[active.value] = song
}

function unpick(id) {
  for (const key of Object.keys(slots)) {
    if (slots[key]?.id === id) {
      delete slots[key]
    }
  }
}

function clearMoment(id) {
  spoken[id] = false
  delete slots[id]
}

function speak(id, on) {
  spoken[id] = on
  if (on) {
    delete slots[id]
  }
}

async function chooseImage() {
  error.value = ''
  try {
    const response = await fetch('/api/choose-image', { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) {
      throw new Error(payload.error || 'Não foi possível escolher a imagem.')
    }
    if (payload.path) {
      intervalImage.value = payload.path
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Não foi possível escolher a imagem.'
  }
}

function changed(current, original) {
  const next = (current || '').trim()
  const previous = (original || '').trim()
  return next !== previous && next
}

const OPTIONAL_SONGS = new Set(['gloria', 'paiNosso'])

function missingSongLabels() {
  return moments.value
    .filter((moment) => !moment.fixed && !OPTIONAL_SONGS.has(moment.id) && !slots[moment.id])
    .map((moment) => moment.label)
}

function listed(names) {
  if (names.length < 2) {
    return names[0] || ''
  }
  return `${names.slice(0, -1).join(', ')} e ${names[names.length - 1]}`
}

async function pickAnotherName() {
  replaceName.value = ''
  await nextTick()
  outputInput.value?.focus()
  outputInput.value?.select()
}

async function generate(force = false, replace = false) {
  const missing = missingSongLabels()
  if (force !== true && missing.length) {
    missingPrompt.value = missing
    return
  }
  missingPrompt.value = []
  replaceName.value = ''
  error.value = ''
  busy.value = true
  const songs = {}
  for (const moment of moments.value) {
    if (spoken[moment.id]) {
      continue
    }
    const song = slots[moment.id]
    if (song?.bookId) {
      songs[moment.id] = song.bookId
    }
  }
  const day = liturgy.value
  const leituras = {}
  const referencias = {}
  if (changed(form.primeira, day.primeira?.citacao)) {
    leituras.primeira = form.primeira.trim()
  }
  if (day.segunda && changed(form.segunda, day.segunda.citacao)) {
    leituras.segunda = form.segunda.trim()
  }
  if (changed(form.salmo, day.salmo)) {
    leituras.salmo = form.salmo.trim()
  }
  if (changed(form.frase, day.frase)) {
    leituras.evangelho = form.frase.trim()
  }
  if (lenten.value && changed(form.louvor, day.louvor)) {
    leituras.louvor = form.louvor.trim()
  }
  if (day.primeira && changed(form.primeiraRef, day.primeira.referencia)) {
    referencias.primeira = form.primeiraRef.trim()
  }
  if (day.segunda && changed(form.segundaRef, day.segunda.referencia)) {
    referencias.segunda = form.segundaRef.trim()
  }
  const opcoes = { credo: credo.value, interval: interval.value }
  if (oracao.value.trim()) {
    opcoes.oracao = oracao.value.trim()
  }
  if (interval.value === 'imagem' || interval.value === 'texto+imagem') {
    opcoes.intervalImage = intervalImage.value
  }
  if (interval.value === 'texto' || interval.value === 'texto+imagem') {
    opcoes.intervalText = intervalText.value.trim()
  }
  const body = {
    mode: 'mass',
    date: date.value,
    output: output.value.trim(),
    domingo: form.domingo.trim(),
    versiculo: form.versiculo.trim(),
    songs,
    leituras,
    opcoes,
    accent: accent.value,
  }
  if (replace === true) {
    body.replace = true
  }
  if (scripturesStored.value && readingsDirty.value) {
    body.manual = true
  }
  if (cover.value && cover.value !== automaticCover()) {
    body.cover = cover.value
  }
  if (!form.versiculo.trim()) {
    delete body.versiculo
  }
  if (Object.keys(referencias).length) {
    body.referencias = referencias
  }
  if (useSolemnity.value && day?.solemnity) {
    body.solemnity = true
  }
  if (props.folder) {
    body.directory = props.folder
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
    emit('generated', payload.output)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="page">
    <header class="mass-head">
      <div>
        <h1>Eucaristia</h1>
        <p class="lede">Preparar os cânticos e liturgia de uma eucaristia.</p>
      </div>
      <div class="accent-picker">
        <p>Cor de destaque: {{ accentChoice.name.toLowerCase() }}<template v-if="accentChoice.season"> ({{ accentChoice.id === 'accent4' ? accentChoice.season : accentChoice.season.toLowerCase() }})</template></p>
        <div class="swatches">
          <button
            v-for="swatch in ACCENTS"
            :key="swatch.id"
            type="button"
            :class="{ selected: swatch.id === accent }"
            :style="{ background: swatch.color }"
            :title="swatch.season ? `${swatch.name}: ${swatch.season}` : swatch.name"
            :aria-label="swatch.season ? `${swatch.name}, ${swatch.season}` : swatch.name"
            :aria-pressed="swatch.id === accent"
            @click="pickAccent(swatch.id)"
          />
        </div>
      </div>
    </header>

    <section class="panel">
      <div class="occasion">
        <label class="field domingo">
          <span>Data</span>
          <input v-model.lazy="date" type="date" />
        </label>
        <label class="field">
          <span>{{ useSolemnity ? 'Título' : 'Domingo' }}</span>
          <input v-model="form.domingo" type="text" :disabled="!liturgy" />
        </label>
        <label class="field tempo">
          <span>Tempo</span>
          <input :value="seasonLabel" type="text" readonly />
        </label>
        <label class="field ano">
          <span>Ano</span>
          <input :value="liturgy?.ano || ''" type="text" readonly />
        </label>
      </div>
      <p v-if="loading" class="status">A ler a liturgia…</p>
      <p v-else-if="liturgyError" class="status error">{{ liturgyError }}</p>
      <div v-else-if="liturgy?.solemnity" class="solemnity">
        <p>{{ liturgy.solemnity.warning }}</p>
        <label v-if="liturgy.solemnity.outranks != null" class="check">
          <input v-model="useSolemnity" class="switch" type="checkbox" />
          <span>Usar {{ liturgy.solemnity.title }}</span>
        </label>
      </div>
    </section>

    <div class="columns mass-columns">
      <SongList
        heading="Cânticos"
        require-book-id
        :songs="songs"
        :query="query"
        :chosen-ids="placedIds"
        @update:query="query = $event"
        @add="pick"
        @remove-song="unpick"
      />
      <section class="panel">
        <h2>Momentos</h2>
        <ul class="moments">
          <li v-for="moment in rows" :key="moment.id">
            <button
              type="button"
              class="moment"
              :class="{ armed: !moment.fixed && active === moment.id }"
              :disabled="moment.fixed"
              @click="active = moment.id"
            >
              <span class="moment-name" :class="{ waiting: !moment.fixed && !slots[moment.id] && !spoken[moment.id] }">{{ moment.label }}</span>
              <span v-if="moment.fixed" class="moment-song">{{ form.louvor || 'O refrão fica no diapositivo' }}</span>
              <span v-else-if="spoken[moment.id]" class="moment-song">Rezado</span>
              <span v-else-if="slots[moment.id]" class="moment-song picked">
                <span class="book-badge">{{ slots[moment.id].bookId }}</span>
                {{ slots[moment.id].title }}
              </span>
              <span v-else class="missing">por escolher</span>
            </button>
            <button
              v-if="moment.optional && !slots[moment.id] && !spoken[moment.id]"
              type="button"
              class="rezado"
              @click="speak(moment.id, true)"
            >
              Rezado
            </button>
            <button
              v-if="slots[moment.id] || spoken[moment.id]"
              type="button"
              class="icon"
              :aria-label="'Retirar ' + moment.label"
              @click="clearMoment(moment.id)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M6 6l12 12M18 6 6 18" />
              </svg>
            </button>
          </li>
        </ul>
      </section>
    </div>

    <section v-if="liturgy" class="panel cover">
      <div class="panel-head">
        <h2>Capa e leituras</h2>
        <div class="panel-actions">
          <button
            v-if="readingsDirty"
            class="text-action"
            type="button"
            title="Repor as leituras"
            aria-label="Repor as leituras"
            @click="revertReadings"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M3 12a9 9 0 1 0 2.5-6.2" />
              <path d="M3 4v5h5" />
            </svg>
          </button>
          <button class="text-action" type="button" @click="readingsOpen = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
            </svg>
            Editar
          </button>
        </div>
      </div>
      <div v-if="liturgy.warnings?.length" class="warnings">
        <p v-for="(warning, index) in liturgy.warnings" :key="warning">
          {{ warning }}
          <a
            v-if="liturgyUrl && index === liturgy.warnings.length - 1"
            :href="liturgyUrl"
            target="_blank"
            rel="noopener noreferrer"
          >Abrir liturgia.pt para consultar</a>
        </p>
      </div>
      <div class="readings">
        <div class="reading">
          <span>Capa</span>
          <p>{{ coverLabel }}</p>
        </div>
        <div v-if="liturgy.primeira" class="reading">
          <span>Primeira leitura <em v-if="form.primeiraRef">{{ form.primeiraRef }}</em></span>
          <p :class="{ empty: !form.primeira.trim() }">{{ shown(form.primeira) }}</p>
        </div>
        <div class="reading">
          <span>Salmo</span>
          <input v-model="form.salmo" type="text" aria-label="Salmo" />
        </div>
        <div v-if="liturgy.segunda" class="reading">
          <span>Segunda leitura <em v-if="form.segundaRef">{{ form.segundaRef }}</em></span>
          <p :class="{ empty: !form.segunda.trim() }">{{ shown(form.segunda) }}</p>
        </div>
        <div v-if="lenten" class="reading">
          <span>Louvor</span>
          <p :class="{ empty: !form.louvor.trim() }">{{ shown(form.louvor) }}</p>
        </div>
        <div class="reading">
          <span>Evangelho <em v-if="form.versiculo">{{ form.versiculo }}</em></span>
          <p :class="{ empty: !form.frase.trim() }">{{ shown(form.frase) }}</p>
        </div>
      </div>
    </section>

    <section class="panel options">
      <h2>Opções</h2>
      <div class="stack">
        <label class="field">
          <span>Credo</span>
          <select v-model="credo">
            <option value="longo">Credo longo</option>
            <option value="breve">Credo breve</option>
          </select>
        </label>
        <label class="field wide">
          <span>Resposta da oração universal (opcional)</span>
          <input v-model="oracao" type="text" placeholder="Ouvi-nos, Senhor" />
        </label>
        <label class="field">
          <span>Intervalo entre slides</span>
          <select v-model="interval">
            <option value="vazio">Em branco</option>
            <option value="imagem">Imagem</option>
            <option value="texto">Texto</option>
            <option value="texto+imagem">Texto e imagem</option>
          </select>
        </label>
        <label v-if="interval === 'texto' || interval === 'texto+imagem'" class="field wide">
          <span>Texto do intervalo</span>
          <input v-model="intervalText" type="text" />
        </label>
        <div v-if="interval === 'imagem' || interval === 'texto+imagem'" class="field wide">
          <span>Imagem do intervalo</span>
          <div class="path-row">
            <input :value="intervalImage" type="text" readonly placeholder="Nenhuma imagem escolhida" />
            <button class="browse" type="button" title="Escolher imagem" aria-label="Escolher imagem" @click="chooseImage">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5v-10z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="panel export">
      <h2>Exportar</h2>
      <div class="export-fields">
        <label class="field">
          <span>Ficheiro</span>
          <input ref="outputInput" :value="output" type="text" @input="outputEdited = true; output = $event.target.value" />
        </label>
        <div class="field">
          <span>Pasta</span>
          <div class="path-row">
            <input :value="folder" type="text" readonly placeholder="Nenhuma pasta escolhida" />
            <button class="browse" type="button" title="Escolher pasta" aria-label="Escolher pasta" @click="emit('choose-folder')">
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
      <p v-if="error" class="status error">{{ error }}</p>
    </section>

    <div v-if="readingsOpen" class="modal-backdrop" @click.self="readingsOpen = false">
      <div
        ref="readingsDialog"
        class="modal readings-editor"
        role="dialog"
        aria-modal="true"
        aria-labelledby="readings-title"
        tabindex="-1"
        @keydown.esc="readingsOpen = false"
      >
        <button class="icon close" type="button" aria-label="Fechar" @click="readingsOpen = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
        <h2 id="readings-title">Capa e leituras</h2>
        <div class="readings-scroll">
        <p v-if="scripturesStored" class="note">Estas alterações são válidas apenas para este domingo ou celebração e não substituem as ocorrências futuras.</p>
        <p v-else class="note">Ao gerar a apresentação, estas leituras ficam guardadas para a próxima vez que este domingo ou esta celebração se repetir.</p>
        <div class="readings">
          <label class="reading">
            <span>Capa</span>
            <select v-model="cover" aria-label="Capa" @change="onCoverPick">
              <option v-for="item in COVERS" :key="item.id" :value="item.id">{{ item.label }}</option>
            </select>
          </label>
          <div v-if="liturgy?.primeira" class="reading">
            <span>Primeira leitura</span>
            <input v-model="form.primeiraRef" type="text" placeholder="Referência" aria-label="Referência da primeira leitura" />
            <input v-model="form.primeira" type="text" placeholder="Citação" aria-label="Primeira leitura" />
          </div>
          <label class="reading">
            <span>Salmo</span>
            <input v-model="form.salmo" type="text" placeholder="Refrão" aria-label="Salmo" />
          </label>
          <div v-if="liturgy?.segunda" class="reading">
            <span>Segunda leitura</span>
            <input v-model="form.segundaRef" type="text" placeholder="Referência" aria-label="Referência da segunda leitura" />
            <input v-model="form.segunda" type="text" placeholder="Citação" aria-label="Segunda leitura" />
          </div>
          <label v-if="lenten" class="reading">
            <span>Louvor</span>
            <input v-model="form.louvor" type="text" placeholder="Citação" aria-label="Louvor" />
          </label>
          <div class="reading">
            <span>Evangelho</span>
            <input v-model="form.versiculo" type="text" placeholder="Referência" aria-label="Referência do Evangelho" />
            <input v-model="form.frase" type="text" placeholder="Citação" aria-label="Evangelho" />
          </div>
        </div>
        </div>
      </div>
    </div>
    <div v-if="missingPrompt.length" class="modal-backdrop" @click.self="missingPrompt = []">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="missing-songs">
        <p id="missing-songs">Faltam os seguintes cânticos: {{ listed(missingPrompt) }}.</p>
        <p>Quer gerar mesmo assim?</p>
        <div class="open-actions">
          <button type="button" @click="missingPrompt = []">Voltar a editar</button>
          <button class="primary" type="button" @click="generate(true)">Gerar mesmo assim</button>
        </div>
      </div>
    </div>
    <div v-if="replaceName" class="modal-backdrop" @click.self="pickAnotherName">
      <div class="modal confirm" role="dialog" aria-modal="true" aria-labelledby="replace-file">
        <p id="replace-file">Já existe um ficheiro com o nome {{ replaceName }}. Quer substituí-lo?</p>
        <div class="open-actions">
          <button type="button" @click="pickAnotherName">Escolher outro nome</button>
          <button class="primary" type="button" @click="generate(true, true)">Substituir</button>
        </div>
      </div>
    </div>
  </main>
</template>
