(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var accent3 = style.getPropertyValue('--accent3').trim();
  var accent4 = style.getPropertyValue('--accent4').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  // ==================== Chart 1: Radar ====================
  var radarChart = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });
  radarChart.setOption({
    animation: false,
    tooltip: { appendToBody: true },
    legend: {
      bottom: 0,
      textStyle: { color: muted, fontSize: 12 },
      data: ['本项目', '钉钉宜搭', '飞书审批', '炎黄盈动', '奥哲云枢', 'FlowLong']
    },
    radar: {
      center: ['50%', '50%'],
      radius: '65%',
      indicator: [
        { name: '核心引擎', max: 100 },
        { name: '审批操作', max: 100 },
        { name: '流程设计器', max: 100 },
        { name: '智能化', max: 100 },
        { name: '监控分析', max: 100 },
        { name: '体验细节', max: 100 },
        { name: '集成扩展', max: 100 }
      ],
      axisName: { color: muted, fontSize: 11 }
    },
    series: [{
      type: 'radar',
      data: [
        {
          name: '本项目',
          value: [88, 85, 62, 55, 65, 68, 70],
          lineStyle: { color: accent, width: 2.5 },
          areaStyle: { color: accent + '22' },
          itemStyle: { color: accent },
          symbol: 'circle',
          symbolSize: 6
        },
        {
          name: '钉钉宜搭',
          value: [55, 65, 75, 60, 50, 85, 60],
          lineStyle: { color: '#f59e0b', width: 1.5, type: 'dashed' },
          areaStyle: { color: '#f59e0b11' },
          itemStyle: { color: '#f59e0b' },
          symbol: 'diamond',
          symbolSize: 5
        },
        {
          name: '飞书审批',
          value: [60, 70, 78, 65, 68, 88, 72],
          lineStyle: { color: '#8b5cf6', width: 1.5, type: 'dashed' },
          areaStyle: { color: '#8b5cf611' },
          itemStyle: { color: '#8b5cf6' },
          symbol: 'diamond',
          symbolSize: 5
        },
        {
          name: '炎黄盈动',
          value: [95, 90, 90, 88, 92, 80, 90],
          lineStyle: { color: '#ec4899', width: 1.5, type: 'dashed' },
          areaStyle: { color: '#ec489911' },
          itemStyle: { color: '#ec4899' },
          symbol: 'triangle',
          symbolSize: 5
        },
        {
          name: '奥哲云枢',
          value: [95, 90, 88, 90, 85, 78, 88],
          lineStyle: { color: '#14b8a6', width: 1.5, type: 'dashed' },
          areaStyle: { color: '#14b8a611' },
          itemStyle: { color: '#14b8a6' },
          symbol: 'triangle',
          symbolSize: 5
        },
        {
          name: 'FlowLong',
          value: [82, 92, 80, 70, 55, 60, 75],
          lineStyle: { color: '#6366f1', width: 1.5, type: 'dashed' },
          areaStyle: { color: '#6366f111' },
          itemStyle: { color: '#6366f1' },
          symbol: 'rect',
          symbolSize: 5
        }
      ]
    }]
  });
  window.addEventListener('resize', function() { radarChart.resize(); });

  // ==================== Chart 2: Gap Severity Distribution ====================
  var gapChart = echarts.init(document.getElementById('chart-gap'), null, { renderer: 'svg' });
  gapChart.setOption({
    animation: false,
    tooltip: {
      appendToBody: true,
      formatter: function(p) { return p.name + '<br/>差距数量: ' + p.value + ' 项'; }
    },
    series: [{
      type: 'treemap',
      data: [
        { name: 'P0 关键\n(3项)', value: 3, itemStyle: { color: accent4 } },
        { name: 'P1 重要\n(7项)', value: 7, itemStyle: { color: accent3 } },
        { name: 'P2 优化\n(6项)', value: 6, itemStyle: { color: accent } },
        { name: 'P3 战略\n(2项)', value: 2, itemStyle: { color: muted } }
      ],
      label: {
        show: true,
        fontSize: 13,
        fontWeight: 'bold',
        color: '#fff',
        formatter: function(p) { return p.name; }
      },
      breadcrumb: { show: false },
      roam: false,
      nodeClick: false,
      top: 10,
      bottom: 10
    }]
  });
  window.addEventListener('resize', function() { gapChart.resize(); });
})();