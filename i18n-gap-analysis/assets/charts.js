(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var danger = style.getPropertyValue('--danger').trim();
  var success = style.getPropertyValue('--success').trim();

  // --- Chart 1: Frontend i18n Coverage (Doughnut) ---
  var chart1 = echarts.init(document.getElementById('chart-frontend-coverage'), null, { renderer: 'svg' });
  chart1.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true, formatter: '{b}: {c} 页 ({d}%)' },
    legend: { bottom: 10, left: 'center', textStyle: { color: muted, fontSize: 13 } },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: bg2, borderWidth: 3 },
      label: { show: true, formatter: '{d}%', fontSize: 16, fontWeight: 'bold', color: ink },
      data: [
        { value: 8, name: '已接入 i18n', itemStyle: { color: accent } },
        { value: 58, name: '未接入（硬编码中文）', itemStyle: { color: danger } }
      ]
    }]
  });
  window.addEventListener('resize', function() { chart1.resize(); });

  // --- Chart 2: Backend Hardcoded Chinese (Horizontal Bar) ---
  var chart2 = echarts.init(document.getElementById('chart-backend-hardcode'), null, { renderer: 'svg' });
  chart2.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
    grid: { left: 180, right: 40, top: 20, bottom: 30 },
    xAxis: { type: 'value', axisLine: { lineStyle: { color: rule } }, axisLabel: { color: muted, fontSize: 12 }, splitLine: { lineStyle: { color: rule } } },
    yAxis: {
      type: 'category',
      data: ['BizException 硬编码', 'Bean Validation 硬编码', 'BizErrorCode 枚举', 'return "中文"', '其他异常硬编码', 'Gateway 硬编码'],
      axisLine: { lineStyle: { color: rule } },
      axisLabel: { color: ink, fontSize: 12 }
    },
    series: [{
      type: 'bar',
      data: [566, 112, 48, 15, 19, 6],
      itemStyle: {
        color: function(params) {
          var colors = [danger, danger, accent2, accent2, muted, muted];
          return colors[params.dataIndex];
        },
        borderRadius: [0, 4, 4, 0]
      },
      barWidth: '55%',
      label: { show: true, position: 'right', color: ink, fontSize: 13, fontWeight: 'bold' }
    }]
  });
  window.addEventListener('resize', function() { chart2.resize(); });

  // --- Chart 3: Radar — Frontend vs Backend vs Standard ---
  var chart3 = echarts.init(document.getElementById('chart-radar'), null, { renderer: 'svg' });
  chart3.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true },
    legend: { bottom: 5, left: 'center', textStyle: { color: muted, fontSize: 13 } },
    radar: {
      indicator: [
        { name: 'i18n 框架', max: 10 },
        { name: '覆盖率', max: 10 },
        { name: '语言切换', max: 10 },
        { name: '格式化', max: 10 },
        { name: '错误消息', max: 10 },
        { name: '校验消息', max: 10 },
        { name: '工程化', max: 10 },
        { name: '数据多语言', max: 10 }
      ],
      center: ['50%', '48%'],
      radius: '62%',
      axisName: { color: ink, fontSize: 12 },
      splitLine: { lineStyle: { color: rule } },
      splitArea: { areaStyle: { color: [bg2, 'transparent'] } },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [{
      type: 'radar',
      data: [
        { value: [10, 10, 10, 10, 10, 10, 10, 10], name: '大厂标准', areaStyle: { color: success + '20' }, lineStyle: { color: success, width: 2 }, itemStyle: { color: success } },
        { value: [4, 2, 1, 0, 5, 5, 1, 0], name: '前端现状', areaStyle: { color: accent + '20' }, lineStyle: { color: accent, width: 2 }, itemStyle: { color: accent } },
        { value: [0, 0, 0, 0, 1, 0, 0, 0], name: '后端现状', areaStyle: { color: danger + '20' }, lineStyle: { color: danger, width: 2 }, itemStyle: { color: danger } }
      ]
    }]
  });
  window.addEventListener('resize', function() { chart3.resize(); });

  // --- Chart 4: Roadmap Workload & Coverage ---
  var chart4 = echarts.init(document.getElementById('chart-roadmap'), null, { renderer: 'svg' });
  chart4.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' },
      formatter: function(params) {
        var s = params[0].name + '<br/>';
        params.forEach(function(p) {
          s += p.marker + ' ' + p.seriesName + ': ' + p.value + (p.seriesName === '预估工作量' ? ' 人日' : '%') + '<br/>';
        });
        return s;
      }
    },
    legend: { bottom: 5, left: 'center', textStyle: { color: muted, fontSize: 13 } },
    grid: { left: 60, right: 60, top: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: ['P0 基础设施', 'P1 覆盖率提升', 'P2 工程化', 'P3 高级能力'],
      axisLine: { lineStyle: { color: rule } },
      axisLabel: { color: ink, fontSize: 12, fontWeight: 'bold' }
    },
    yAxis: [
      { type: 'value', name: '工作量(人日)', nameTextStyle: { color: muted, fontSize: 11 }, axisLine: { lineStyle: { color: rule } }, axisLabel: { color: muted, fontSize: 11 }, splitLine: { lineStyle: { color: rule } } },
      { type: 'value', name: '覆盖率(%)', nameTextStyle: { color: muted, fontSize: 11 }, min: 0, max: 100, axisLine: { lineStyle: { color: rule } }, axisLabel: { color: muted, fontSize: 11, formatter: '{value}%' }, splitLine: { show: false } }
    ],
    series: [
      { name: '预估工作量', type: 'bar', data: [4, 12, 4, 6], itemStyle: { color: accent, borderRadius: [4, 4, 0, 0] }, barWidth: '30%', label: { show: true, position: 'top', color: ink, fontSize: 12, fontWeight: 'bold' } },
      { name: '预期覆盖率', type: 'line', yAxisIndex: 1, data: [12, 80, 85, 95], itemStyle: { color: success }, lineStyle: { width: 3 }, symbol: 'circle', symbolSize: 10, label: { show: true, color: success, fontSize: 12, fontWeight: 'bold', formatter: '{c}%' } }
    ]
  });
  window.addEventListener('resize', function() { chart4.resize(); });

  // --- Mermaid Init ---
  if (typeof mermaid !== 'undefined') {
    mermaid.initialize({ startOnLoad: true, theme: 'neutral', securityLevel: 'loose' });
  }
})();
