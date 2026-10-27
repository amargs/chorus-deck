<script setup>
import { onMounted, ref, watch } from 'vue'
import { htmlToMarkup, markupToHtml } from '../markup.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, required: true },
})

const emit = defineEmits(['update:modelValue', 'focus'])

const el = ref(null)
let writing = false

onMounted(() => {
  el.value.innerHTML = markupToHtml(props.modelValue)
})

watch(() => props.modelValue, (value) => {
  if (writing || !el.value) return
  if (htmlToMarkup(el.value) !== value) {
    el.value.innerHTML = markupToHtml(value)
  }
})

function onInput() {
  writing = true
  emit('update:modelValue', htmlToMarkup(el.value))
  writing = false
}

function onKeydown(event) {
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return
  const command = { b: 'bold', i: 'italic', u: 'underline' }[event.key.toLowerCase()]
  if (!command) return
  event.preventDefault()
  document.execCommand(command)
  onInput()
}

function onFocus() {
  emit('focus', el.value)
}
</script>

<template>
  <div
    ref="el"
    class="verse-box"
    contenteditable="true"
    role="textbox"
    aria-multiline="true"
    :aria-label="label"
    @input="onInput"
    @keydown="onKeydown"
    @focus="onFocus"
  ></div>
</template>
