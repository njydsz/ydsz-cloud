/**
 * 应用偏好配置
 *
 * @path comm\effects\layouts\src\widgets\preferences\use-open-preferences.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { ref } from 'vue';

const openPreferences = ref(false);

function useOpenPreferences() {
  function handleOpenPreference() {
    openPreferences.value = true;
  }

  return {
    handleOpenPreference,
    openPreferences,
  };
}

export { useOpenPreferences };
