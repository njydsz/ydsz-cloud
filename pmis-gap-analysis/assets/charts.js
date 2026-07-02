(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var bg = style.getPropertyValue('--bg').trim();

  // --- Chart 1: 前端各维度评分雷达图 ---
  var chart1 = echarts.init(document.getElementById('chart-frontend-radar'), null, { renderer: 'svg' });
  chart1.setOption({
    animation: false,
    radar: {
      center: ['50%', '50%'],
      radius: '65%',
      indicator: [
        { name: '通用组件', max: 100 },
        { name: '表单交互', max: 100 },
        { name: '列表/表格', max: 100 },
        { name: '错误处理', max: 100 },
        { name: '国际化', max: 100 },
        { name: '路由守卫', max: 100 },
        { name: '样式系统', max: 100 }
      ],
      axisName: { color: muted, fontSize: 12 },
      splitArea: { areaStyle: { color: [bg, bg2] } }
    },
    series: [{
      type: 'radar',
      data: [{ value: [72, 45, 48, 68, 35, 70, 65], name: '当前评分' }],
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { color: accent, width: 2 },
      areaStyle: { color: accent + '22' },
      itemStyle: { color: accent }
    }]
  });
  window.addEventListener('resize', function() { chart1.resize(); });

  // --- Chart 2: 后端各维度评分雷达图 ---
  var chart2 = echarts.init(document.getElementById('chart-backend-radar'), null, { renderer: 'svg' });
  chart2.setOption({
    animation: false,
    radar: {
      center: ['50%', '50%'],
      radius: '65%',
      indicator: [
        { name: 'API设计', max: 100 },
        { name: '异常处理', max: 100 },
        { name: 'Service层', max: 100 },
        { name: '安全防护', max: 100 },
        { name: '数据库设计', max: 100 },
        { name: '日志监控', max: 100 },
        { name: '性能优化', max: 100 },
        { name: '代码规范', max: 100 }
      ],
      axisName: { color: muted, fontSize: 12 },
      splitArea: { areaStyle: { color: [bg, bg2] } }
    },
    series: [{
      type: 'radar',
      data: [{ value: [68, 65, 50, 55, 25, 62, 42, 52], name: '当前评分' }],
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { color: accent2, width: 2 },
      areaStyle: { color: accent2 + '22' },
      itemStyle: { color: accent2 }
    }]
  });
  window.addEventListener('resize', function() { chart2.resize(); });

  // --- Chart 3: 问题严重程度分布 ---
  var chart3 = echarts.init(document.getElementById('chart-severity'), null, { renderer: 'svg' });
  chart3.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true },
    grid: { left: '10%', right: '5%', top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: ['前端交互', '前端架构', '后端API', '后端Service', '安全防护', '数据库', '性能', '代码规范'],
      axisLabel: { color: muted, fontSize: 11, rotate: 30 },
      axisLine: { lineStyle: { color: rule } }
    },
    yAxis: {
      type: 'value',
      name: '问题数',
      axisLabel: { color: muted },
      splitLine: { lineStyle: { color: rule } }
    },
    series: [
      {
        name: 'P0-严重',
        type: 'bar',
        stack: 'total',
        data: [3, 2, 1, 2, 3, 2, 3, 2],
        itemStyle: { color: '#e74c3c' },
        barWidth: 32
      },
      {
        name: 'P1-重要',
        type: 'bar',
        stack: 'total',
        data: [5, 3, 2, 2, 2, 1, 1, 2],
        itemStyle: { color: accent2 }
      },
      {
        name: 'P2-优化',
        type: 'bar',
        stack: 'total',
        data: [4, 2, 1, 1, 1, 1, 1, 1],
        itemStyle: { color: accent }
      }
    ],
    legend: {
      data: ['P0-严重', 'P1-重要', 'P2-优化'],
      textStyle: { color: muted },
      top: 0
    }
  });
  window.addEventListener('resize', function() { chart3.resize(); });

  // --- Chart 4: 功能完成度饼图 ---
  var chart4 = echarts.init(document.getElementById('chart-completion'), null, { renderer: 'svg' });
  chart4.setOption({
    animation: false,
    tooltip: { trigger: 'item', appendToBody: true },
    series: [{
      type: 'pie',
      radius: ['55%', '80%'],
      center: ['50%', '50%'],
      label: { color: muted, fontSize: 12 },
      emphasis: { label: { fontSize: 16, fontWeight: 'bold' } },
      data: [
        { value: 15, name: '已完成核心模块', itemStyle: { color: accent } },
        { value: 3, name: '部分实现', itemStyle: { color: accent2 } },
        { value: 5, name: '规划中', itemStyle: { color: muted } }
      ]
    }]
  });
  window.addEventListener('resize', function() { chart4.resize(); });

  // --- Chart 5: 优化路线甘特图 ---
  var chart5 = echarts.init(document.getElementById('chart-roadmap'), null, { renderer: 'svg' });
  var phases = ['第一阶段: 体验修复', '第二阶段: 性能优化', '第三阶段: 安全加固', '第四阶段: 平台化'];
  var tasks = [
    { name: '表单防重复提交', phase: 0, start: 0, end: 1 },
    { name: 'i18n体系统一', phase: 0, start: 0, end: 2 },
    { name: '列表导出功能', phase: 0, start: 1, end: 2 },
    { name: '空状态集成', phase: 0, start: 2, end: 3 },
    { name: 'N+1查询修复', phase: 1, start: 3, end: 5 },
    { name: 'Feign批量查询', phase: 1, start: 3, end: 4 },
    { name: '缓存策略实施', phase: 1, start: 4, end: 6 },
    { name: 'Flyway迁移', phase: 1, start: 3, end: 4 },
    { name: 'CORS限制', phase: 2, start: 6, end: 7 },
    { name: 'XSS防护', phase: 2, start: 6, end: 7 },
    { name: '密钥外部化', phase: 2, start: 7, end: 8 },
    { name: '信创适配', phase: 3, start: 8, end: 11 },
    { name: '多租户SaaS', phase: 3, start: 9, end: 12 },
    { name: '移动端H5', phase: 3, start: 10, end: 12 }
  ];
  var seriesData = tasks.map(function(t) {
    return {
      name: t.name,
      value: [t.phase, t.start, t.end],
      itemStyle: { color: [accent, accent2, accent + '88', muted][t.phase] }
    };
  });
  chart5.setOption({
    animation: false,
    tooltip: {
      trigger: 'item',
      appendToBody: true,
      formatter: function(p) { return p.name + '<br/>周次: ' + p.value[1] + ' - ' + p.value[2]; }
    },
    grid: { left: 180, right: 30, top: 20, bottom: 30 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 12,
      interval: 1,
      name: '周次',
      axisLabel: { color: muted },
      splitLine: { lineStyle: { color: rule } }
    },
    yAxis: {
      type: 'category',
      data: phases,
      axisLabel: { color: ink, fontSize: 13, fontWeight: 600 },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [{
      type: 'custom',
      renderItem: function(params, api) {
        var catIndex = api.value(0);
        var start = api.coord([api.value(1), catIndex]);
        var end = api.coord([api.value(2), catIndex]);
        var height = api.size([0, 1])[1] * 0.6;
        return {
          type: 'rect',
          shape: {
            x: start[0],
            y: start[1] - height / 2,
            width: Math.max(end[0] - start[0], 4),
            height: height
          },
          style: { fill: api.visual('color'), rx: 4, ry: 4 },
          textContent: {
            type: 'text',
            style: {
              text: api.value(3),
              fill: ink,
              fontSize: 11,
              x: start[0] + 6,
              y: start[1],
              textVerticalAlign: 'middle'
            }
          }
        };
      },
      data: seriesData,
      encode: { x: [1, 2], y: 0 }
    }]
  });
  window.addEventListener('resize', function() { chart5.resize(); });

  // --- Chart 6: 各维度问题对比 ---
  var chart6 = echarts.init(document.getElementById('chart-dimension-bar'), null, { renderer: 'svg' });
  chart6.setOption({
    animation: false,
    tooltip: { trigger: 'axis', appendToBody: true },
    grid: { left: '3%', right: '8%', top: 40, bottom: 30, containLabel: true },
    xAxis: {
      type: 'value',
      name: '问题数量',
      axisLabel: { color: muted },
      splitLine: { lineStyle: { color: rule } }
    },
    yAxis: {
      type: 'category',
      data: ['数据库设计', '性能优化', '代码规范', 'Service层', '安全防护', '表单交互', '国际化', 'API设计', '日志监控', '异常处理', '通用组件', '路由守卫', '样式系统'],
      axisLabel: { color: ink, fontSize: 12 },
      axisLine: { lineStyle: { color: rule } }
    },
    series: [
      {
        name: '前端',
        type: 'bar',
        data: [0, 0, 0, 0, 0, 8, 4, 0, 0, 0, 7, 5, 5],
        itemStyle: { color: accent },
        barWidth: 12
      },
      {
        name: '后端',
        type: 'bar',
        data: [4, 5, 5, 5, 6, 0, 0, 4, 4, 4, 0, 0, 0],
        itemStyle: { color: accent2 },
        barWidth: 12
      }
    ],
    legend: {
      data: ['前端', '后端'],
      textStyle: { color: muted },
      top: 0
    }
  });
  window.addEventListener('resize', function() { chart6.resize(); });
})();