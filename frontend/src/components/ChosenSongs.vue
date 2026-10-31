<script setup>
import { ref } from 'vue'
import Estrofes from './Estrofes.vue'
import SongShow from './SongShow.vue'
import { estrofeCountLabel, languageLabel } from '../songs.js'

defineProps({
  songs: { type: Array, required: true },
})

const emit = defineEmits(['remove', 'up', 'down', 'clear', 'show'])
const estrofesOpen = ref(null)

function showEstrofes(id) {
  estrofesOpen.value = estrofesOpen.value === id ? null : id
}

function chosenDetail(song) {
  return [languageLabel(song), estrofeCountLabel(song)].filter(Boolean).join(' · ')
}
</script>

<template>
  <section class="panel">
    <div class="panel-head">
      <h2>Cânticos escolhidos</h2>
      <button type="button" class="clear" :disabled="songs.length === 0" @click="emit('clear')">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 6h9M4 12h9M4 18h6M16 15l4 4M20 15l-4 4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        Limpar
      </button>
    </div>
    <p v-if="songs.length === 0" class="missing">Ainda não há cânticos.</p>
    <ul class="songs">
      <li v-for="(song, index) in songs" :key="`${song.id}-${index}`" class="chosen-song">
        <div class="chosen-body">
          <span class="song-label">
            <span class="book-badge">{{ song.bookId || '#' }}</span>
            <span class="song-name">
              <span>{{ song.title }}</span>
              <span v-if="chosenDetail(song)" class="chosen-detail">{{ chosenDetail(song) }}</span>
            </span>
          </span>
        </div>
        <span class="row-actions">
          <SongShow :song="song" @change="emit('show', index, $event)" />
          <Estrofes
            compact
            :song="song"
            :open="estrofesOpen === song.id"
            @open="showEstrofes(song.id)"
            @close="estrofesOpen = null"
            @change="emit('show', index, $event)"
          />
          <button type="button" class="icon" aria-label="Subir" :disabled="index === 0" @click="emit('up', index)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 19V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <button type="button" class="icon" aria-label="Descer" :disabled="index === songs.length - 1" @click="emit('down', index)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 5v14M6 13l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <button type="button" class="icon row-remove" aria-label="Retirar" @click="emit('remove', index)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </span>
      </li>
    </ul>
  </section>
</template>
