<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import VerseBox from './VerseBox.vue'
import { LANGUAGES, languageName } from '../songs.js'

const props = defineProps({
  modelValue: { type: Array, required: true },
  alt: { type: Array, default: null },
  lang: { type: String, default: 'pt' },
  langAlt: { type: String, default: 'la' },
})

const emit = defineEmits(['update:modelValue', 'update:alt', 'update:lang', 'update:langAlt'])

const root = ref(null)
const lastVerse = ref(null)
const activeField = ref(null)
const dragFrom = ref(null)
const dragOver = ref(null)
const boldOn = ref(false)
const italicOn = ref(false)
const underlineOn = ref(false)

const paired = computed(() => Array.isArray(props.alt))

const columns = computed(() => {
  const cols = [{ id: 'a', label: languageName(props.lang) || 'Português', items: props.modelValue }]
  if (paired.value) {
    cols.push({ id: 'b', label: languageName(props.langAlt) || 'Latim', items: props.alt })
  }
  return cols
})

function replace(verses, alt) {
  emit('update:modelValue', verses)
  if (alt) emit('update:alt', alt)
}

function nextKey(list) {
  return list.reduce((n, item) => Math.max(n, Number(item.key) || 0), 0) + 1
}

function blank(list) {
  return { key: nextKey(list), markup: '' }
}

function insertVerse(index) {
  const verses = props.modelValue.slice()
  verses.splice(index + 1, 0, blank(props.modelValue))
  let alt = null
  if (paired.value) {
    alt = props.alt.slice()
    alt.splice(index + 1, 0, blank(props.alt))
  }
  replace(verses, alt)
  nextTick(() => {
    root.value?.querySelectorAll('.verse-row .verse-box')[paired.value ? (index + 1) * 2 : index + 1]?.focus()
  })
}

function duplicateVerse(index) {
  const verses = props.modelValue.slice()
  const created = blank(props.modelValue)
  created.markup = props.modelValue[index].markup
  verses.splice(index + 1, 0, created)
  let alt = null
  if (paired.value) {
    alt = props.alt.slice()
    const copy = blank(props.alt)
    copy.markup = props.alt[index].markup
    alt.splice(index + 1, 0, copy)
  }
  replace(verses, alt)
}

function moveVerse(index, delta) {
  const to = index + delta
  if (to < 0 || to >= props.modelValue.length) return
  const verses = props.modelValue.slice()
  const [item] = verses.splice(index, 1)
  verses.splice(to, 0, item)
  let alt = null
  if (paired.value) {
    alt = props.alt.slice()
    const [other] = alt.splice(index, 1)
    alt.splice(to, 0, other)
  }
  replace(verses, alt)
}

function moveVerseToGap(from, gap) {
  if (from == null || gap == null || gap === from || gap === from + 1) return
  const verses = props.modelValue.slice()
  const [item] = verses.splice(from, 1)
  const to = gap > from ? gap - 1 : gap
  verses.splice(to, 0, item)
  let alt = null
  if (paired.value) {
    alt = props.alt.slice()
    const [other] = alt.splice(from, 1)
    alt.splice(to, 0, other)
  }
  replace(verses, alt)
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
  const verses = props.modelValue.filter((_, i) => i !== index)
  const alt = paired.value ? props.alt.filter((_, i) => i !== index) : null
  replace(verses, alt)
}

function fieldId(column, item) {
  return `${column}-${item.key}`
}

function onVerseFocus(id, el) {
  activeField.value = id
  lastVerse.value = el
  syncFormatState()
}

function onVerseBlur(id, event) {
  const next = event.relatedTarget
  if (next && event.currentTarget.contains(next)) return
  if (activeField.value === id) activeField.value = null
}

function onPointerDown(event) {
  if (!root.value?.contains(event.target)) {
    activeField.value = null
    return
  }
  const field = event.target.closest?.('.verse-field')
  activeField.value = field?.dataset.field || null
}

function format(command) {
  const box = verseBoxFromSelection() || lastVerse.value
  if (!box || !root.value?.contains(box)) return
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
  if (!box || !root.value?.contains(box)) return
  boldOn.value = document.queryCommandState('bold')
  italicOn.value = document.queryCommandState('italic')
  underlineOn.value = document.queryCommandState('underline')
}

function chooseLang(event) {
  const code = event.target.value
  if (code === props.langAlt) emit('update:langAlt', props.lang)
  emit('update:lang', code)
}

function chooseLangAlt(event) {
  const code = event.target.value
  if (code === props.lang) emit('update:lang', props.langAlt)
  emit('update:langAlt', code)
}

onMounted(() => {
  document.addEventListener('selectionchange', syncFormatState)
  document.addEventListener('pointerdown', onPointerDown)
})
onUnmounted(() => {
  document.removeEventListener('selectionchange', syncFormatState)
  document.removeEventListener('pointerdown', onPointerDown)
})
</script>

<template>
  <div ref="root">
    <div v-if="paired" class="verse-row language-row">
      <div class="verse-move"></div>
      <label class="language-pick">
        <span>Língua</span>
        <select :value="lang" aria-label="Língua da primeira coluna" @change="chooseLang">
          <option v-for="item in LANGUAGES" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
      </label>
      <label class="language-pick">
        <span>Língua</span>
        <select :value="langAlt" aria-label="Língua da segunda coluna" @change="chooseLangAlt">
          <option v-for="item in LANGUAGES" :key="item.id" :value="item.id">{{ item.label }}</option>
        </select>
      </label>
      <div class="verse-actions"></div>
    </div>
    <div
      class="verse-insert verse-gap"
      :class="{ 'verse-drop': dragOver === 0 }"
      @dragover.prevent.capture="onDragOverGap(0)"
      @drop.prevent="onDropGap(0)"
    ></div>
    <template v-for="(item, index) in modelValue" :key="item.key">
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
            :disabled="index === modelValue.length - 1"
            @click="moveVerse(index, 1)"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="m6 10 6 6 6-6" />
            </svg>
          </button>
        </div>
        <div
          v-for="column in columns"
          :key="column.id"
          class="verse-field"
          :data-field="fieldId(column.id, column.items[index])"
          :class="{ 'verse-focused': activeField === fieldId(column.id, column.items[index]) }"
          @focusout="onVerseBlur(fieldId(column.id, column.items[index]), $event)"
        >
          <div v-if="activeField === fieldId(column.id, column.items[index])" class="format-bar">
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
            v-model="column.items[index].markup"
            :label="`Estrofe ${index + 1}${paired ? ', ' + column.label : ''}`"
            @focus="onVerseFocus(fieldId(column.id, column.items[index]), $event)"
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
    <div v-if="modelValue.length === 0" class="verse-insert">
      <button type="button" class="verse-plus" aria-label="Inserir estrofe" @click="insertVerse(-1)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true">
          <path stroke-linecap="round" d="M12 6v12M6 12h12" />
        </svg>
      </button>
    </div>
  </div>
</template>
