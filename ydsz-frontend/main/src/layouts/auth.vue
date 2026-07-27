<script lang="ts" setup>
import { AuthPageLayout } from '@ydsz/layouts';

import { $t } from '#/locales';
</script>

<template>
  <AuthPageLayout
    app-name=""
    logo=""
    :page-description="$t('authentication.pageDesc')"
    :page-title="$t('authentication.pageTitle')"
  >
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>
