<script setup>
import { computed } from 'vue'
import { languageName } from '../songs.js'

const props = defineProps({
  song: { type: Object, required: true },
})

const emit = defineEmits(['change'])

const bothLanguages = computed(() => (props.song.verses || 0) > 0 && (props.song.versesAlt || 0) > 0)

function onLyrics(event) {
  emit('change', { showLyrics: event.target.value })
}
</script>

<template>
  <div v-if="bothLanguages" class="song-show">
    <label>
      <span>Língua</span>
      <select :value="song.showLyrics || 'content'" aria-label="Língua" @change="onLyrics">
        <option value="content">{{ languageName(song.lang) || 'Português' }}</option>
        <option value="contentAlt">{{ languageName(song.langAlt) || 'Latim' }}</option>
      </select>
    </label>
  </div>
</template>
