<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { shownPlan } from '../songs.js'

const props = defineProps({
  song: { type: Object, required: true },
  compact: { type: Boolean, default: false },
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['change', 'open', 'close'])
const root = ref(null)
const menu = ref(null)
const menuStyle = ref({ visibility: 'hidden' })
let listening = false

const plan = computed(() => shownPlan(props.song))
const numbers = computed(() => plan.value?.rows?.map((row) => row.n) || [])
const total = computed(() => numbers.value.length)
const chosen = computed(() => {
  const raw = props.song.showEstrofes
  if (Array.isArray(raw)) {
    const picked = raw.filter((n) => numbers.value.includes(n))
    return picked.length ? picked : numbers.value
  }
  if (Number.isInteger(raw) && raw > 0 && raw < total.value) {
    return numbers.value.filter((n) => n <= raw)
  }
  return numbers.value
})
const label = computed(() => {
  if (chosen.value.length === total.value) return 'Todas as estrofes'
  return chosen.value.length === 1 ? '1 estrofe' : `${chosen.value.length} estrofes`
})

function preview(text) {
  const line = (text || '').trim()
  if (!line || line.endsWith('...') || line.endsWith('…')) return line
  return `${line}...`
}

function included(n) {
  return chosen.value.includes(n)
}

function toggle(n, on) {
  const next = numbers.value.filter((item) => (item === n ? on : included(item)))
  if (!next.length) return false
  emit('change', { showEstrofes: next.length === total.value ? undefined : next })
  return true
}

function onSwitch(n, event) {
  if (!toggle(n, event.target.checked)) event.target.checked = true
}

function titleLeft(button) {
  const title = button.closest('li')?.querySelector('.moment-song, .song-name')
  return title ? title.getBoundingClientRect().left : null
}

function place() {
  const button = root.value?.querySelector('button')
  const panel = menu.value
  if (!button || !panel) return
  const rect = button.getBoundingClientRect()
  const start = titleLeft(button)
  const left = start == null || start > rect.right - 160 ? Math.max(8, rect.right - 256) : start
  const width = rect.right - left
  const height = panel.offsetHeight
  let top = rect.bottom + 4
  if (top + height > window.innerHeight - 8 && rect.top - height - 4 >= 8) {
    top = rect.top - height - 4
  }
  menuStyle.value = {
    top: `${top}px`,
    left: `${left}px`,
    width: `${width}px`,
    minWidth: `${width}px`,
    maxWidth: `${width}px`,
    visibility: 'visible',
  }
}

function onPointer(event) {
  if (root.value?.contains(event.target)) return
  emit('close')
}

function onKey(event) {
  if (event.key === 'Escape') emit('close')
}

function listen(on) {
  if (on === listening) return
  listening = on
  const method = on ? 'addEventListener' : 'removeEventListener'
  document[method]('mousedown', onPointer, true)
  document[method]('keydown', onKey)
  window[method]('resize', place)
  document[method]('scroll', place, true)
}

watch(() => props.open, (open) => {
  if (!open) {
    listen(false)
    return
  }
  menuStyle.value = { visibility: 'hidden' }
  nextTick(() => {
    place()
    listen(true)
  })
})

onBeforeUnmount(() => listen(false))
</script>

<template>
  <div v-if="compact && plan" ref="root" class="estrofe-menu-anchor">
    <button
      type="button"
      class="icon"
      :aria-expanded="open"
      aria-haspopup="menu"
      :aria-label="label"
      title="Selecionar estrofes"
      @click="emit('open')"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <path stroke-linecap="round" d="M15 6H3M11 12H3M11 18H3" />
        <path stroke-linecap="round" d="M20.5 16V5" />
        <circle cx="18" cy="16" r="2.5" />
      </svg>
    </button>
    <div v-if="open" ref="menu" class="estrofe-menu" role="menu" :style="menuStyle">
      <p class="estrofe-menu-title">{{ label }}</p>
      <ul>
        <li v-for="row in plan.rows" :key="row.n">
          <div class="estrofe" @click="toggle(row.n, !included(row.n))">
            <input
              class="switch small"
              type="checkbox"
              :checked="included(row.n)"
              :aria-label="preview(row.line)"
              @click.stop
              @change="onSwitch(row.n, $event)"
            />
            <span class="estrofe-n">{{ row.n }}</span>
            <span class="estrofe-line">{{ preview(row.line) }}</span>
          </div>
        </li>
        <li class="estrofe-refrain">
          <span>Refrão</span>
          <span class="estrofe-line">{{ preview(plan.refrain) }}</span>
          <span class="estrofe-where">{{ plan.where }}</span>
        </li>
      </ul>
    </div>
  </div>
  <div v-else-if="plan" class="estrofes">
    <p class="estrofe-label">Estrofes</p>
    <ul>
      <li v-for="row in plan.rows" :key="row.n">
        <div class="estrofe" @click="toggle(row.n, !included(row.n))">
          <input
            class="switch small"
            type="checkbox"
            :checked="included(row.n)"
            :aria-label="preview(row.line)"
            @click.stop
            @change="onSwitch(row.n, $event)"
          />
          <span class="estrofe-n">{{ row.n }}</span>
          <span class="estrofe-line">{{ preview(row.line) }}</span>
        </div>
      </li>
      <li class="estrofe-refrain">
        <span>Refrão</span>
        <span class="estrofe-line">{{ preview(plan.refrain) }}</span>
        <span class="estrofe-where">{{ plan.where }}</span>
      </li>
    </ul>
  </div>
</template>
