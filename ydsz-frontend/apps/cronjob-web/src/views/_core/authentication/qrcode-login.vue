<script lang="ts" setup>
import { AuthenticationQrCodeLogin } from '@ydsz/common-ui';
import { LOGIN_PATH } from '@ydsz/constants';

defineOptions({ name: 'QrCodeLogin' });
</script>

<template>
  <AuthenticationQrCodeLogin :login-path="LOGIN_PATH" />
</template>
