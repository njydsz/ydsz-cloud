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
