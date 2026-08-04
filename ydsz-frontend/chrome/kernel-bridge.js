/* MV3 bridge: micro-kernel page -> extension */
;(function (g) {
  var NS = '__YDSZ_MICRO_KERNEL__';
  var CH = NS + '_CHANNEL';
  g.__sendToExtension = function (type, payload) {
    var msg = { channel: CH, source: 'page', type: type, payload: payload, _t: Date.now() };
    g.postMessage(msg, '*');
    g.dispatchEvent(new g.CustomEvent(NS + ':out', { detail: { type: type, payload: payload } }));
  };
  g.__markExtensionActive = function () { g[NS + '_ACTIVE__'] = true; };
  g.__extensionPresent = function () { return !!g[NS + '_ACTIVE__']; };
  g.__KERNEL_BRIDGE__ = { channel: CH, NAMESPACE: NS };
})(window);
