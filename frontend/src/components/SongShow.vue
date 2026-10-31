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
const switched = computed(() => shown.value !== preferredLyrics(props.song))

function toggle() {
  if (!bothLanguages.value) return
  emit('change', { showLyrics: shown.value === 'contentAlt' ? 'content' : 'contentAlt' })
}
</script>

<template>
  <button
    v-if="bothLanguages"
    type="button"
    class="icon language-toggle"
    :class="{ 'language-on': switched }"
    title="Mudar a língua"
    :aria-label="'Língua ' + current + '. Mudar para ' + other"
    @click="toggle"
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <path stroke-linecap="round" stroke-linejoin="round" d="m5 8 6 6" />
      <path stroke-linecap="round" stroke-linejoin="round" d="m4 14 6-6 2-3" />
      <path stroke-linecap="round" d="M2 5h12" />
      <path stroke-linecap="round" d="M7 2h1" />
      <path stroke-linecap="round" stroke-linejoin="round" d="m22 22-5-10-5 10" />
      <path stroke-linecap="round" d="M14 18h6" />
    </svg>
  </button>
</template>
