<script setup>
import { COVERS, SEASONS } from '../songs.js'

defineProps({
  enabled: { type: Boolean, required: true },
  cover: { type: Object, required: true },
})

const emit = defineEmits(['update:enabled'])
</script>

<template>
  <section class="panel cover">
    <label class="cover-toggle">
      <input
        class="switch"
        type="checkbox"
        :checked="enabled"
        @change="emit('update:enabled', $event.target.checked)"
      />
      <span><strong>Incluir capa</strong> (primeiro slide da apresentação)</span>
    </label>
    <div v-if="enabled" class="fields">
      <div class="field wide kind">
        <label>
          <input v-model="cover.kind" type="radio" value="celebracao" />
          <span>Celebração</span>
        </label>
        <label>
          <input v-model="cover.kind" type="radio" value="outro" />
          <span>Outro momento</span>
        </label>
      </div>
      <template v-if="cover.kind === 'celebracao'">
        <div class="occasion">
          <label class="field domingo">
            <span>Domingo</span>
            <input v-model="cover.domingo" type="text" placeholder="Domingo XXXIV" />
          </label>
          <label class="field tempo">
            <span>Tempo</span>
            <select v-model="cover.season">
              <option v-for="season in SEASONS" :key="season.id" :value="season.id">{{ season.label }}</option>
            </select>
          </label>
          <label class="field ano">
            <span>Ano</span>
            <select v-model="cover.ano">
              <option>A</option>
              <option>B</option>
              <option>C</option>
            </select>
          </label>
        </div>
      </template>
      <template v-else>
        <label class="field wide">
          <span>Capa</span>
          <select v-model="cover.layout">
            <option v-for="item in COVERS" :key="item.id" :value="item.id">{{ item.label }}</option>
          </select>
        </label>
        <div class="occasion">
          <label class="field">
            <span>Título</span>
            <input v-model="cover.domingo" type="text" placeholder="Ensaio" />
          </label>
          <label class="field">
            <span>Cabeçalho</span>
            <input v-model="cover.header" type="text" placeholder="Ensaio do coro" />
          </label>
        </div>
      </template>
      <label class="field wide">
        <span>Frase</span>
        <input v-model="cover.frase" type="text" placeholder="«Vinde a mim, todos os que estais cansados»" />
      </label>
      <label class="field wide">
        <span>Versículo</span>
        <input v-model="cover.versiculo" type="text" placeholder="Mt 11, 28" />
      </label>
    </div>
  </section>
</template>
