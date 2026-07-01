<template>
  <MemberApp v-if="route === 'app'" @loading-start="showActivityLoading" @ready="hideActivityLoading" />
  <MarketingSite v-else @enter-app="enterApp" />
  <Teleport to="body">
    <Transition name="activity-loader">
      <div v-if="activityLoading" class="activity-loading-mask" role="status" aria-live="polite" aria-label="活动列表加载中">
        <img :src="activityLoadingLogo" alt="宁波甬士" />
        <span>活动加载中</span>
      </div>
    </Transition>
  </Teleport>
</template>

<script>
import MarketingSite from './MarketingSite.vue'
import MemberApp from './MemberApp.vue'
import activityLoadingLogo from './assets/activity-loading-logo.jpg'

export default {
  name: 'App',
  components: { MarketingSite, MemberApp },
  data() {
    const initialRoute = window.location.hash.replace(/^#\/?/, '') || 'site'
    return {
      route: initialRoute,
      activityLoadingLogo,
      activityLoading: initialRoute === 'app',
      activityLoadingStartedAt: Date.now(),
      activityLoadingTimer: null
    }
  },
  mounted() {
    window.addEventListener('hashchange', this.syncRoute)
    this.applyAppMode()
  },
  beforeUnmount() {
    window.removeEventListener('hashchange', this.syncRoute)
    window.clearTimeout(this.activityLoadingTimer)
  },
  methods: {
    syncRoute() {
      const nextRoute = window.location.hash.replace(/^#\/?/, '') || 'site'
      if (nextRoute === 'app' && !this.activityLoading) this.showActivityLoading()
      if (nextRoute !== 'app') {
        window.clearTimeout(this.activityLoadingTimer)
        this.activityLoading = false
      }
      this.route = nextRoute
      this.applyAppMode()
      window.scrollTo({ top: 0, behavior: 'auto' })
    },
    applyAppMode() {
      document.body.classList.toggle('ys-marketing-mode', this.route !== 'app')
      document.body.classList.toggle('ys-member-mode', this.route === 'app')
    },
    enterApp() {
      this.showActivityLoading()
      window.location.hash = '#/app'
    },
    showActivityLoading() {
      window.clearTimeout(this.activityLoadingTimer)
      this.activityLoadingStartedAt = Date.now()
      this.activityLoading = true
    },
    hideActivityLoading() {
      const remaining = Math.max(0, 800 - (Date.now() - this.activityLoadingStartedAt))
      window.clearTimeout(this.activityLoadingTimer)
      this.activityLoadingTimer = window.setTimeout(() => {
        this.activityLoading = false
      }, remaining)
    }
  }
}
</script>
