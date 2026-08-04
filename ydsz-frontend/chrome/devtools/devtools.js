chrome.devtools.panels.create('Micro Kernel', '', 'devtools/panel.html', (panel) => {
  panel.onShown.addListener(() => {
    chrome.runtime.sendMessage({ target: 'background', type: 'devtools:subscribe', tabId: chrome.devtools.inspectedWindow.tabId });
  });
});
chrome.runtime.onMessage.addListener((msg) => {
  if (msg.target !== 'devtools') return;
  chrome.devtools.inspectedWindow.eval(`window.postMessage({source:'ext-bg',detail:${JSON.stringify(msg).replace(/\\/g,'\\\\').replace(/'/g,"\\'")}}, '*')`).catch(()=>{});
});
