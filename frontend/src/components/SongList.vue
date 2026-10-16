<script setup>
import { computed } from 'vue'
import { byBookId, matches } from '../songs.js'

const props = defineProps({
  songs: { type: Array, required: true },
  query: { type: String, required: true },
  chosenIds: { type: Array, required: true },
})

const emit = defineEmits(['update:query', 'add', 'remove-song'])

function picked(song) {
  return props.chosenIds.includes(song.id)
}

function toggle(song) {
  if (!song.hasVerses) {
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
    <h2>Lista de cânticos</h2>
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
          <span v-if="song.bookId" class="book">{{ song.bookId }}</span>
          {{ song.title }}
        </span>
        <span v-if="!song.hasVerses" class="missing">sem letra</span>
        <button
          type="button"
          class="icon"
          :aria-label="picked(song) ? 'Retirar' : 'Adicionar'"
          :disabled="!song.hasVerses"
          @click="toggle(song)"
        >
          <svg v-if="picked(song)" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M19 12H5M11 6l-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg v-else viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 12h14M13 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </li>
    </ul>
  </section>
</template>
