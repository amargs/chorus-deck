<script setup>
import SongShow from './SongShow.vue'

defineProps({
  songs: { type: Array, required: true },
})

const emit = defineEmits(['remove', 'up', 'down', 'clear', 'show'])
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
      <li v-for="(song, index) in songs" :key="`${song.id}-${index}`">
        <div class="chosen-body">
          <span class="song-label">
            <span class="book-badge">{{ song.bookId || '#' }}</span>
            <span class="song-name">{{ song.title }}</span>
          </span>
          <SongShow :song="song" @change="emit('show', index, $event)" />
        </div>
        <span class="row-actions">
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
          <button type="button" class="icon" aria-label="Retirar" @click="emit('remove', index)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 7h16M9 7V5h6v2M8 7l1 12h6l1-12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </span>
      </li>
    </ul>
  </section>
</template>
