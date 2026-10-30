<script setup>
import { computed } from 'vue'
import { languageName, preferredLyrics } from '../songs.js'

const props = defineProps({
  song: { type: Object, required: true },
})

const emit = defineEmits(['change'])

const bothLanguages = computed(() => (props.song.verses || 0) > 0 && (props.song.versesAlt || 0) > 0)
const shown = computed(() => props.song.showLyrics || preferredLyrics(props.song))

function name(alt) {
  return languageName(alt ? props.song.langAlt : props.song.lang) || (alt ? 'Latim' : 'Português')
}

const current = computed(() => name(shown.value === 'contentAlt'))
const other = computed(() => name(shown.value !== 'contentAlt'))

function toggle() {
  if (!bothLanguages.value) return
  emit('change', { showLyrics: shown.value === 'contentAlt' ? 'content' : 'contentAlt' })
}
</script>

<template>
  <button
    v-if="bothLanguages"
    type="button"
    class="rezado"
    :title="'Mudar para ' + other"
    :aria-label="'Língua ' + current + '. Mudar para ' + other"
    @click="toggle"
  >
    {{ current }}
  </button>
</template>
