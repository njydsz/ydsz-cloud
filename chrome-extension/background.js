/* Background Service Worker — 路由中转 + 连接状态维护 */
const connections = new Map();
const stateCache = new Map();

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.target !== 'background') return false;
  switch (msg.type) {
    case 'content-script:ready':
      if (sender.tab?.id) {
        connections.set(sender.tab.id, { tabId: sender.tab.id, url: msg.payload?.url, lastHeartbeat: Date.now() });
        bcast('kernel:tab:activated', { tabId: sender.tab.id });
      }
      sendResponse({ ok: true }); break;
    case 'kernel:state:response': case 'kernel:health:response': case 'kernel:event':
      cacheState(msg.payload); bcast(msg.type, msg.payload, msg._id); sendResponse({ ok: true }); break;
    case 'devtools:command':
      chrome.tabs.sendMessage(msg.tabId, { target: 'content-script', type: msg.type, payload: msg.payload })
        .then(sendResponse).catch((e) => sendResponse({ ok: false, error: e.message }));
      return true;
    case 'devtools:subscribe':
      sendResponse({ ok: true, cached: getCached() }); break;
    default: sendResponse({ ok: false, error: 'unknown type' });
  }
});

function cacheState(s) { if (s?.appName) stateCache.set(s.appName, { ...s, _t: Date.now() }); }
function getCached() {
  const out = {}; const now = Date.now();
  for (const [k, v] of stateCache) if (now - v._t < 30000) out[k] = v;
  return out;
}
function bcast(type, payload, _id) { chrome.runtime.sendMessage({ target: 'devtools', type, payload, _id }).catch(() => {}); }
chrome.tabs.onRemoved.addListener((id) => { connections.delete(id); bcast('kernel:tab:deactivated', { tabId: id }); });
chrome.runtime.onConnect.addListener((port) => {
  if (port.name !== 'micro-kernel-devtools') return;
  port.onMessage.addListener((m) => { if (m.type === 'ping') port.postMessage({ type: 'pong' }); });
});
