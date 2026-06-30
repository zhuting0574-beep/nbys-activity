<template>
  <div
    ref="root"
    class="section-background-carousel"
    :aria-label="label"
    @mouseenter="setHover(true)"
    @mouseleave="setHover(false)"
  >
    <div class="section-background-viewport">
      <div class="section-background-slides" aria-hidden="true">
        <img
          v-for="(image, index) in normalizedImages"
          :key="`${image}-${index}`"
          :src="image"
          alt=""
          :class="{ active: index === activeIndex }"
          :loading="index === 0 ? 'eager' : 'lazy'"
          decoding="async"
        />
      </div>
      <div class="section-background-shade" aria-hidden="true"></div>
      <template v-if="normalizedImages.length > 1">
        <button class="section-carousel-arrow previous" type="button" :aria-label="`${label}上一张`" @click="previous">‹</button>
        <button class="section-carousel-arrow next" type="button" :aria-label="`${label}下一张`" @click="manualNext">›</button>
        <div class="section-carousel-dots" :aria-label="`${label}图片选择`">
          <button
            v-for="(_, index) in normalizedImages"
            :key="index"
            type="button"
            :class="{ active: index === activeIndex }"
            :aria-label="`显示第${index + 1}张图片`"
            :aria-current="index === activeIndex ? 'true' : undefined"
            @click="goTo(index)"
          ></button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BackgroundCarousel',
  props: {
    images: { type: Array, default: () => [] },
    label: { type: String, default: '板块背景轮播' },
    interval: { type: Number, default: 5000 }
  },
  data() {
    return {
      activeIndex: 0,
      timer: null,
      hovered: false,
      visible: false,
      pageVisible: !document.hidden,
      reduceMotion: false,
      observer: null,
      motionQuery: null
    }
  },
  computed: {
    normalizedImages() {
      return this.images
        .map(item => typeof item === 'string' ? item : item?.image_url)
        .filter(Boolean)
    },
    shouldPlay() {
      return this.normalizedImages.length > 1 && this.visible && this.pageVisible && !this.hovered && !this.reduceMotion
    }
  },
  watch: {
    normalizedImages() {
      this.activeIndex = 0
      this.restart()
    },
    shouldPlay() {
      this.restart()
    }
  },
  mounted() {
    this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    this.reduceMotion = this.motionQuery.matches
    this.motionQuery.addEventListener?.('change', this.handleMotionChange)
    document.addEventListener('visibilitychange', this.handleVisibilityChange)
    this.observer = new IntersectionObserver(entries => {
      this.visible = entries.some(entry => entry.isIntersecting)
    }, { threshold: 0.08 })
    this.observer.observe(this.$refs.root)
  },
  beforeUnmount() {
    this.stop()
    this.observer?.disconnect()
    this.motionQuery?.removeEventListener?.('change', this.handleMotionChange)
    document.removeEventListener('visibilitychange', this.handleVisibilityChange)
  },
  methods: {
    restart() {
      this.stop()
      if (this.shouldPlay) this.timer = window.setInterval(this.advance, this.interval)
    },
    stop() {
      if (this.timer) window.clearInterval(this.timer)
      this.timer = null
    },
    previous() {
      const count = this.normalizedImages.length
      if (!count) return
      this.activeIndex = (this.activeIndex - 1 + count) % count
      this.restart()
    },
    advance() {
      const count = this.normalizedImages.length
      if (!count) return
      this.activeIndex = (this.activeIndex + 1) % count
    },
    manualNext() {
      this.advance()
      this.restart()
    },
    goTo(index) {
      this.activeIndex = index
      this.restart()
    },
    setHover(value) {
      this.hovered = value
    },
    handleVisibilityChange() {
      this.pageVisible = !document.hidden
    },
    handleMotionChange(event) {
      this.reduceMotion = event.matches
    }
  }
}
</script>
