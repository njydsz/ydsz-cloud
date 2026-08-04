/* Popup —— 快速触达面板 */
;(function () {
  chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
    if (tabs[0]?.id) {
      chrome.tabs.sendMessage(tabs[0].id, { target: 'content-script', type: 'kernel:state:request' }).catch(() => {});
    }
  });
  chrome.runtime.sendMessage({ target: 'background', type: 'devtools:subscribe' }, (res) => {
    const d = res?.cached?.aggregate;
    if (d) {
      document.getElementById('sa').textContent = d.activeApp || '—';
      document.getElementById('kl').textContent = d.keepAlive || 0;
      document.getElementById('na').textContent = d.total || 0;
    }
  });
  document.getElementById('b').addEventListener('click', () => {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        chrome.scripting?.executeScript({
          target: { tabId: tabs[0].id },
          func: () => !!window.__YDSZ_MICRO_KERNEL__ACTIVE__,
        });
      }
    });
    window.close();
  });
})();
