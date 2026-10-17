<template>
  <router-link :to="`/programs/${program.id}`" class="program-card">
    <!-- 썸네일: 배송유형 컬러 배경 + 라인 아이콘 -->
    <div class="card-thumb" :class="meta.bg">
      <CategoryIcon :category="program.category" class="thumb-icon" />
    </div>

    <!-- 내용 -->
    <div class="card-body">
      <span class="badge" :class="meta.badge">{{ categoryLabel }}</span>
      <h3 class="card-title">{{ program.title }}</h3>
      <p class="card-blurb">{{ meta.blurb }}</p>
      <div class="card-meta">
        <span class="price">분담금 ₩{{ netBurden(program.price).toLocaleString() }}</span>
      </div>
      <div class="card-footer">
        <span class="participated">현재 {{ Number(program.participationCount || 0).toLocaleString() }}건 참여 신청</span>
      </div>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import { useProgramStore, netBurden } from '@/store/program.js'
import CategoryIcon from '@/components/CategoryIcon.vue'

const props = defineProps({
  program: { type: Object, required: true },
})

const programStore = useProgramStore()

// program.category는 목록(정규화된 라벨) / 추천·참여 응답(원본 enum) 어느 쪽이든 들어올 수 있다.
const meta = computed(() => programStore.categoryMeta(props.program.category))
const categoryLabel = computed(() => meta.value.label)
</script>

<style scoped>
.program-card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: var(--transition);
  cursor: pointer;
}
.program-card:hover {
  transform: translateY(-3px);
  border-color: var(--color-primary);
}
.card-thumb {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
/* .thumb-* 배경/아이콘 색은 global.css 에서 관리 */
.thumb-icon {
  width: 44px;
  height: 44px;
}
.card-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  line-height: 1.4;
}
.card-blurb {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.4;
}
.card-meta {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: auto;
}
.price {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}
.card-footer {
  margin-top: 2px;
}
.participated {
  font-size: 11px;
  color: var(--color-text-muted);
}
</style>
