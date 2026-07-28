<!--
 * basic 布局组件
 *
 * @path apps\cronjob-web\src\layouts\basic.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
import { watch } from 'vue';

import { useWatermark } from '@ydsz/hooks';
import { preferences } from '@ydsz/preferences';
import { useUserStore } from '@ydsz/stores';

const userStore = useUserStore();
const { destroyWatermark, updateWatermark } = useWatermark();

watch(
  () => preferences.app.watermark,
  async (enable) => {
    if (enable) {
      await updateWatermark({
        content: `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <!-- 只渲染路由视图，不渲染布局 -->
  <RouterView />
</template>
