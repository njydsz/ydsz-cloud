/**
 * 按需预加载策略 — hover 菜单项时 prefetch 子应用入口
 *
 * 对标飞书微前端方案，避免启动时全量预加载 9 个子应用影响首屏。
 * 从 bootstrap.ts 中抽取为独立模块，与 MicroRuntime 内核解耦。
 *
 * @path main/src/qiankun/prefetch.ts
 * @author ydsz-team
 * @since 3.0.0
 */
export function setupHoverPrefetch() {
  const prefetched = new Set<string>();
  let prefetchTimer: null | ReturnType<typeof setTimeout> = null;

  const routePrefixMap: Record<string, string> = {
    '/ydsz-user': import.meta.env.DEV ? '//localhost:5601' : '/ydsz-userinfo-web/',
    '/ydsz-sys': import.meta.env.DEV ? '//localhost:5602' : '/ydsz-system-web/',
    '/ydsz-proj': import.meta.env.DEV ? '//localhost:5603' : '/ydsz-project-web/',
    '/ydsz-msg': import.meta.env.DEV ? '//localhost:5604' : '/ydsz-message-web/',
    '/ydsz-cron': import.meta.env.DEV ? '//localhost:5605' : '/ydsz-cronjob-web/',
    '/ydsz-flow': import.meta.env.DEV ? '//localhost:5606' : '/ydsz-workflow-web/',
    '/ydsz-wiki': import.meta.env.DEV ? '//localhost:5607' : '/ydsz-nextwiki-web/',
    '/ydsz-rule': import.meta.env.DEV ? '//localhost:5608' : '/ydsz-literule-web/',
    '/ydsz-ai': import.meta.env.DEV ? '//localhost:5610' : '/ydsz-agent-web/',
  };

  function prefetchApp(entry: string) {
    if (prefetched.has(entry)) return;
    prefetched.add(entry);

    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = entry;
    link.as = 'document';
    document.head.appendChild(link);
  }

  document.addEventListener(
    'mouseover',
    (event) => {
      const target = event.target as HTMLElement;
      if (!target) return;

      const anchor = target.closest('a');
      if (!anchor) return;

      const href = anchor.getAttribute('href') || '';

      for (const [prefix, entry] of Object.entries(routePrefixMap)) {
        if (href.startsWith(prefix)) {
          if (prefetchTimer) clearTimeout(prefetchTimer);
          prefetchTimer = setTimeout(() => prefetchApp(entry), 200);
          break;
        }
      }
    },
    { capture: true },
  );

  function prefetchOnFirstInteraction() {
    const firstEntry = routePrefixMap['/ydsz-user'];
    if (firstEntry) {
      setTimeout(() => prefetchApp(firstEntry), 3000);
    }
    document.removeEventListener('click', prefetchOnFirstInteraction);
    document.removeEventListener('keydown', prefetchOnFirstInteraction);
  }

  document.addEventListener('click', prefetchOnFirstInteraction, { once: true });
  document.addEventListener('keydown', prefetchOnFirstInteraction, { once: true });

  console.info('[MicroRuntime] Hover-based prefetch strategy installed');
}
