<template>
  <Transition name="search-modal">
    <div
      v-if="visible"
      class="gs-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="全局搜索"
      @click.self="close"
      @keydown.esc="close"
    >
      <div class="gs-panel" role="search">
        <!-- 输入框 -->
        <div class="gs-input-wrap">
          <LucideIcon name="lucide:search" :size="18" class="gs-icon" />
          <input
            ref="inputRef"
            v-model="query"
            class="gs-input"
            :placeholder="placeholder"
            aria-label="搜索"
            @keydown.enter="handleEnter"
            @keydown.up.prevent="navigate(-1)"
            @keydown.down.prevent="navigate(1)"
          />
          <kbd class="gs-kbd">Esc</kbd>
        </div>

        <!-- 结果列表 -->
        <div class="gs-results" role="listbox">
          <template v-if="results.length">
            <button
              v-for="(item, idx) in results"
              :key="item.id"
              class="gs-item"
              :class="{ 'is-active': idx === activeIndex }"
              role="option"
              :aria-selected="idx === activeIndex"
              @click="goTo(item)"
              @mouseenter="activeIndex = idx"
            >
              <LucideIcon :name="item.icon || 'lucide:file'" :size="14" class="gs-item-icon" />
              <div class="gs-item-body">
                <span class="gs-item-title" v-html="item.highlightedTitle || item.title"></span>
                <span class="gs-item-desc" v-if="item.description">{{ item.description }}</span>
              </div>
              <span class="gs-app-badge" :class="`is-${item.appName}`">{{ item.appLabel }}</span>
            </button>
          </template>
          <div v-else-if="query.length" class="gs-empty">未找到匹配项</div>
          <div v-else class="gs-tips">
            <span>↑↓ 选择</span><span>↵ 跳转</span><span>Esc 关闭</span>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted, defineModel } from 'vue';
import type { SearchItem } from '@/hooks/use-global-search';
import { registerKeyboard } from '@/hooks/use-global-shortcut';
import LucideIcon from '@/components/lucide-icon.vue';

/** 受控显隐 */
const props = defineProps<{ items: SearchItem[]; appNameLabels?: Record<string, string> }>();
const visible = defineModel<boolean>('visible', { required: true });

const query = ref('');
const activeIndex = ref(0);
const inputRef = ref<HTMLInputElement | null>(null);
const placeholder = '搜索菜单、功能、操作... (⌘K)';

/** 搜索结果过滤 */
const results = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return [];
  return props.items
    .map((item) => {
      const titleIdx = item.title.toLowerCase().indexOf(q);
      const descIdx = item.description?.toLowerCase().indexOf(q) ?? -1;
      if (titleIdx < 0 && descIdx < 0) return null;
      const qIdx = titleIdx >= 0 ? titleIdx : descIdx;
      const highlightedTitle =
        item.title.slice(0, qIdx) +
        `<mark>${item.title.slice(qIdx, qIdx + q.length)}</mark>` +
        item.title.slice(qIdx + q.length);
      return { ...item, highlightedTitle };
    })
    .filter((x): x is SearchItem & { highlightedTitle: string } => x !== null)
    .slice(0, 30);
});

watch(visible, async (v) => {
  if (v) {
    await nextTick();
    inputRef.value?.focus();
    query.value = '';
    activeIndex.value = 0;
  }
});

watch(query, () => { activeIndex.value = 0; });

function navigate(dir: number) {
  const len = results.value.length;
  if (!len) return;
  activeIndex.value = (activeIndex.value + dir + len) % len;
}

function handleEnter() {
  const item = results.value[activeIndex.value];
  if (item) goTo(item);
}

function goTo(item: SearchItem) {
  close();
  if (item.onClick) item.onClick();
  else if (item.path) emitRouterPush(item.path);
}

function emitRouterPush(path: string) {
  window.dispatchEvent(new CustomEvent('micro-kernel:navigate', { detail: { path } }));
}

function close() {
  visible.value = false;
}

function handler(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault();
    visible.value = !visible.value;
  }
}

onMounted(() => registerKeyboard('cmd+k', handler));
onUnmounted(() => {});
</script>

<style scoped>
.gs-overlay {
  position: fixed; inset: 0; z-index: 99999;
  display: flex; align-items: flex-start; justify-content: center; padding-top: 15vh;
  background: rgba(0, 0, 0, 0.4);
}
.gs-panel {
  width: 560px; max-width: 90vw;
  background: var(--el-bg-color, #fff);
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}
.gs-input-wrap {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.gs-icon { color: var(--el-text-color-placeholder); }
.gs-input {
  flex: 1; border: none; outline: none;
  font-size: 15px; background: transparent;
  color: var(--el-text-color-primary);
}
.gs-kbd {
  padding: 2px 6px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
}
.gs-results { max-height: 420px; overflow-y: auto; padding: 8px 0; }
.gs-item {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 16px;
  cursor: pointer;
  border: none; background: transparent; width: 100%; text-align: left;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.gs-item:last-child { border-bottom: none; }
.gs-item.is-active, .gs-item:hover { background: var(--el-fill-color-light); }
.gs-item-icon { color: var(--el-text-color-placeholder); }
.gs-item-body { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.gs-item-title { font-size: 13px; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gs-item-title :deep(mark) { background: var(--el-color-primary-light-8); color: var(--el-color-primary); border-radius: 2px; padding: 0 2px; }
.gs-item-desc { font-size: 11px; color: var(--el-text-color-secondary); }
.gs-app-badge {
  padding: 2px 8px; border-radius: 10px;
  font-size: 10px; background: var(--el-fill-color-light);
  color: var(--el-text-color-regular); white-space: nowrap;
}
.gs-empty, .gs-tips { text-align: center; padding: 20px; font-size: 12px; color: var(--el-text-color-placeholder); }
.gs-tips { display: flex; gap: 16px; justify-content: center; }
.search-modal-enter-active, .search-modal-leave-active { transition: opacity 0.15s ease; }
.search-modal-enter-from, .search-modal-leave-to { opacity: 0; }
</style>
