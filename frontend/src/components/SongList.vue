<script setup>
import { computed } from 'vue'
import { byBookId, matches } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
  query: { type: String, required: true },
  chosenIds: { type: Array, required: true },
  heading: { type: String, default: 'Lista de cânticos' },
  requireBookId: { type: Boolean, default: false },
})

const emit = defineEmits(['update:query', 'add', 'remove-song'])

function picked(song) {
  return props.chosenIds.includes(song.id)
}

function unavailable(song) {
  return !song.hasVerses || (props.requireBookId && !song.bookId)
}

function toggle(song) {
  if (unavailable(song)) {
    return
  }
  if (picked(song)) {
    emit('remove-song', song.id)
  } else {
    emit('add', song)
  }
}

const visible = computed(() =>
  props.songs.filter((song) => matches(song, props.query)).sort(byBookId))
</script>

<template>
  <section class="panel">
    <h2>{{ heading }}</h2>
    <input
      class="search"
      type="search"
      placeholder="Título ou número"
      :value="query"
      @input="emit('update:query', $event.target.value)"
    />
    <ul class="songs">
      <li v-for="song in visible" :key="song.id">
        <span class="song-label">
          <span class="book-badge">{{ song.bookId || '#' }}</span>
          {{ song.title }}
        </span>
        <span v-if="!song.hasVerses" class="missing">sem letra</span>
        <button
          type="button"
          class="icon"
          :class="{ chosen: picked(song) }"
          :aria-label="picked(song) ? 'Retirar' : 'Adicionar'"
          :disabled="unavailable(song)"
          @click="toggle(song)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 12h14M13 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </li>
    </ul>
  </section>
</template>
