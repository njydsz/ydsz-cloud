;(function () {
  const port = chrome.runtime.connect({ name: 'micro-kernel-devtools' });
  let state = { activeApp: null, keepAlive: 0, total: 0, memory: 'N/A', apps: [], caps: {} };
  const $ = (s) => document.querySelector(s);
  const al = $('#al'), elog = $('#elog');
  function render() {
    $('#sa').textContent = state.activeApp || '—';
    $('#kl').textContent = state.keepAlive;
    $('#mem').textContent = state.memory;
    $('#na').textContent = state.total;
    $('#kv').textContent = state.caps?.kernelVersion || 'v—';
    if (!state.apps.length) { al.innerHTML = '<div class="empty">未检测到运行时</div>'; return; }
    al.innerHTML = state.apps.map(a => `
      <div class="ar">
        <span class="dot ${a.status}"></span>
        <span class="an" title="入口:${a.entry||'—'}">${a.name}</span>
        <span style="color:#909399;font-size:11px">${a.status}</span>
        ${a.loadDuration ? `<span style="color:#c0c4cc">${a.loadDuration}ms</span>` : ''}
        <button class="act danger" data-act="unmount" data-app="${a.name}">卸载</button>
        <button class="act primary" data-act="reload" data-app="${a.name}">重载</button>
      </div>`).join('');
    al.querySelectorAll('button[data-act]').forEach(b => b.addEventListener('click', () => {
      const { act, app } = b.dataset;
      send({ type: act === 'unmount' ? 'kernel:unmount' : 'kernel:reload', payload: { appName: app } });
    }));
  }
  function log(t, lv = 'info') {
    const el = document.createElement('div'); el.className = 'le ' + (lv === 'err' ? 'err' : '');
    el.textContent = `[${new Date().toLocaleTimeString()}] ${t}`;
    elog.appendChild(el); elog.scrollTop = elog.scrollHeight;
    if (elog.children.length > 100) elog.removeChild(elog.firstChild);
  }
  function send(msg) { chrome.runtime.sendMessage({ target: 'background', ...msg }).catch(e => log(e.message, 'err')); }
  chrome.runtime.onMessage.addListener(m => handleMsg(m));
  window.addEventListener('message', e => { if (e.source === window && e.data?.source === 'ext-bg') handleMsg(e.data.detail); });
  function handleMsg(m) {
    if (!m) return;
    if (m.type === 'kernel:state:response') { state = { ...state, ...m.payload }; render(); }
    else if (m.type === 'kernel:health:response') log(`健康检查: ${JSON.stringify(m.payload)}`, 'info');
    else if (m.type === 'kernel:event') log(`${m.payload?.eventName || 'event'}`);
    else if (m.type === 'kernel:tab:activated') log(`Tab #${m.payload.tabId} 就绪`);
    else if (m.type === 'kernel:tab:deactivated') log(`Tab #${m.payload.tabId} 离线`, 'err');
  }
  $('#br').addEventListener('click', () => { send({ type: 'kernel:refresh-registry' }); log('触发注册刷新'); });
  $('#bc').addEventListener('click', () => { send({ type: 'kernel:clear-cache' }); log('触发缓存清理'); });
  $('#bh').addEventListener('click', () => send({ type: 'kernel:health:request' }));
  setInterval(() => port.postMessage({ type: 'ping' }), 25000);
  port.onMessage.addListener(m => { if (m.type === 'pong') {} });
  setTimeout(() => send({ type: 'kernel:state:request' }), 300);
  log('DevTools Panel 已启动');
})();
