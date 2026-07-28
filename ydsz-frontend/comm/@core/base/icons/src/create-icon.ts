/**
 * create-icon 模块
 *
 * @path comm\@core\base\icons\src\create-icon.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineComponent, h } from 'vue';

import { Icon } from '@iconify/vue';

function createIconifyIcon(icon: string) {
  return defineComponent({
    name: `Icon-${icon}`,
    setup(props, { attrs }) {
      return () => h(Icon, { icon, ...props, ...attrs });
    },
  });
}

export { createIconifyIcon };
