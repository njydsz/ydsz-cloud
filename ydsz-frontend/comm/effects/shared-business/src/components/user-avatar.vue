<script lang="ts" setup>
/**
 * 用户头像组件 — 含在线状态指示
 */
import { computed } from 'vue';

interface Props {
  name?: string;
  avatar?: string;
  userId?: string;
  online?: boolean;
  size?: number;
}

const props = withDefaults(defineProps<Props>(), {
  name: '',
  avatar: '',
  online: false,
  size: 32,
});

const displayName = computed(() => {
  if (props.name) return props.name.slice(0, 2);
  return props.userId?.slice(-2) || '??';
});
</script>

<template>
  <div class="user-avatar" :style="{ '--avatar-size': size + 'px' }">
    <el-avatar :size="size" :src="avatar || undefined">
      {{ displayName }}
    </el-avatar>
    <span v-if="online" class="online-dot" />
  </div>
</template>

<style scoped>
.user-avatar {
  position: relative;
  display: inline-flex;
}
.online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a;
  border: 2px solid #fff;
}
</style>
