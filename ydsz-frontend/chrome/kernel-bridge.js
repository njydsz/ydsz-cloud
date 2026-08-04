/* [MV3] micro-kernel <-> Chrome Extension 双向桥接 */
;(function (g) {
  'use strict';
  const NS = '__YDSZ_MICRO_KERNEL__';
  const CH = NS + '_CHANNEL';
  g.__sendToExtension = function (type, payload) {
    const msg = { source: 'page', type, payload, _t: Date.now() };
    g.postMessage({ channel: CH, ...msg }, '*');
    g.dispatchEvent(new g.CustomEvent(NS + ':out', { detail: msg }));
  };
  g.__emitKernelEvent = function (ev, data) { g.__sendToExtension('kernel:event', { eventName: ev, data }); };
  g.__markExtensionActive = function () { g[NS + '_ACTIVE__'] = true; };
  g.__KERNEL_BRIDGE__ = {
    forwardToPage: function (m) { g.postMessage({ channel: CH, source: 'extension', ...m }, '*'); },
    channel: CH, NAMESPACE: NS,
  };
})(window);
