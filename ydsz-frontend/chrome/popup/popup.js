/* Popup —— 快速触达面板：显示概览 + 触发 DevTools */
;(function () {
  /* 查询当前 tab 的快照状态 */
  function queryActiveTab() {
    chrome.tabs.query({ active: true, currentWindow: true }, function (tabs) {
      if (!tabs[0] || !tabs[0].id) return;
      chrome.tabs.sendMessage(tabs[0].id, { target: 'content-script', type: 'kernel:state:request' }).catch(function () {});
      setTimeout(pullSnapshot, 600);
    });
  }

  function pullSnapshot() {
    chrome.runtime.sendMessage({ target: 'background', type: 'devtools:subscribe' }, function (res) {
      if (!res || !res.cached) return;
      var agg = res.cached.aggregate || res.cached;
      var vs = $('#vs'), sa = $('#sa'), kl = $('#kl'), na = $('#na'), mem = $('#mem');

      if (agg.totalApps > 0 || agg.activeApp) {
        vs.innerHTML = '<span class="dot on"></span>已连接'; vs.className = 'value ok';
      } else {
        vs.innerHTML = '<span class="dot off"></span>等待连接'; vs.className = 'value';
      }
      if (agg.activeApp) sa.textContent = agg.activeApp;
      if (agg.keepAlive != null) kl.textContent = agg.keepAlive;
      if (agg.totalApps) na.textContent = agg.totalApps;
      if (agg.memory) mem.textContent = agg.memory;
    });
  }

  /* 打开 DevTools Panel */
  $('#open').addEventListener('click', function () {
    chrome.tabs.query({ active: true, currentWindow: true }, function (tabs) {
      if (tabs[0] && tabs[0].id) {
        chrome.tabs.sendMessage(tabs[0].id, { target: 'content-script', type: 'kernel:state:request' });
        // DevTools 需要用户在当前 tab 主动按 F12；Extension 无法直接打开 F12（安全限制）
      }
    });
    window.close();
  });

  queryActiveTab();
  /* 定期刷新（popup 关闭时停止） */
  var timer = setInterval(queryActiveTab, 3000);
  window.addEventListener('unload', function () { clearInterval(timer); });
})();
