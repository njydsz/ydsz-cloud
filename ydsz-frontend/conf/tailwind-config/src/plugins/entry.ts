/**
 * entry 配置模块
 *
 * @path conf\tailwind-config\src\plugins\entry.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import plugin from 'tailwindcss/plugin.js';

/**
 * 生成 `enter-x / enter-y` 系列入场动画工具类的 Tailwind 插件。
 *
 * 为前 5 个子元素（maxChild）按序递增 0.1s 延迟，实现列表依次淡入位移效果，
 * 避免逐个手写动画类；同时注册 enter-x / enter-y 的关键帧。
 */
const enterAnimationPlugin = plugin(({ addUtilities }) => {
  const maxChild = 5;
  const utilities: Record<string, any> = {};
  for (let i = 1; i <= maxChild; i++) {
    const baseDelay = 0.1;
    const delay = `${baseDelay * i}s`;

    utilities[`.enter-x:nth-child(${i})`] = {
      animation: `enter-x-animation 0.3s ease-in-out ${delay} forwards`,
      opacity: '0',
      transform: `translateX(50px)`,
    };

    utilities[`.enter-y:nth-child(${i})`] = {
      animation: `enter-y-animation 0.3s ease-in-out ${delay} forwards`,
      opacity: '0',
      transform: `translateY(50px)`,
    };

    utilities[`.-enter-x:nth-child(${i})`] = {
      animation: `enter-x-animation 0.3s ease-in-out ${delay} forwards`,
      opacity: '0',
      transform: `translateX(-50px)`,
    };

    utilities[`.-enter-y:nth-child(${i})`] = {
      animation: `enter-y-animation 0.3s ease-in-out ${delay} forwards`,
      opacity: '0',
      transform: `translateY(-50px)`,
    };
  }

  // 添加动画关键帧
  addUtilities(utilities);
  addUtilities({
    '@keyframes enter-x-animation': {
      to: {
        opacity: '1',
        transform: 'translateX(0)',
      },
    },
    '@keyframes enter-y-animation': {
      to: {
        opacity: '1',
        transform: 'translateY(0)',
      },
    },
  });
});

export { enterAnimationPlugin };
