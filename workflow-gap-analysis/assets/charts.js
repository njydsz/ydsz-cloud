(function () {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var warn = style.getPropertyValue('--warn').trim();
  var amber = style.getPropertyValue('--amber').trim();
  var ok = style.getPropertyValue('--ok').trim();

  // ---------- 图1：六维能力覆盖度雷达 ----------
  var radarEl = document.getElementById('chart-radar');
  if (radarEl) {
    var radar = echarts.init(radarEl, null, { renderer: 'svg' });
    radar.setOption({
      animation: false,
      tooltip: { appendToBody: true },
      legend: {
        data: ['本引擎 pmis_flow_*', '钉钉审批', '飞书审批', 'Flowable/Camunda'],
        top: 0,
        textStyle: { color: muted, fontSize: 12 },
        itemGap: 18
      },
      radar: {
        indicator: [
          { name: '流程建模', max: 100 },
          { name: '流程调度', max: 100 },
          { name: '任务协作', max: 100 },
          { name: '通知集成', max: 100 },
          { name: '治理分析', max: 100 },
          { name: '高级能力', max: 100 }
        ],
        center: ['50%', '56%'],
        radius: '64%',
        axisName: { color: ink, fontSize: 12.5, fontWeight: 600 },
        splitLine: { lineStyle: { color: rule } },
        splitArea: { areaStyle: { color: [bg2, '#f1f5f9'] } },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [{
        type: 'radar',
        data: [
          {
            value: [62, 92, 86, 42, 80, 64],
            name: '本引擎 pmis_flow_*',
            itemStyle: { color: accent },
            lineStyle: { width: 2.5, color: accent },
            areaStyle: { color: accent + '22' }
          },
          {
            value: [86, 74, 95, 90, 70, 50],
            name: '钉钉审批',
            itemStyle: { color: warn },
            lineStyle: { width: 2, color: warn },
            areaStyle: { color: warn + '14' }
          },
          {
            value: [82, 84, 88, 88, 76, 56],
            name: '飞书审批',
            itemStyle: { color: accent2 },
            lineStyle: { width: 2, color: accent2 },
            areaStyle: { color: accent2 + '14' }
          },
          {
            value: [96, 90, 50, 40, 86, 90],
            name: 'Flowable/Camunda',
            itemStyle: { color: amber },
            lineStyle: { width: 2, color: amber, type: 'dashed' },
            areaStyle: { color: amber + '10' }
          }
        ],
        symbolSize: 5
      }]
    });
    window.addEventListener('resize', function () { radar.resize(); });
  }

  // ---------- 图2：前端 API 对接覆盖率 ----------
  var covEl = document.getElementById('chart-coverage');
  if (covEl) {
    var cov = echarts.init(covEl, null, { renderer: 'svg' });
    var modules = ['流程定义', '流程实例', '任务操作', '待办/已办/抄送', '流程图/轨迹', '运营统计', 'AI辅助', '嵌入式审批', '表单Schema', '委托授权', 'SLA配置', '灰度发布', '版本管理', '模拟运行', '运行时表单'];
    var rates = [85, 88, 92, 90, 100, 60, 100, 100, 80, 0, 0, 0, 0, 0, 0];
    cov.setOption({
      animation: false,
      tooltip: {
        appendToBody: true,
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: function (p) {
          return p[0].name + '<br/>对接率：<b>' + p[0].value + '%</b>';
        }
      },
      grid: { left: 8, right: 28, top: 20, bottom: 10, containLabel: true },
      xAxis: {
        type: 'value', max: 100,
        axisLabel: { color: muted, fontSize: 11, formatter: '{value}%' },
        splitLine: { lineStyle: { color: rule } },
        axisLine: { show: false }
      },
      yAxis: {
        type: 'category',
        data: modules,
        inverse: true,
        axisLabel: { color: ink, fontSize: 12 },
        axisLine: { lineStyle: { color: rule } },
        axisTick: { show: false }
      },
      series: [{
        type: 'bar',
        data: rates.map(function (v) {
          var c = v === 0 ? warn : (v < 70 ? amber : ok);
          return { value: v, itemStyle: { color: c, borderRadius: [0, 4, 4, 0] } };
        }),
        barWidth: '58%',
        label: {
          show: true, position: 'right',
          formatter: function (p) { return p.value === 0 ? '零对接' : p.value + '%'; },
          color: muted, fontSize: 11
        }
      }]
    });
    window.addEventListener('resize', function () { cov.resize(); });
  }

  // ---------- Mermaid 初始化 ----------
  if (window.mermaid) {
    mermaid.initialize({ startOnLoad: true, theme: 'neutral', securityLevel: 'loose' });
  }
})();
