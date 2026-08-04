/* DevTools Panel 入口 —— 在 Chrome DevTools 创建 Micro Kernel 面板 */
chrome.devtools.panels.create(
  'Micro Kernel',
  '',
  'devtools/panel.html',
  (panel) => {
    console.log('[YDSZ] Micro Kernel DevTools panel created.');
    panel.onShown.addListener(() => {
      chrome.runtime.sendMessage({ target: 'background', type: 'devtools:subscribe', tabId: chrome.devtools.inspectedWindow.tabId });
    });
  }
);
chrome.runtime.onMessage.addListener((msg) => {
  if (msg.target !== 'devtools') return;
  chrome.devtools.inspectedWindow.eval(`window.postMessage({ source: 'ext-bg', detail: ${JSON.stringify(msg).replace(/\\/g,'\\\\').replace(/'/g,"\\'") }},'*')`);
});
