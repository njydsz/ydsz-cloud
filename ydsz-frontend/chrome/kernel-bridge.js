;(function (g) {
  const NS = '__YDSZ_MICRO_KERNEL__';
  const CH = NS + '_CHANNEL';
  g.__sendToExtension = function (type, payload) {
    g.postMessage({ channel: CH, source: 'page', type, payload, _t: Date.now() }, '*');
    g.dispatchEvent(new g.CustomEvent(NS + ':out', { detail: { type, payload } }));
  };
  g.__markExtensionActive = function () { g[NS + '_ACTIVE__'] = true; };
  g.__KERNEL_BRIDGE__ = { channel: CH, NAMESPACE: NS };
})(window);
