<!--
 * auth-title Vue 组件
 *
 * @path comm\effects\common-ui\src\ui\authentication\auth-title.vue
 * @author ydsz-team
 * @since 1.0.0
-->
<template>
  <div class="mb-7 sm:mx-auto sm:w-full sm:max-w-md">
    <h2
      class="text-foreground mb-3 text-3xl font-bold leading-9 tracking-tight lg:text-4xl"
    >
      <slot></slot>
    </h2>

    <p class="text-muted-foreground lg:text-md text-sm">
      <slot name="desc"></slot>
    </p>
  </div>
</template>
