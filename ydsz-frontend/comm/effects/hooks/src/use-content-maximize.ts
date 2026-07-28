/**
 * use-content-maximize 组合式函数
 *
 * @path comm\effects\hooks\src\use-content-maximize.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { updatePreferences, usePreferences } from '@ydsz/preferences';
/**
 * 主体区域最大化
 */
export function useContentMaximize() {
  const { contentIsMaximize } = usePreferences();

  function toggleMaximize() {
    const isMaximize = contentIsMaximize.value;

    updatePreferences({
      header: {
        hidden: !isMaximize,
      },
      sidebar: {
        hidden: !isMaximize,
      },
    });
  }
  return {
    contentIsMaximize,
    toggleMaximize,
  };
}
