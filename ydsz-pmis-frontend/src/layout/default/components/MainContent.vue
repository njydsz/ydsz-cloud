<!--
  @file 主内容容器
  @description 内容区外壳，提供 padding 与滚动容器；通过 slot 接收 RouterView 内容
  @module layout/default/components/MainContent
-->
<template>
  <main class="main-content">
    <slot />
  </main>
</template>

<style lang="scss" scoped>
.main-content {
  flex: 1;
  padding: $spacing-md;
  background: $bg-page;
  overflow: auto;
}
</style>
