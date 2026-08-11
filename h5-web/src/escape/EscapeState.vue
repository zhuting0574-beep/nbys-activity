<template>
  <div class="escape-state" :class="`is-${type}`" role="status">
    <div v-if="type === 'loading'" class="escape-state-spinner" aria-hidden="true"></div>
    <div v-else class="escape-state-icon" aria-hidden="true">{{ icon }}</div>
    <strong>{{ title }}</strong>
    <p>{{ description }}</p>
    <button v-if="actionLabel" type="button" @click="$emit('action')">{{ actionLabel }}</button>
  </div>
</template>

<script>
export default {
  name: 'EscapeState',
  emits: ['action'],
  props: {
    type: { type: String, default: 'empty' },
    title: { type: String, required: true },
    description: { type: String, default: '' },
    actionLabel: { type: String, default: '' }
  },
  computed: {
    icon() {
      return { empty: '◇', error: '!', forbidden: '×' }[this.type] || '·'
    }
  }
}
</script>
