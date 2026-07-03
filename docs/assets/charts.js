/*
 * ============================================================================
 *  文件名: charts.js
 *  路径:   docs/assets/charts.js
 *  作用:   PRD/对标报告中所有 ECharts 图表的渲染脚本（竞品雷达、岗位分布等）
 *  依赖:   ../_shared/js/echarts.min.js
 *  使用:   在 docs/pmis-prd-v3.html 中引入
 *  维护:   PMIS 产品组
 * ============================================================================
 */
(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var accent3 = style.getPropertyValue('--accent3').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();

  // --- Chart: 竞品对标雷达图 ---
  var el1 = document.getElementById('chart-radar');
  if (el1) {
    var chart1 = echarts.init(el1, null, { renderer: 'svg' });
    chart1.setOption({
      animation: false,
      tooltip: { trigger: 'item', appendToBody: true },
      legend: {
        data: ['南京云顶 PMIS V3.0', '通用研发工具(TAPD/ONES)', '通用协同工具(飞书/钉钉)', '用友BIP业财一体化'],
        bottom: 0,
        textStyle: { color: muted, fontSize: 11 },
        itemWidth: 14,
        itemHeight: 8
      },
      radar: {
        indicator: [
          { name: '项目全生命周期', max: 3 },
          { name: '工时与成本核算', max: 3 },
          { name: '资源管理与调度', max: 3 },
          { name: '项目财务(预算/成本/收入/利润)', max: 3 },
          { name: '数据分析与驾驶舱', max: 3 },
          { name: '权限安全与合规', max: 3 }
        ],
        center: ['50%', '48%'],
        radius: '62%',
        axisName: { color: ink, fontSize: 11 },
        splitLine: { lineStyle: { color: rule } },
        splitArea: { areaStyle: { color: [bg2, '#f1f5f9'] } },
        axisLine: { lineStyle: { color: rule } }
      },
      series: [{
        type: 'radar',
        data: [
          {
            value: [3, 3, 3, 3, 3, 3],
            name: '南京云顶 PMIS V3.0',
            areaStyle: { color: accent + '33' },
            lineStyle: { color: accent, width: 2 },
            itemStyle: { color: accent }
          },
          {
            value: [2.5, 2.5, 2, 1.5, 2.5, 2.5],
            name: '通用研发工具(TAPD/ONES)',
            areaStyle: { color: accent2 + '22' },
            lineStyle: { color: accent2, width: 1.5, type: 'dashed' },
            itemStyle: { color: accent2 }
          },
          {
            value: [2, 1.5, 2, 0.5, 2.5, 2.5],
            name: '通用协同工具(飞书/钉钉)',
            areaStyle: { color: muted + '15' },
            lineStyle: { color: muted, width: 1.5, type: 'dashed' },
            itemStyle: { color: muted }
          },
          {
            value: [3, 3, 2, 3, 3, 3],
            name: '用友BIP业财一体化',
            areaStyle: { color: accent3 + '22' },
            lineStyle: { color: accent3, width: 1.5, type: 'dashed' },
            itemStyle: { color: accent3 }
          }
        ]
      }]
    });
    window.addEventListener('resize', function() { chart1.resize(); });
  }

  // --- Chart: 项目类型利润率对比 ---
  var el2 = document.getElementById('chart-profit');
  if (el2) {
    var chart2 = echarts.init(el2, null, { renderer: 'svg' });
    chart2.setOption({
      animation: false,
      tooltip: { trigger: 'axis', appendToBody: true, axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '10%', containLabel: true },
      xAxis: {
        type: 'category',
        data: ['系统开发', '系统集成', '系统维护', '软件产品', '硬件产品', '技术咨询', '硬件运维', '人力外包'],
        axisLabel: { color: muted, fontSize: 10, rotate: 20 },
        axisLine: { lineStyle: { color: rule } }
      },
      yAxis: {
        type: 'value',
        name: '预期净利润率 (%)',
        nameTextStyle: { color: muted, fontSize: 11 },
        axisLabel: { color: muted, fontSize: 10, formatter: '{value}%' },
        axisLine: { lineStyle: { color: rule } },
        splitLine: { lineStyle: { color: rule } }
      },
      series: [{
        type: 'bar',
        data: [
          { value: 15, itemStyle: { color: accent } },
          { value: 15, itemStyle: { color: accent } },
          { value: 15, itemStyle: { color: accent } },
          { value: 15, itemStyle: { color: accent } },
          { value: 15, itemStyle: { color: accent2 } },
          { value: 15, itemStyle: { color: accent } },
          { value: 15, itemStyle: { color: accent2 } },
          { value: 25, itemStyle: { color: accent3 } }
        ],
        barWidth: '55%',
        label: {
          show: true,
          position: 'top',
          color: ink,
          fontSize: 11,
          formatter: '{c}%'
        }
      }]
    });
    window.addEventListener('resize', function() { chart2.resize(); });
  }
})();
