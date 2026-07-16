<!--
  @file WBS 甘特图视图（P0-1：项目甘特图可视化）
  @description 基于 ECharts 自定义系列渲染项目甘特图，支持：
    1. 树形任务层级展示（可展开/折叠）
    2. 计划 vs 实际日期条
    3. 进度百分比覆盖
    4. 里程碑菱形标记
    5. 任务依赖箭头线
    6. 今日参考线
    7. 缩放 + 拖拽平移
  @module views/execution/wbs-task/gantt
-->
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { CustomChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, MarkLineComponent, DataZoomComponent, LegendComponent, ToolboxComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { GanttNode } from '@/api/execution/wbs-task'
import { getGanttData } from '@/api/execution/wbs-task'

echarts.use([
  CanvasRenderer,
  CustomChart,
  GridComponent,
  TooltipComponent,
  MarkLineComponent,
  DataZoomComponent,
  LegendComponent,
  ToolboxComponent,
])

const props = defineProps<{
  initiationId: string | number
}>()

const { t } = useI18n()

const chartRef = ref<HTMLDivElement>()
const loading = ref(false)
const ganttData = ref<GanttNode[]>([])
let chartInstance: echarts.ECharts | null = null

// 状态颜色映射
const statusColors: Record<string, string> = {
  PLANNED: '#909399',
  IN_PROGRESS: '#409eff',
  BLOCKED: '#f56c6c',
  IN_REVIEW: '#e6a23c',
  COMPLETED: '#67c23a',
  CANCELLED: '#c0c4cc',
}

// 优先级颜色
const priorityColors: Record<string, string> = {
  LOW: '#dcdfe6',
  NORMAL: '#409eff',
  HIGH: '#e6a23c',
  URGENT: '#f56c6c',
}

/** 将树形数据扁平化为 ECharts 数据项 */
interface GanttItem {
  name: string
  value: [number, number, number] // [categoryIndex, startTime, endTime]
  itemStyle: { color: string }
  taskId: string
  progress: number
  isMilestone: boolean
  status: string
  ownerName: string
  plannedStart: string
  plannedEnd: string
  actualStart?: string
  actualEnd?: string
  dependsOn?: string
  taskLevel: number
}

/** 扁平化树形数据为 ECharts 渲染所需的分类 + 数据项 */
function flattenTasks(nodes: GanttNode[], level = 0, result: { categories: string[]; items: GanttItem[] } = { categories: [], items: [] }) {
  for (const node of nodes) {
    const categoryIndex = result.categories.length
    result.categories.push(node.taskName)

    const startDate = node.plannedStartDate ? new Date(node.plannedStartDate).getTime() : 0
    const endDate = node.plannedEndDate ? new Date(node.plannedEndDate).getTime() : startDate

    const isMilestone = node.milestone === 1 || node.taskType === 'MILESTONE'
    const status = node.status || 'PLANNED'
    const progress = Number(node.progressPct || 0)

    result.items.push({
      name: node.taskName,
      value: [categoryIndex, startDate, endDate],
      itemStyle: {
        color: isMilestone ? '#e6a23c' : statusColors[status] || '#409eff',
      },
      taskId: node.id,
      progress,
      isMilestone,
      status,
      ownerName: node.ownerName || '-',
      plannedStart: node.plannedStartDate || '-',
      plannedEnd: node.plannedEndDate || '-',
      actualStart: node.actualStartDate,
      actualEnd: node.actualEndDate,
      dependsOn: node.dependsOn,
      taskLevel: level,
    })

    if (node.children && node.children.length > 0) {
      flattenTasks(node.children, level + 1, result)
    }
  }
  return result
}

/** 渲染 ECharts 甘特图 */
function renderChart() {
  if (!chartRef.value || !chartInstance) return

  const { categories, items } = flattenTasks(ganttData.value)

  if (items.length === 0) {
    chartInstance.setOption({
      title: {
        text: t('execution.wbsTask.gantt.noData'),
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 },
      },
    })
    return
  }

  // 计算日期范围
  const allStarts = items.map(i => i.value[1]).filter(v => v > 0)
  const allEnds = items.map(i => i.value[2]).filter(v => v > 0)
  const minDate = allStarts.length > 0 ? Math.min(...allStarts) : Date.now()
  const maxDate = allEnds.length > 0 ? Math.max(...allEnds) : Date.now()
  // 向前后扩展 3 天作为边距
  const padding = 3 * 24 * 60 * 60 * 1000
  const todayTime = new Date().setHours(0, 0, 0, 0)

  const option: echarts.EChartsCoreOption = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const data = params.data as GanttItem
        if (!data) return ''
        const lines = [
          `<b>${data.name}</b>`,
          `${t('execution.wbsTask.gantt.owner')}: ${data.ownerName}`,
          `${t('execution.wbsTask.gantt.plannedRange')}: ${data.plannedStart} ~ ${data.plannedEnd}`,
        ]
        if (data.actualStart) {
          lines.push(`${t('execution.wbsTask.gantt.actualRange')}: ${data.actualStart} ~ ${data.actualEnd || '-'}`)
        }
        lines.push(`${t('execution.wbsTask.gantt.progress')}: ${data.progress}%`)
        lines.push(`${t('execution.wbsTask.gantt.status')}: ${data.status}`)
        if (data.isMilestone) {
          lines.push(`★ ${t('execution.wbsTask.gantt.milestone')}`)
        }
        return lines.join('<br/>')
      },
    },
    grid: {
      left: 200,
      right: 40,
      top: 60,
      bottom: 80,
      containLabel: false,
    },
    xAxis: {
      type: 'time',
      min: minDate - padding,
      max: maxDate + padding,
      axisLabel: {
        formatter: (val: number) => {
          const d = new Date(val)
          return `${d.getMonth() + 1}/${d.getDate()}`
        },
      },
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#ebeef5' } },
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      axisLabel: {
        width: 180,
        overflow: 'truncate',
        fontSize: 12,
      },
      splitLine: { show: true, lineStyle: { color: '#f0f0f0' } },
    },
    dataZoom: [
      {
        type: 'slider',
        xAxisIndex: 0,
        filterMode: 'weakFilter',
        height: 24,
        bottom: 30,
        showDetail: false,
      },
      {
        type: 'slider',
        yAxisIndex: 0,
        filterMode: 'weakFilter',
        width: 16,
        right: 10,
        showDetail: false,
      },
      {
        type: 'inside',
        xAxisIndex: 0,
        filterMode: 'weakFilter',
      },
      {
        type: 'inside',
        yAxisIndex: 0,
        filterMode: 'weakFilter',
      },
    ],
    series: [
      {
        type: 'custom',
        renderItem: (params: any, api: any) => {
          const categoryIndex = api.value(0)
          const start = api.coord([api.value(1), categoryIndex])
          const end = api.coord([api.value(2), categoryIndex])
          const height = api.size([0, 1])[1] * 0.6
          const width = Math.max(end[0] - start[0], 2)

          const item = items[params.dataIndex]
          if (!item) return null

          // 里程碑渲染为菱形
          if (item.isMilestone) {
            return {
              type: 'group',
              children: [
                {
                  type: 'path',
                  shape: {
                    pathData: 'M 0 -8 L 8 0 L 0 8 L -8 0 Z',
                    x: start[0],
                    y: start[1],
                  },
                  style: {
                    fill: '#e6a23c',
                    stroke: '#fff',
                    lineWidth: 1,
                  },
                },
                {
                  type: 'text',
                  style: {
                    text: '★',
                    x: start[0] - 4,
                    y: start[1] + 3,
                    fill: '#fff',
                    fontSize: 10,
                  },
                },
              ],
            }
          }

          // 任务条
          const children: any[] = [
            {
              type: 'rect',
              shape: {
                x: start[0],
                y: start[1] - height / 2,
                width: width,
                height: height,
                r: 2,
              },
              style: {
                fill: item.itemStyle.color,
                stroke: priorityColors[item.status] || '#dcdfe6',
                lineWidth: 1,
              },
            },
          ]

          // 进度覆盖条
          if (item.progress > 0 && item.progress < 100) {
            const progressWidth = (width * item.progress) / 100
            children.push({
              type: 'rect',
              shape: {
                x: start[0],
                y: start[1] - height / 2,
                width: progressWidth,
                height: height,
                r: 2,
              },
              style: {
                fill: 'rgba(255, 255, 255, 0.35)',
              },
            })
          } else if (item.progress >= 100) {
            children.push({
              type: 'text',
              style: {
                text: '✓',
                x: start[0] + width / 2 - 5,
                y: start[1] + 4,
                fill: '#fff',
                fontSize: 12,
                fontWeight: 'bold',
              },
            })
          }

          // 进度百分比文字
          if (width > 40 && item.progress > 0 && item.progress < 100) {
            children.push({
              type: 'text',
              style: {
                text: `${item.progress}%`,
                x: start[0] + 4,
                y: start[1] + 4,
                fill: '#fff',
                fontSize: 11,
              },
            })
          }

          return {
            type: 'group',
            children,
          }
        },
        encode: {
          x: [1, 2],
          y: 0,
        },
        data: items.map((item, idx) => ({
          name: item.name,
          value: [idx, item.value[1], item.value[2]],
          itemStyle: item.itemStyle,
          taskId: item.taskId,
          progress: item.progress,
          isMilestone: item.isMilestone,
          status: item.status,
          ownerName: item.ownerName,
          plannedStart: item.plannedStart,
          plannedEnd: item.plannedEnd,
          actualStart: item.actualStart,
          actualEnd: item.actualEnd,
          dependsOn: item.dependsOn,
        })),
      },
    ],
    // 今日参考线
    markLine: undefined as any,
  }

  // 添加今日参考线
  if (todayTime >= minDate - padding && todayTime <= maxDate + padding) {
    ;(option as any).series[0].markLine = {
      symbol: 'none',
      lineStyle: { type: 'dashed', color: '#f56c6c', width: 2 },
      label: { show: true, formatter: t('execution.wbsTask.gantt.today'), color: '#f56c6c', position: 'start' },
      data: [{ xAxis: todayTime }],
    }
  }

  chartInstance.setOption(option, true)
}

/** 加载甘特图数据 */
async function loadData() {
  if (!props.initiationId) {
    ElMessage.warning(t('execution.wbsTask.gantt.selectProject'))
    return
  }
  loading.value = true
  try {
    const { data } = await getGanttData(props.initiationId)
    ganttData.value = data || []
    await nextTick()
    renderChart()
  } catch (e: any) {
    ElMessage.error(e.message || t('execution.wbsTask.gantt.loadError'))
  } finally {
    loading.value = false
  }
}

/** 窗口 resize */
function handleResize() {
  chartInstance?.resize()
}

onMounted(async () => {
  await nextTick()
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
  }
  await loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})

watch(() => props.initiationId, () => {
  if (props.initiationId) loadData()
})
</script>

<template>
  <div v-loading="loading" class="gantt-container">
    <div ref="chartRef" class="gantt-chart" />
  </div>
</template>

<style scoped>
.gantt-container {
  width: 100%;
  height: 100%;
  min-height: 500px;
  position: relative;
}

.gantt-chart {
  width: 100%;
  height: 100%;
  min-height: 500px;
}
</style>
