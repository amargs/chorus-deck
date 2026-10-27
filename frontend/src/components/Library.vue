<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import VerseBox from './VerseBox.vue'
import { byBookId, matches } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
})

const emit = defineEmits(['saved'])

const query = ref('')
const editing = ref(false)
const songId = ref(null)
const title = ref('')
const bookId = ref('')
const verses = ref([])
const baseline = ref('')
const busy = ref(false)
const error = ref('')
const savedNotice = ref(false)
const lastVerse = ref(null)
const activeKey = ref(null)
const dragFrom = ref(null)
const dragOver = ref(null)
const boldOn = ref(false)
const italicOn = ref(false)
const underlineOn = ref(false)
let nextKey = 1

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
    bookId: bookId.value,
    verses: verses.value.map((item) => item.markup),
  })
}

function remember() {
  baseline.value = formSnapshot()
}

function startNew() {
  songId.value = null
  title.value = ''
  bookId.value = ''
  verses.value = [verse('')]
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
    bookId.value = payload.bookId || ''
    const content = Array.isArray(payload.content) && payload.content.length ? payload.content : ['']
    verses.value = content.map((markup) => verse(markup))
    editing.value = true
    remember()
  } catch (e) {
    error.value = e.message
  }
}

function closeEditor() {
  editing.value = false
  error.value = ''
  lastVerse.value = null
  activeKey.value = null
}

function insertVerse(index) {
  const next = verses.value.slice()
  next.splice(index + 1, 0, verse(''))
  verses.value = next
  nextTick(() => {
    const boxes = document.querySelectorAll('.catalog-editor .verse-box')
    boxes[index + 1]?.focus()
  })
}

function duplicateVerse(index) {
  const copy = verse(verses.value[index].markup)
  const next = verses.value.slice()
  next.splice(index + 1, 0, copy)
  verses.value = next
}

function moveVerse(index, delta) {
  const to = index + delta
  if (to < 0 || to >= verses.value.length) return
  const next = verses.value.slice()
  const [item] = next.splice(index, 1)
  next.splice(to, 0, item)
  verses.value = next
}

function moveVerseToGap(from, gap) {
  if (from == null || gap == null || gap === from || gap === from + 1) return
  const next = verses.value.slice()
  const [item] = next.splice(from, 1)
  next.splice(gap > from ? gap - 1 : gap, 0, item)
  verses.value = next
}

function gapForRow(index) {
  if (dragFrom.value == null || index === dragFrom.value) return null
  return index > dragFrom.value ? index + 1 : index
}

function showGap(gap) {
  if (dragFrom.value == null || gap == null || gap === dragFrom.value || gap === dragFrom.value + 1) {
    dragOver.value = null
    return
  }
  dragOver.value = gap
}

function onDragStart(index, event) {
  dragFrom.value = index
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(index))
}

function onDragOverRow(index) {
  showGap(gapForRow(index))
}

function onDropRow(index) {
  moveVerseToGap(dragFrom.value, gapForRow(index))
  dragFrom.value = null
  dragOver.value = null
}

function onDragOverGap(gap) {
  showGap(gap)
}

function onDropGap(gap) {
  moveVerseToGap(dragFrom.value, gap)
  dragFrom.value = null
  dragOver.value = null
}

function onDragEnd() {
  dragFrom.value = null
  dragOver.value = null
}

function removeVerse(index) {
  verses.value = verses.value.filter((_, i) => i !== index)
}

function onVerseFocus(key, el) {
  activeKey.value = key
  lastVerse.value = el
  syncFormatState()
}

function onVerseBlur(key, event) {
  const next = event.relatedTarget
  if (next && event.currentTarget.contains(next)) return
  if (activeKey.value === key) activeKey.value = null
}

function onPointerDown(event) {
  const field = event.target.closest?.('.verse-field')
  activeKey.value = field ? Number(field.dataset.key) : null
}

function dismissSaved() {
  savedNotice.value = false
}

function onKeydown(event) {
  if (event.key === 'Escape' && savedNotice.value) dismissSaved()
}

function format(command) {
  const box = verseBoxFromSelection() || lastVerse.value
  if (!box) return
  box.focus()
  document.execCommand(command)
  box.dispatchEvent(new Event('input', { bubbles: true }))
  syncFormatState()
}

function verseBoxFromSelection() {
  const node = document.getSelection()?.anchorNode
  const el = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement
  return el?.closest?.('.verse-box') || null
}

function syncFormatState() {
  const box = verseBoxFromSelection()
  if (!box) return
  boldOn.value = document.queryCommandState('bold')
  italicOn.value = document.queryCommandState('italic')
  underlineOn.value = document.queryCommandState('underline')
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
    savedNotice.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  document.addEventListener('selectionchange', syncFormatState)
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  document.removeEventListener('selectionchange', syncFormatState)
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <main class="page" :class="{ 'catalog-list': !editing }">
    <header class="catalog-head">
      <h1>Biblioteca</h1>
      <button v-if="editing" type="button" class="text-action" @click="closeEditor">
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
      </div>
      <div class="field">
        <span>Estrofes e refrões</span>
        <div
          class="verse-insert verse-gap"
          :class="{ 'verse-drop': dragOver === 0 }"
          @dragover.prevent.capture="onDragOverGap(0)"
          @drop.prevent="onDropGap(0)"
        ></div>
        <template v-for="(item, index) in verses" :key="item.key">
          <div
            class="verse-row"
            :class="{ 'verse-dragging': dragFrom === index }"
            @dragover.prevent.capture="onDragOverRow(index)"
            @drop.prevent="onDropRow(index)"
          >
            <div class="verse-move">
              <button
                type="button"
                class="icon nudge"
                aria-label="Mover estrofe para cima"
                :disabled="index === 0"
                @click="moveVerse(index, -1)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m6 14 6-6 6 6" />
                </svg>
              </button>
              <button
                type="button"
                class="icon drag-handle"
                draggable="true"
                aria-label="Arrastar estrofe"
                @dragstart="onDragStart(index, $event)"
                @dragend="onDragEnd"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="9" cy="7" r="1.3" fill="currentColor" />
                  <circle cx="15" cy="7" r="1.3" fill="currentColor" />
                  <circle cx="9" cy="12" r="1.3" fill="currentColor" />
                  <circle cx="15" cy="12" r="1.3" fill="currentColor" />
                  <circle cx="9" cy="17" r="1.3" fill="currentColor" />
                  <circle cx="15" cy="17" r="1.3" fill="currentColor" />
                </svg>
              </button>
              <button
                type="button"
                class="icon nudge"
                aria-label="Mover estrofe para baixo"
                :disabled="index === verses.length - 1"
                @click="moveVerse(index, 1)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" d="m6 10 6 6 6-6" />
                </svg>
              </button>
            </div>
            <div
              class="verse-field"
              :data-key="item.key"
              :class="{ 'verse-focused': activeKey === item.key }"
              @focusout="onVerseBlur(item.key, $event)"
            >
              <div v-if="activeKey === item.key" class="format-bar">
                <button type="button" class="format" :aria-pressed="boldOn" aria-label="Negrito" @mousedown.prevent="format('bold')">
                  <strong>B</strong>
                </button>
                <button type="button" class="format" :aria-pressed="italicOn" aria-label="Itálico" @mousedown.prevent="format('italic')">
                  <em>I</em>
                </button>
                <button type="button" class="format" :aria-pressed="underlineOn" aria-label="Sublinhado" @mousedown.prevent="format('underline')">
                  <span class="format-u">U</span>
                </button>
              </div>
              <VerseBox
                v-model="item.markup"
                :label="`Estrofe ${index + 1}`"
                @focus="onVerseFocus(item.key, $event)"
              />
            </div>
            <div class="verse-actions">
              <button type="button" class="icon" aria-label="Duplicar estrofe" @click="duplicateVerse(index)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <rect x="8" y="8" width="10" height="10" rx="1.5" />
                  <path stroke-linecap="round" d="M6 16V6.5A1.5 1.5 0 0 1 7.5 5H16" />
                </svg>
              </button>
              <button type="button" class="icon" aria-label="Retirar estrofe" @click="removeVerse(index)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path stroke-linecap="round" d="M6 6l12 12M18 6 6 18" />
                </svg>
              </button>
            </div>
          </div>
          <div
            class="verse-insert"
            :class="{ 'verse-drop': dragOver === index + 1 }"
            @dragover.prevent.capture="onDragOverGap(index + 1)"
            @drop.prevent="onDropGap(index + 1)"
          >
            <button type="button" class="verse-plus" aria-label="Inserir estrofe" @click="insertVerse(index)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true">
                <path stroke-linecap="round" d="M12 6v12M6 12h12" />
              </svg>
            </button>
          </div>
        </template>
        <div v-if="verses.length === 0" class="verse-insert">
          <button type="button" class="verse-plus" aria-label="Inserir estrofe" @click="insertVerse(-1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true">
              <path stroke-linecap="round" d="M12 6v12M6 12h12" />
            </svg>
          </button>
        </div>
      </div>
      <div class="catalog-actions">
        <button type="button" :disabled="busy" @click="closeEditor">Cancelar</button>
        <button class="primary" type="button" :disabled="!canSave" @click="save">
          {{ busy ? 'A guardar…' : 'Guardar' }}
        </button>
      </div>
      <p v-if="error" class="status error">{{ error }}</p>
    </section>
    <section v-else class="panel">
      <div class="panel-head">
        <h2>Cânticos</h2>
        <button class="primary" type="button" @click="startNew">Novo cântico</button>
      </div>
      <input v-model="query" class="search" type="search" placeholder="Título ou número" />
      <ul class="songs">
        <li v-for="song in visible" :key="song.id">
          <button type="button" class="catalog-song" @click="openSong(song)">
            <span class="song-label">
              <span class="book-badge">{{ song.bookId || '#' }}</span>
              {{ song.title }}
            </span>
            <span v-if="!song.hasVerses" class="missing">sem letra</span>
            <span v-else-if="!song.bookId" class="missing">sem número</span>
          </button>
        </li>
      </ul>
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
        <p id="song-saved">Cântico guardado.</p>
        <div class="open-actions">
          <button class="primary" type="button" @click="dismissSaved">Fechar</button>
        </div>
      </div>
    </div>
  </main>
</template>
