/**
 * ECharts 统一入口
 *
 * P2-6: 配合 CDN 外置方案，改为完整包导入。
 *  - 生产环境：echarts 通过 CDN external，import 会被替换为 window.echarts（完整 UMD 包）
 *  - 开发环境：从 node_modules 加载完整包
 *
 * 之前按需引入（echarts/core + 子模块 + echarts.use）的方案与 external 冲突：
 * external 只能匹配 'echarts' 顶层，无法为 'echarts/core' 等子路径映射全局变量。
 * 因此统一改为完整包导入，API 保持兼容，使用方无需修改。
 *
 * 使用方式:
 *   import { init, use, type ECharts, type EChartsOption } from '@/utils/echarts'
 *
 * 或保持兼容:
 *   import * as echarts from '@/utils/echarts'
 *   echarts.init(...)
 */
import * as echarts from 'echarts'

// 导出 use / connect / disconnect / getInstanceByDom 等核心方法
// 完整包已内置全部图表与组件注册，无需再调用 echarts.use([...])
export const { use, connect, disconnect, getInstanceByDom } = echarts

// init 直接导出（完整包场景下不再需要包装函数阻止 Rollup 扁平化）
export const { init } = echarts

// ECharts 实例类型：使用 init 的返回类型，保证与 echarts.init() 返回值类型完全一致
// 说明：echarts 完整包与 echarts/core 各自声明了 EChartsType，二者在私有属性 _ssr 上
// 存在声明差异；若 ECharts 取自 echarts/core 而 init 来自完整包，会出现赋值不兼容。
// 直接用 ReturnType<typeof echarts.init> 可避免该不一致。
export type ECharts = ReturnType<typeof echarts.init>

// EChartsOption 从 echarts/core 重导出(EChartsCoreOption),
// 避免从 'echarts' 全量包重导出类型导致 tree-shaking 失效
export type { EChartsCoreOption as EChartsOption } from 'echarts/core'
export type { BarSeriesOption, LineSeriesOption, PieSeriesOption, RadarSeriesOption } from 'echarts/charts'

// 默认导出(兼容 import * as echarts 写法)
export default echarts
