// assets/charts.js
(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  // --- Chart: Radar ---
  var radarDom = document.getElementById('chart-radar');
  if (radarDom) {
    var chart = echarts.init(radarDom, null, { renderer: 'svg' });
    chart.setOption({
      color: [accent, accent2, muted],
      radar: {
        center: ['50%', '55%'],
        radius: '65%',
        indicator: [
          { name: '表达式能力', max: 10 },
          { name: '规则编排', max: 10 },
          { name: '可视化', max: 10 },
          { name: '热加载', max: 10 },
          { name: '版本管理', max: 10 },
          { name: '灰度发布', max: 10 },
          { name: '链路追踪', max: 10 },
          { name: 'AI辅助', max: 10 },
          { name: '性能', max: 10 },
          { name: '生态工具', max: 10 }
        ],
        axisName: {
          color: muted,
          fontSize: 11,
          borderRadius: 3,
          padding: [3, 5]
        },
        splitArea: {
          areaStyle: {
            color: ['rgba(37, 99, 235, 0.02)', 'rgba(37, 99, 235, 0.02)']
          }
        },
        splitLine: {
          lineStyle: { color: rule }
        },
        axisLine: {
          lineStyle: { color: rule }
        }
      },
      tooltip: {
        appendToBody: true,
        trigger: 'item'
      },
      legend: {
        data: ['LiteRule', '行业领先水平'],
        bottom: 10,
        textStyle: { color: muted, fontSize: 12 }
      },
      series: [{
        type: 'radar',
        name: 'LiteRule',
        data: [{
          value: [7, 6, 2, 7, 6, 0, 2, 8, 6, 1],
          name: 'LiteRule',
          areaStyle: { color: accent + '33' },
          lineStyle: { color: accent, width: 2 },
          itemStyle: { color: accent }
        }],
        symbol: 'circle',
        symbolSize: 6,
        animation: false
      }, {
        type: 'radar',
        name: '行业领先水平',
        data: [{
          value: [9, 9, 9, 9, 8, 8, 8, 3, 9, 7],
          name: '行业领先水平',
          areaStyle: { color: muted + '22' },
          lineStyle: { color: muted, width: 2, type: 'dashed' },
          itemStyle: { color: muted }
        }],
        symbol: 'diamond',
        symbolSize: 6,
        animation: false
      }]
    });
    window.addEventListener('resize', function() { chart.resize(); });
  }
})();