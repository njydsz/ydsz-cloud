(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bad = style.getPropertyValue('--bad').trim();
  var warn = style.getPropertyValue('--warn').trim();
  var ok = style.getPropertyValue('--ok').trim();

  var cjkFont = '"PingFang SC","Microsoft YaHei","Noto Sans CJK SC",sans-serif';

  // ---------- Chart 1: Radar — 十维能力对标 ----------
  var radarEl = document.getElementById('chart-radar');
  if (radarEl) {
    var radar = echarts.init(radarEl, null, { renderer: 'svg' });
    radar.setOption({
      animation: false,
      color: [bad, accent, accent2, muted],
      tooltip: { appendToBody: true },
      legend: {
        data: ['当前实现', 'LiteFlow', 'URule', 'QLExpress'],
        top: 0,
        textStyle: { color: ink, fontFamily: cjkFont, fontSize: 13 }
      },
      radar: {
        indicator: [
          { name: '规则定义', max: 10 },
          { name: '动态配置', max: 10 },
          { name: '规则编排', max: 10 },
          { name: '版本管理', max: 10 },
          { name: '可视化编辑', max: 10 },
          { name: '执行性能', max: 10 },
          { name: '规则测试', max: 10 },
          { name: '监控告警', max: 10 },
          { name: '扩展性', max: 10 },
          { name: '模块独立性', max: 10 }
        ],
        center: ['50%', '55%'],
        radius: '62%',
        axisName: { color: ink, fontFamily: cjkFont, fontSize: 12 },
        splitLine: { lineStyle: { color: rule } },
        splitArea: { areaStyle: { color: [bg2, 'transparent'] } },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [{
        type: 'radar',
        data: [
          {
            value: [3, 1, 1, 0, 0, 8, 1, 4, 4, 4],
            name: '当前实现',
            areaStyle: { color: bad + '22' },
            lineStyle: { color: bad, width: 2 },
            itemStyle: { color: bad }
          },
          {
            value: [9, 9, 10, 7, 4, 9, 5, 6, 10, 9],
            name: 'LiteFlow',
            areaStyle: { color: accent + '18' },
            lineStyle: { color: accent, width: 2 },
            itemStyle: { color: accent }
          },
          {
            value: [9, 8, 6, 7, 10, 7, 6, 7, 8, 8],
            name: 'URule',
            areaStyle: { color: accent2 + '15' },
            lineStyle: { color: accent2, width: 2 },
            itemStyle: { color: accent2 }
          },
          {
            value: [7, 8, 2, 3, 0, 9, 3, 3, 8, 9],
            name: 'QLExpress',
            areaStyle: { color: muted + '12' },
            lineStyle: { color: muted, width: 2, type: 'dashed' },
            itemStyle: { color: muted }
          }
        ]
      }]
    });
    window.addEventListener('resize', function () { radar.resize(); });
  }

  // ---------- Chart 2: Bar — 功能覆盖度 ----------
  var barEl = document.getElementById('chart-bar');
  if (barEl) {
    var bar = echarts.init(barEl, null, { renderer: 'svg' });
    bar.setOption({
      animation: false,
      tooltip: {
        appendToBody: true,
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: function (p) {
          var s = p[0].name + '<br/>';
          p.forEach(function (item) {
            s += item.marker + ' ' + item.seriesName + ': ' + item.value + '%<br/>';
          });
          return s;
        }
      },
      legend: {
        data: ['规则定义', '动态配置', '规则编排', '版本管理', '可视化编辑', '规则测试'],
        top: 0,
        textStyle: { color: ink, fontFamily: cjkFont, fontSize: 12 }
      },
      grid: { left: '3%', right: '4%', bottom: '3%', top: '18%', containLabel: true },
      xAxis: {
        type: 'category',
        data: ['当前实现', 'LiteFlow', 'URule', 'QLExpress', 'Aviator'],
        axisLabel: { color: ink, fontFamily: cjkFont, fontSize: 12 },
        axisLine: { lineStyle: { color: rule } }
      },
      yAxis: {
        type: 'value',
        max: 100,
        axisLabel: { color: muted, fontFamily: cjkFont, fontSize: 11, formatter: '{value}%' },
        splitLine: { lineStyle: { color: rule } }
      },
      series: [
        { name: '规则定义', type: 'bar', data: [20, 95, 95, 80, 75], itemStyle: { color: accent }, barGap: '10%' },
        { name: '动态配置', type: 'bar', data: [0, 95, 85, 60, 60], itemStyle: { color: accent2 } },
        { name: '规则编排', type: 'bar', data: [0, 100, 50, 0, 0], itemStyle: { color: ok } },
        { name: '版本管理', type: 'bar', data: [0, 75, 60, 0, 0], itemStyle: { color: warn } },
        { name: '可视化编辑', type: 'bar', data: [0, 40, 100, 0, 0], itemStyle: { color: bad } },
        { name: '规则测试', type: 'bar', data: [0, 50, 60, 0, 0], itemStyle: { color: muted } }
      ]
    });
    window.addEventListener('resize', function () { bar.resize(); });
  }
})();
