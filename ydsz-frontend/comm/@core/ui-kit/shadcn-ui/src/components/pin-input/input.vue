<!--
 * input 通用组件
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\pin-input\input.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<script setup lang="ts">
import type { PinInputProps } from './types';

import { computed, onBeforeUnmount, ref, useId, watch } from 'vue';

import { PinInput, PinInputGroup, PinInputInput } from '../../ui';
import { YDSZButton } from '../button';

defineOptions({
  inheritAttrs: false,
});

const {
  codeLength = 6,
  createText = async () => {},
  disabled = false,
  handleSendCode = async () => {},
  loading = false,
  maxTime = 60,
} = defineProps<PinInputProps>();

const emit = defineEmits<{
  complete: [];
  sendError: [error: any];
}>();

const timer = ref<ReturnType<typeof setTimeout>>();

const modelValue = defineModel<string>();

const inputValue = ref<string[]>([]);
const countdown = ref(0);

const btnText = computed(() => {
  const countdownValue = countdown.value;
  return createText?.(countdownValue);
});

const btnLoading = computed(() => {
  return loading || countdown.value > 0;
});

watch(
  () => modelValue.value,
  () => {
    inputValue.value = modelValue.value?.split('') ?? [];
  },
);

watch(inputValue, (val) => {
  modelValue.value = val.join('');
});

function handleComplete(e: string[]) {
  modelValue.value = e.join('');
  emit('complete');
}

async function handleSend(e: Event) {
  try {
    e?.preventDefault();
    await handleSendCode();
    countdown.value = maxTime;
    startCountdown();
  } catch (error) {
    console.error('Failed to send code:', error);
    // Consider emitting an error event or showing a notification
    emit('sendError', error);
  }
}

function startCountdown() {
  if (countdown.value > 0) {
    timer.value = setTimeout(() => {
      countdown.value--;
      startCountdown();
    }, 1000);
  }
}

onBeforeUnmount(() => {
  countdown.value = 0;
  clearTimeout(timer.value);
});

const id = useId();
</script>

<template>
  <PinInput
    :id="id"
    v-model="inputValue"
    :disabled="disabled"
    class="flex w-full justify-between"
    otp
    placeholder="○"
    type="number"
    aria-label="验证码输入"
    @complete="handleComplete"
  >
    <div class="relative flex w-full">
      <PinInputGroup class="mr-2" aria-label="验证码数字输入框">
        <PinInputInput
          v-for="(item, index) in codeLength"
          :key="item"
          :index="index"
          :aria-label="`第 ${index + 1} 位验证码`"
        />
      </PinInputGroup>
      <YDSZButton
        :disabled="disabled"
        :loading="btnLoading"
        class="flex-grow"
        size="lg"
        variant="outline"
        :aria-label="btnLoading ? '正在发送验证码' : '发送验证码'"
        @click="handleSend"
      >
        {{ btnText }}
      </YDSZButton>
    </div>
  </PinInput>
</template>
