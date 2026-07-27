<script lang="ts" setup>
import type { UserApi } from '#/api/user';
import type { CompanyApi } from '#/api/company';
import type { PostApi } from '#/api/post';

import { useVbenModal } from '@ydsz/common-ui';
import { ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElSelect, ElOption, ElTreeSelect, ElRadioGroup, ElRadio } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';

import { createUserApi, updateUserApi } from '#/api/user';

const emit = defineEmits<{ success: [] }>();

const formRef = ref();
const isEdit = ref(false);
const deptTreeData = ref<any[]>([]);
const companyList = ref<CompanyApi.CompanyVO[]>([]);
const postList = ref<PostApi.PostVO[]>([]);

const formData = reactive({
  id: '',
  username: '',
  password: '',
  realName: '',
  nickname: '',
  email: '',
  phone: '',
  gender: 0,
  deptId: '',
  postId: '',
  companyId: '',
  status: 1,
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
};

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen) => {
    if (!isOpen) return;
    const data = modalApi.getData<{
      record?: UserApi.UserAccountVO;
      deptTreeData: any[];
      companyList: CompanyApi.CompanyVO[];
      postList: PostApi.PostVO[];
    }>();
    deptTreeData.value = data.deptTreeData || [];
    companyList.value = data.companyList || [];
    postList.value = data.postList || [];

    if (data.record) {
      isEdit.value = true;
      Object.assign(formData, {
        id: data.record.id,
        username: data.record.username,
        password: '',
        realName: data.record.realName,
        nickname: data.record.nickname || '',
        email: data.record.email || '',
        phone: data.record.phone || '',
        gender: data.record.gender || 0,
        deptId: data.record.deptId || '',
        postId: data.record.postId || '',
        companyId: data.record.companyId || '',
        status: data.record.status,
      });
    } else {
      isEdit.value = false;
      Object.assign(formData, {
        id: '',
        username: '',
        password: '',
        realName: '',
        nickname: '',
        email: '',
        phone: '',
        gender: 0,
        deptId: '',
        postId: '',
        companyId: '',
        status: 1,
      });
    }
  },
  onConfirm: async () => {
    try {
      await formRef.value?.validate();
    } catch {
      return;
    }

    modalApi.lock();
    try {
      if (isEdit.value) {
        const { password: _pw, ...updateData } = formData;
        await updateUserApi(updateData as any);
        ElMessage.success('更新成功');
      } else {
        await createUserApi(formData as any);
        ElMessage.success('创建成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
});

const title = computed(() => (isEdit.value ? '编辑用户' : '新增用户'));
</script>

<template>
  <Modal :title="title">
    <ElForm
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      label-position="right"
    >
      <ElFormItem label="用户名" prop="username">
        <ElInput
          v-model="formData.username"
          placeholder="请输入用户名"
          :disabled="isEdit"
        />
      </ElFormItem>
      <ElFormItem v-if="!isEdit" label="密码" prop="password">
        <ElInput
          v-model="formData.password"
          type="password"
          placeholder="请输入密码"
          show-password
        />
      </ElFormItem>
      <ElFormItem label="真实姓名" prop="realName">
        <ElInput v-model="formData.realName" placeholder="请输入真实姓名" />
      </ElFormItem>
      <ElFormItem label="昵称">
        <ElInput v-model="formData.nickname" placeholder="请输入昵称" />
      </ElFormItem>
      <ElFormItem label="手机号">
        <ElInput v-model="formData.phone" placeholder="请输入手机号" />
      </ElFormItem>
      <ElFormItem label="邮箱">
        <ElInput v-model="formData.email" placeholder="请输入邮箱" />
      </ElFormItem>
      <ElFormItem label="性别">
        <ElRadioGroup v-model="formData.gender">
          <ElRadio :value="1">男</ElRadio>
          <ElRadio :value="2">女</ElRadio>
          <ElRadio :value="0">未知</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="部门">
        <ElTreeSelect
          v-model="formData.deptId"
          :data="deptTreeData"
          :props="{ label: 'label', children: 'children' }"
          node-key="id"
          check-strictly
          placeholder="请选择部门"
          clearable
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="岗位">
        <ElSelect
          v-model="formData.postId"
          placeholder="请选择岗位"
          clearable
          class="w-full"
        >
          <ElOption
            v-for="item in postList"
            :key="item.id"
            :label="item.postName"
            :value="item.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="公司">
        <ElSelect
          v-model="formData.companyId"
          placeholder="请选择公司"
          clearable
          class="w-full"
        >
          <ElOption
            v-for="item in companyList"
            :key="item.id"
            :label="item.companyName"
            :value="item.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态">
        <ElRadioGroup v-model="formData.status">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
  </Modal>
</template>
