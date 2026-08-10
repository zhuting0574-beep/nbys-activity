<template>
  <main class="escape-route-page">
    <EscapeApp v-if="user.id" :user="user" @back="goBack" @notify="showToast" />
    <section v-else class="escape-route-loading">
      <span></span>
      <strong>正在进入逃离西撇镇</strong>
      <p>同步操作员档案与资产信息</p>
    </section>
    <div v-if="toast.show" class="escape-route-toast" role="alert">{{ toast.message }}</div>
  </main>
</template>

<script>
import { api, setErrorHandler, token } from '../api'
import EscapeApp from './EscapeApp.vue'

export default {
  name: 'EscapePage',
  components: { EscapeApp },
  data() {
    return {
      user: {},
      toast: { show: false, message: '' },
      toastTimer: null
    }
  },
  async mounted() {
    if (!token()) {
      this.goBack()
      return
    }
    setErrorHandler(this.showToast)
    window.addEventListener('nbys-auth-expired', this.goBack)
    try {
      this.user = await api('/api/h5/me')
    } catch (error) {
      this.showToast(error.message || '操作员信息加载失败')
    }
  },
  beforeUnmount() {
    window.removeEventListener('nbys-auth-expired', this.goBack)
    window.clearTimeout(this.toastTimer)
  },
  methods: {
    goBack() {
      window.location.hash = '#/app'
    },
    showToast(message) {
      if (!message) return
      window.clearTimeout(this.toastTimer)
      this.toast = { show: true, message }
      this.toastTimer = window.setTimeout(() => {
        this.toast.show = false
      }, 2400)
    }
  }
}
</script>
