<script lang="ts" setup>
import { YDSZSpinner } from '@ydsz-core/shadcn-ui';

import { useContentSpinner } from './use-content-spinner';

defineOptions({ name: 'LayoutContentSpinner' });

const { spinning } = useContentSpinner();
</script>
<template>
  <YDSZSpinner :spinning="spinning" />
</template>
