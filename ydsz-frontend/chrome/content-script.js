/* Content Script: page <-> background 之间的消息中转 + bridge 注入 */
;(function () {
  /* 1. 主文档注入 kernel-bridge.js（让页面侧微内核可以主动推送事件到 Extension） */
  function injectBridge() {
    if (document.contentType && document.contentType !== 'text/html') return;
    var s = document.createElement('script');
    s.src = chrome.runtime.getURL('kernel-bridge.js');
    s.onload = function () { s.remove(); };
    (document.head || document.documentElement).appendChild(s);
  }
  injectBridge();

  /* 2. page -> background（主动推送） */
  window.addEventListener('message', function (e) {
    if (e.source !== window || !e.data) return;
    if (e.data.channel !== '__YDSZ_MICRO_KERNEL__CHANNEL') return;
    if (e.data.source !== 'page') return;
    chrome.runtime.sendMessage({
      target: 'background',
      type: e.data.type,
      payload: e.data.payload,
      _id: e.data._id
    }).catch(function () {});
  });

  window.addEventListener('__YDSZ_MICRO_KERNEL__:out', function (e) {
    var d = e.detail;
    if (!d) return;
    chrome.runtime.sendMessage({ target: 'background', type: d.type, payload: d.payload }).catch(function () {});
  });

  /* 3. background -> page（命令转发） */
  chrome.runtime.onMessage.addListener(function (msg, _sender, sendResponse) {
    if (msg.target !== 'content-script') return;
    window.postMessage({
      channel: '__YDSZ_MICRO_KERNEL__CHANNEL',
      source: 'extension',
      type: msg.type,
      payload: msg.payload,
      _id: msg._id
    }, '*');
    sendResponse({ ok: true });
  });

  /* 4. 页面就绪通知 background */
  var report = function () {
    chrome.runtime.sendMessage({
      target: 'background',
      type: 'content-script:ready',
      payload: { url: location.href, ts: Date.now() }
    });
  };
  window.addEventListener('load', report);
  setTimeout(report, 100);
})();
