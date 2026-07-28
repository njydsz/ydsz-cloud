<!--
 * 用户角色分配弹窗 — 为用户分配或取消分配角色
 *
 * @path apps\userinfo-web\src\views\system\user\role-assign.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script lang="ts" setup>
/**
 * 用户角色分配
 * <p>用户角色分配的弹窗组件，分配/取消分配角色。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RoleApi } from '#/api/role';

import { useVbenModal } from '@ydsz/common-ui';
import { ElMessage, ElTransfer } from 'element-plus';
import { ref, watch } from 'vue';

import { assignUserRolesApi } from '#/api/user';

const emit = defineEmits<{ success: [] }>();

const userId = ref('');
const username = ref('');
const roleList = ref<RoleApi.RoleVO[]>([]);
const selectedRoleIds = ref<string[]>([]);

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{
      userId: string;
      username: string;
      roleList: RoleApi.RoleVO[];
      selectedRoleIds: string[];
    }>();
    userId.value = data.userId;
    username.value = data.username;
    roleList.value = data.roleList || [];
    selectedRoleIds.value = [...(data.selectedRoleIds || [])];
  },
  onConfirm: async () => {
    modalApi.lock();
    try {
      await assignUserRolesApi(userId.value, selectedRoleIds.value);
      ElMessage.success('角色分配成功');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const transferData = ref<{ label: string; key: string }[]>([]);
watch(roleList, (list) => {
  transferData.value = list.map((r) => ({
    label: `${r.roleName} (${r.roleCode})`,
    key: r.id,
  }));
}, { immediate: true });

</script>

<template>
  <Modal :title="`分配角色 - ${username}`" class="w-[600px]">
    <div class="py-4">
      <ElTransfer
        v-model="selectedRoleIds"
        :data="transferData"
        :titles="['可选角色', '已分配角色']"
        filterable
        filter-placeholder="搜索角色"
      />
    </div>
  </Modal>
</template>
