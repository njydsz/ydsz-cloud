const conns = new Map();
const cache = new Map();
chrome.runtime.onMessage.addListener((msg, sender, send) => {
  if (msg.target !== 'background') return false;
  switch (msg.type) {
    case 'content-script:ready':
      if (sender.tab?.id) { conns.set(sender.tab.id, { url: msg.payload?.url, ts: Date.now() }); bcast('kernel:tab:activated', { tabId: sender.tab.id }); }
      send({ ok: true }); break;
    case 'kernel:state:response': case 'kernel:health:response': case 'kernel:event':
      if (msg.payload?.appName) cache.set(msg.payload.appName, { ...msg.payload, _t: Date.now() });
      bcast(msg.type, msg.payload, msg._id); send({ ok: true }); break;
    case 'devtools:command':
      chrome.tabs.sendMessage(msg.tabId, { target: 'content-script', type: msg.type, payload: msg.payload }).then(send).catch((e) => send({ ok: false, error: e.message }));
      return true;
    case 'devtools:subscribe': send({ ok: true, cached: getCached() }); break;
    default: send({ ok: false });
  }
});
function getCached() { const o = {}; const now = Date.now(); for (const [k, v] of cache) if (now - v._t < 30000) o[k] = v; return o; }
function bcast(type, payload, _id) { chrome.runtime.sendMessage({ target: 'devtools', type, payload, _id }).catch(() => {}); }
chrome.tabs.onRemoved.addListener((id) => { conns.delete(id); bcast('kernel:tab:deactivated', { tabId: id }); });
chrome.runtime.onConnect.addListener((port) => { if (port.name !== 'micro-kernel-devtools') return; port.onMessage.addListener((m) => { if (m.type === 'ping') port.postMessage({ type: 'pong' }); }); });
