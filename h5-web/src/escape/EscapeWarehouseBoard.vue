<template>
  <section class="escape-warehouse-board">
    <header class="escape-storage-heading">
      <div><span>ASSET STORAGE</span><h2>我的仓库</h2></div>
      <small>拖拽，或点选物品后点击目标格</small>
    </header>

    <article
      v-for="type in warehouseTypes"
      :key="type"
      class="escape-warehouse-zone"
      :class="`${type}-zone`"
    >
      <header>
        <div><strong>{{ type === 'buffer' ? '缓冲区仓库' : '个人仓库' }}</strong><span>{{ warehouse(type).height }} × {{ warehouse(type).width }}</span></div>
        <small>{{ type === 'buffer' ? '每日 05:00 自动出售过夜物品' : '永久保存，不参与自动出售' }}</small>
      </header>
      <div class="escape-storage-scroll">
        <div
          class="escape-storage-grid"
          :data-warehouse="type"
          :style="gridStyle(type)"
          role="grid"
          :aria-label="`${type === 'buffer' ? '缓冲区' : '个人'}仓库 ${warehouse(type).height} 行 ${warehouse(type).width} 列`"
          @click="placeSelected($event, type)"
        >
          <i
            v-for="cell in cells(type)"
            :key="cell"
            class="escape-storage-cell"
            :style="cellStyle(cell, type)"
            aria-hidden="true"
          ></i>
          <button
            v-for="item in warehouse(type).items"
            :key="item.inventory_id"
            type="button"
            class="escape-storage-item"
            :class="[
              `rarity-${item.rarity || 'normal'}`,
              { selected: Number(item.inventory_id) === Number(selectedId), dragging: Number(item.inventory_id) === Number(pointer?.item?.inventory_id) && pointer?.dragging }
            ]"
            :style="itemStyle(item)"
            :aria-label="`${item.name}，尺寸 ${item.width || 1} 乘 ${item.height || 1}`"
            @pointerdown="beginDrag($event, item, type)"
            @pointermove="moveDrag"
            @pointerup="endDrag"
            @pointercancel="cancelDrag"
          >
            <img v-if="item.image_url" :src="item.image_url" alt="" draggable="false" />
            <b v-else>{{ symbol(item) }}</b>
            <em>{{ item.width || 1 }}×{{ item.height || 1 }}</em>
            <small>{{ item.name }}</small>
          </button>
          <div
            v-if="preview?.warehouse === type"
            class="escape-storage-preview"
            :class="preview.valid ? 'valid' : 'invalid'"
            :style="previewStyle"
            aria-hidden="true"
          ></div>
        </div>
      </div>
      <footer>
        <span>{{ type === 'buffer' ? '横向滑动查看完整仓库' : '可拖回缓冲区' }}</span>
        <div><b>{{ usage(type) }} / {{ Number(warehouse(type).width || 0) * Number(warehouse(type).height || 0) }} 格</b><button v-if="warehouse(type).items.length" type="button" :disabled="moving" @click="$emit('sell-all', type)">全部出售</button></div>
      </footer>
    </article>

    <aside v-if="selectedItem" class="escape-selected-item">
      <div>
        <span>{{ rarityText(selectedItem.rarity) }} · {{ selectedItem.width || 1 }} × {{ selectedItem.height || 1 }}</span>
        <strong>{{ selectedItem.name }}</strong>
      </div>
      <div><small>今日估值</small><b>¥{{ money(selectedItem.current_price) }}</b></div>
      <button type="button" :disabled="moving || selectedItem.status !== 'available'" @click="$emit('sell', selectedItem)">出售</button>
    </aside>

    <div
      v-if="pointer?.dragging"
      class="escape-storage-ghost"
      :class="`rarity-${pointer.item.rarity || 'normal'}`"
      :style="ghostStyle"
      aria-hidden="true"
    >
      <img v-if="pointer.item.image_url" :src="pointer.item.image_url" alt="" />
      <b v-else>{{ symbol(pointer.item) }}</b>
    </div>
  </section>
</template>

<script>
export default {
  name: 'EscapeWarehouseBoard',
  props: {
    warehouses: { type: Object, default: () => ({}) },
    moving: Boolean
  },
  emits: ['move', 'sell', 'sell-all', 'invalid'],
  data() {
    return {
      warehouseTypes: ['buffer', 'personal'],
      selectedId: null,
      pointer: null,
      preview: null
    }
  },
  computed: {
    selectedItem() {
      for (const type of this.warehouseTypes) {
        const item = this.warehouse(type).items.find(row => Number(row.inventory_id) === Number(this.selectedId))
        if (item) return item
      }
      return null
    },
    previewStyle() {
      if (!this.preview || !this.pointer) return {}
      return this.itemStyle({
        pos_x: this.preview.posX,
        pos_y: this.preview.posY,
        width: this.pointer.item.width,
        height: this.pointer.item.height
      })
    },
    ghostStyle() {
      if (!this.pointer) return {}
      return {
        left: `${this.pointer.clientX}px`,
        top: `${this.pointer.clientY}px`,
        width: `${this.pointer.width}px`,
        height: `${this.pointer.height}px`
      }
    }
  },
  methods: {
    warehouse(type) {
      return this.warehouses[type] || { width: type === 'buffer' ? 48 : 12, height: type === 'buffer' ? 16 : 8, items: [] }
    },
    cells(type) {
      const data = this.warehouse(type)
      return Array.from({ length: Number(data.width || 0) * Number(data.height || 0) }, (_, index) => index)
    },
    gridStyle(type) {
      const data = this.warehouse(type)
      return { '--cols': Number(data.width || 1), '--rows': Number(data.height || 1) }
    },
    itemStyle(item) {
      return {
        gridColumn: `${Number(item.pos_x || 0) + 1} / span ${Number(item.width || 1)}`,
        gridRow: `${Number(item.pos_y || 0) + 1} / span ${Number(item.height || 1)}`
      }
    },
    cellStyle(index, type) {
      const width = Number(this.warehouse(type).width || 1)
      return {
        gridColumn: String(index % width + 1),
        gridRow: String(Math.floor(index / width) + 1)
      }
    },
    usage(type) {
      return this.warehouse(type).items.reduce((sum, item) => sum + Number(item.width || 1) * Number(item.height || 1), 0)
    },
    beginDrag(event, item, type) {
      this.selectedId = item.inventory_id
      if (this.moving || item.status !== 'available') return
      const rect = event.currentTarget.getBoundingClientRect()
      event.currentTarget.setPointerCapture?.(event.pointerId)
      this.pointer = {
        item,
        sourceWarehouse: type,
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        clientX: event.clientX,
        clientY: event.clientY,
        width: rect.width,
        height: rect.height,
        dragging: false
      }
    },
    moveDrag(event) {
      if (!this.pointer || event.pointerId !== this.pointer.pointerId) return
      const distance = Math.hypot(event.clientX - this.pointer.startX, event.clientY - this.pointer.startY)
      if (!this.pointer.dragging && distance < 7) return
      event.preventDefault()
      this.pointer = { ...this.pointer, dragging: true, clientX: event.clientX, clientY: event.clientY }
      this.autoScroll(event)
      this.updatePreview(event)
    },
    endDrag(event) {
      if (!this.pointer || event.pointerId !== this.pointer.pointerId) return
      const drop = this.preview
      const item = this.pointer.item
      const dragged = this.pointer.dragging
      this.pointer = null
      this.preview = null
      if (!dragged) return
      event.preventDefault()
      if (!drop?.valid) {
        this.$emit('invalid', '目标位置空间不足或与其他物品重叠')
        return
      }
      this.$emit('move', { item, targetWarehouse: drop.warehouse, posX: drop.posX, posY: drop.posY })
    },
    cancelDrag() {
      this.pointer = null
      this.preview = null
    },
    updatePreview(event) {
      const element = document.elementFromPoint(event.clientX, event.clientY)
      const grid = element instanceof Element ? element.closest('.escape-storage-grid') : null
      if (!grid) {
        this.preview = null
        return
      }
      const warehouse = grid.dataset.warehouse
      const position = this.gridPosition(grid, warehouse, event.clientX, event.clientY)
      this.preview = {
        warehouse,
        ...position,
        valid: this.canPlace(this.pointer.item, warehouse, position.posX, position.posY)
      }
    },
    gridPosition(grid, type, clientX, clientY) {
      const rect = grid.getBoundingClientRect()
      const data = this.warehouse(type)
      return {
        posX: Math.floor((clientX - rect.left) / (rect.width / Number(data.width || 1))),
        posY: Math.floor((clientY - rect.top) / (rect.height / Number(data.height || 1)))
      }
    },
    canPlace(item, type, posX, posY) {
      const data = this.warehouse(type)
      const width = Number(item.width || 1)
      const height = Number(item.height || 1)
      if (posX < 0 || posY < 0 || posX + width > Number(data.width) || posY + height > Number(data.height)) return false
      return !data.items.some(existing => {
        if (Number(existing.inventory_id) === Number(item.inventory_id)) return false
        return posX < Number(existing.pos_x) + Number(existing.width || 1)
          && posX + width > Number(existing.pos_x)
          && posY < Number(existing.pos_y) + Number(existing.height || 1)
          && posY + height > Number(existing.pos_y)
      })
    },
    placeSelected(event, type) {
      if (!this.selectedItem || this.moving || event.target.closest('.escape-storage-item')) return
      const grid = event.currentTarget
      const position = this.gridPosition(grid, type, event.clientX, event.clientY)
      if (!this.canPlace(this.selectedItem, type, position.posX, position.posY)) {
        this.$emit('invalid', '目标位置空间不足或与其他物品重叠')
        return
      }
      this.$emit('move', { item: this.selectedItem, targetWarehouse: type, posX: position.posX, posY: position.posY })
    },
    autoScroll(event) {
      const element = document.elementFromPoint(event.clientX, event.clientY)
      const scroll = element instanceof Element ? element.closest('.escape-storage-scroll') : null
      if (scroll) {
        const rect = scroll.getBoundingClientRect()
        if (event.clientX > rect.right - 32) scroll.scrollLeft += 18
        if (event.clientX < rect.left + 32) scroll.scrollLeft -= 18
        if (event.clientY > rect.bottom - 26) scroll.scrollTop += 14
        if (event.clientY < rect.top + 26) scroll.scrollTop -= 14
      }
      if (event.clientY > window.innerHeight - 44) window.scrollBy(0, 18)
      if (event.clientY < 104) window.scrollBy(0, -18)
    },
    symbol(item) {
      return String(item.name || '物').slice(0, 1)
    },
    rarityText(value) {
      return { extraordinary: '超凡', epic: '史诗', fine: '精品', premium: '精品', normal: '普通' }[value] || '普通'
    },
    money(value) {
      return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
    }
  }
}
</script>
