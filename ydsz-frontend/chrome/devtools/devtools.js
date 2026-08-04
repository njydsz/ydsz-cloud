/* DevTools 入口 —— 在 Chrome DevTools 中创建名为 "Micro Kernel" 的自定义面板 */
chrome.devtools.panels.create(
  'Micro Kernel',
  '',
  'devtools/panel.html',
  function (panel) {
    console.log('[YDSZ] Micro Kernel panel created');

    // 监听 Extension -> DevTools 的消息
    chrome.runtime.onMessage.addListener(function (msg) {
      if (msg.target !== 'devtools') return;
      // 通过 inspectedWindow.eval 转发到页面（因 DevTools Panel 与 background 隔离）
      var jsonStr = JSON.stringify(msg).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
      var code = "window.postMessage({ source: 'ext-bg', detail: " + jsonStr + " }, '*')";
      chrome.devtools.inspectedWindow.eval(code).catch(function () {});
    });

    // Panel 可见性变化
    panel.onShown.addListener(function () {
      // 拉取最新缓存
      chrome.runtime.sendMessage({
        target: 'background',
        type: 'devtools:subscribe',
        tabId: chrome.devtools.inspectedWindow.tabId
      });
    });
    panel.onHidden.addListener(function () {
      chrome.runtime.sendMessage({ target: 'background', type: 'devtools:lost-focus' });
    });
  }
);
