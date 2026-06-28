<template>
  <MemberApp v-if="route === 'app'" />
  <MarketingSite v-else @enter-app="enterApp" />
</template>

<script>
import MarketingSite from './MarketingSite.vue'
import MemberApp from './MemberApp.vue'

export default {
  name: 'App',
  components: { MarketingSite, MemberApp },
  data() {
    return {
      route: window.location.hash.replace(/^#\/?/, '') || 'site'
    }
  },
  mounted() {
    window.addEventListener('hashchange', this.syncRoute)
    this.applyAppMode()
  },
  beforeUnmount() {
    window.removeEventListener('hashchange', this.syncRoute)
  },
  methods: {
    syncRoute() {
      this.route = window.location.hash.replace(/^#\/?/, '') || 'site'
      this.applyAppMode()
      window.scrollTo({ top: 0, behavior: 'auto' })
    },
    applyAppMode() {
      document.body.classList.toggle('ys-marketing-mode', this.route !== 'app')
      document.body.classList.toggle('ys-member-mode', this.route === 'app')
    },
    enterApp() {
      window.location.hash = '#/app'
    }
  }
}
</script>
