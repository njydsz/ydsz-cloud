<!--
 * fallback Vue 组件
 *
 * @path comm\effects\common-ui\src\ui\fallback\fallback.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { FallbackProps } from './fallback';

import { computed, defineAsyncComponent } from 'vue';
import { useRouter } from 'vue-router';

import { ArrowLeft, RotateCw } from '@ydsz/icons';
import { $t } from '@ydsz/locales';

import { YDSZButton } from '@ydsz-core/shadcn-ui';

interface Props extends FallbackProps {}

defineOptions({
  name: 'Fallback',
});

const props = withDefaults(defineProps<Props>(), {
  description: '',
  homePath: '/',
  image: '',
  showBack: true,
  status: 'coming-soon',
  title: '',
});

const Icon403 = defineAsyncComponent(() => import('./icons/icon-403.vue'));
const Icon404 = defineAsyncComponent(() => import('./icons/icon-404.vue'));
const Icon500 = defineAsyncComponent(() => import('./icons/icon-500.vue'));
const IconHello = defineAsyncComponent(
  () => import('./icons/icon-coming-soon.vue'),
);
const IconOffline = defineAsyncComponent(
  () => import('./icons/icon-offline.vue'),
);
const IconEmpty = defineAsyncComponent(
  () => import('./icons/icon-empty.vue'),
);

const titleText = computed(() => {
  if (props.title) {
    return props.title;
  }

  switch (props.status) {
    case '403': {
      return $t('ui.fallback.forbidden');
    }
    case '404': {
      return $t('ui.fallback.pageNotFound');
    }
    case '500': {
      return $t('ui.fallback.internalError');
    }
    case 'coming-soon': {
      return $t('ui.fallback.comingSoon');
    }
    case 'offline': {
      return $t('ui.fallback.offlineError');
    }
    case 'empty': {
      return '暂无数据';
    }
    default: {
      return '';
    }
  }
});

const descText = computed(() => {
  if (props.description) {
    return props.description;
  }
  switch (props.status) {
    case '403': {
      return $t('ui.fallback.forbiddenDesc');
    }
    case '404': {
      return $t('ui.fallback.pageNotFoundDesc');
    }
    case '500': {
      return $t('ui.fallback.internalErrorDesc');
    }
    case 'offline': {
      return $t('ui.fallback.offlineErrorDesc');
    }
    case 'empty': {
      return '当前页面暂无数据';
    }
    default: {
      return '';
    }
  }
});

const fallbackIcon = computed(() => {
  switch (props.status) {
    case '403': {
      return Icon403;
    }
    case '404': {
      return Icon404;
    }
    case '500': {
      return Icon500;
    }
    case 'coming-soon': {
      return IconHello;
    }
    case 'offline': {
      return IconOffline;
    }
    default: {
      return null;
    }
  }
});

const showBack = computed(() => {
  return props.status === '403' || props.status === '404';
});

const showRefresh = computed(() => {
  return props.status === '500' || props.status === 'offline';
});

const { push } = useRouter();

// 返回首页
function back() {
  push(props.homePath);
}

function refresh() {
  location.reload();
}
</script>

<template>
  <div
    class="flex size-full flex-col items-center justify-center duration-300"
    role="main"
    aria-labelledby="fallback-title"
    aria-describedby="fallback-description"
  >
    <img
      v-if="image"
      :src="image"
      :alt="titleText"
      class="md:1/3 w-1/2 lg:w-1/4"
      role="img"
    />
    <component
      :is="fallbackIcon"
      v-else-if="fallbackIcon"
      class="md:1/3 h-1/3 w-1/2 lg:w-1/4"
      aria-hidden="true"
    />
    <div class="flex-col-center">
      <slot v-if="$slots.title" name="title"></slot>
      <p
        v-else-if="titleText"
        id="fallback-title"
        class="text-foreground mt-8 text-2xl md:text-3xl lg:text-4xl"
        role="heading"
        aria-level="1"
      >
        {{ titleText }}
      </p>
      <slot v-if="$slots.describe" name="describe"></slot>
      <p
        v-else-if="descText"
        id="fallback-description"
        class="text-muted-foreground md:text-md my-4 lg:text-lg"
      >
        {{ descText }}
      </p>
      <slot v-if="$slots.action" name="action"></slot>
      <YDSZButton
        v-else-if="showBack"
        size="lg"
        @click="back"
        :aria-label="$t('common.backToHome')"
      >
        <ArrowLeft class="mr-2 size-4" aria-hidden="true" />
        {{ $t('common.backToHome') }}
      </YDSZButton>
      <YDSZButton
        v-else-if="showRefresh"
        size="lg"
        @click="refresh"
        :aria-label="$t('common.refresh')"
      >
        <RotateCw class="mr-2 size-4" aria-hidden="true" />
        {{ $t('common.refresh') }}
      </YDSZButton>
    </div>
  </div>
</template>
