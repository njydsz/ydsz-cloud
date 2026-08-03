<!--
 * global-search 通用组件
 *
 * @path main\src\components\global-search.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import type { SearchApi } from '#/api/core/search';

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Search } from '@element-plus/icons-vue';
import {
  ElDialog,
  ElEmpty,
  ElIcon,
  ElInput,
  ElLink,
  ElTag,
} from 'element-plus';

import { globalSearchApi, searchSuggestApi } from '#/api/core/search';

const router = useRouter();

/** 搜索弹窗显隐（v-model:visible） */
const visible = defineModel<boolean>('visible', { default: false });

const keyword = ref('');
const loading = ref(false);
const results = ref<SearchApi.SearchResultItem[]>([]);
const suggestions = ref<string[]>([]);

let searchTimer: null | ReturnType<typeof setTimeout> = null;

watch(keyword, (val) => {
  if (searchTimer) clearTimeout(searchTimer);
  if (!val || val.trim().length < 2) {
    results.value = [];
    suggestions.value = [];
    return;
  }

  searchTimer = setTimeout(async () => {
    loading.value = true;
    try {
      // 获取搜索建议
      searchSuggestApi(val.trim()).then((s) => {
        suggestions.value = s || [];
      });

      // 执行搜索
      const res = await globalSearchApi({
        keyword: val.trim(),
        pageNum: 1,
        pageSize: 20,
      });
      results.value = res.items || [];
    } catch {
      results.value = [];
    } finally {
      loading.value = false;
    }
  }, 300);
});

function handleSelectResult(item: SearchApi.SearchResultItem) {
  if (item.url) {
    router.push(item.url);
  }
  visible.value = false;
  keyword.value = '';
  results.value = [];
}

function handleSuggestion(suggestion: string) {
  keyword.value = suggestion;
}

// 全局快捷键 Ctrl+K / Cmd+K
function handleKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault();
    visible.value = true;
  }
  if (e.key === 'Escape') {
    visible.value = false;
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', handleKeydown);
}

/** 模块标识到标签颜色的映射，用于结果列表中模块标签着色 */
const moduleColorMap: Record<string, string> = {
  project: 'primary',
  workflow: 'success',
  message: 'warning',
  userinfo: 'info',
  system: 'danger',
  nextwiki: 'primary',
  literule: 'success',
  cronjob: 'warning',
  agent: 'info',
};
</script>

<template>
  <ElDialog
    v-model="visible"
    title="全局搜索"
    width="640px"
    :show-close="true"
    append-to-body
    class="global-search-dialog"
  >
    <ElInput
      v-model="keyword"
      placeholder="搜索项目、合同、任务、文件、规则...（Ctrl+K）"
      size="large"
      clearable
      :prefix-icon="Search"
    />
    <div class="mt-3 max-h-[400px] overflow-y-auto">
      <!-- 搜索建议 -->
      <div
        v-if="suggestions.length > 0 && results.length === 0 && !loading"
        class="mb-3"
      >
        <div class="mb-2 text-xs text-gray-400">搜索建议</div>
        <div class="flex flex-wrap gap-2">
          <ElTag
            v-for="s in suggestions"
            :key="s"
            class="cursor-pointer"
            effect="plain"
            @click="handleSuggestion(s)"
          >
            {{ s }}
          </ElTag>
        </div>
      </div>

      <!-- 搜索结果 -->
      <div v-if="loading" class="py-8 text-center text-gray-400">
        搜索中...
      </div>

      <ElEmpty
        v-else-if="results.length === 0 && keyword.trim().length >= 2"
        description="未找到相关结果"
      />

      <div v-else class="space-y-2">
        <div
          v-for="item in results"
          :key="item.id"
          class="cursor-pointer rounded-lg border border-gray-100 p-3 transition-colors hover:border-blue-300 hover:bg-blue-50"
          @click="handleSelectResult(item)"
        >
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-800">{{ item.title }}</span>
            <ElTag
              :type="moduleColorMap[item.module] || 'info'"
              size="small"
            >
              {{ item.module }}
            </ElTag>
          </div>
          <div
            v-if="item.snippet"
            class="mt-1 text-sm text-gray-500 line-clamp-2"
            v-safe-html="item.highlight || item.snippet"
          />
        </div>
      </div>
    </div>
  </ElDialog>
</template>

<style scoped>
.global-search-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}
</style>
