/* Content Script —— 注入页面并桥接 micro-kernel <-> Extension */
;(function () {
  function injectBridge() {
    const s = document.createElement('script');
    s.src = chrome.runtime.getURL('kernel-bridge.js');
    s.onload = () => s.remove();
    (document.head || document.documentElement).appendChild(s);
  }
  if (document.contentType === 'text/html') injectBridge();

  // Page -> Background
  window.addEventListener('message', (e) => {
    if (e.source !== window || !e.data || e.data.channel !== '__YDSZ_MICRO_KERNEL__CHANNEL') return;
    if (e.data.source !== 'page') return;
    chrome.runtime.sendMessage({ target: 'background', type: e.data.type, payload: e.data.payload }).catch(() => {});
  });
  window.addEventListener('__YDSZ_MICRO_KERNEL__:out', (e) => {
    chrome.runtime.sendMessage({ target: 'background', type: e.detail?.type, payload: e.detail?.payload }).catch(() => {});
  });

  // Background -> Page
  chrome.runtime.onMessage.addListener((msg, _s, sendResponse) => {
    if (msg.target !== 'content-script') return;
    window.postMessage({ channel: '__YDSZ_MICRO_KERNEL__CHANNEL', source: 'extension', type: msg.type, payload: msg.payload, _id: msg._id }, '*');
    sendResponse({ ok: true });
  });

  function report() { chrome.runtime.sendMessage({ target: 'background', type: 'content-script:ready', payload: { url: location.href, ts: Date.now() } }); }
  window.addEventListener('load', report);
  setTimeout(report, 100);
})();
