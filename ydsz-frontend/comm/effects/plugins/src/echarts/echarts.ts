/**
 * echarts 模块
 *
 * @path comm\effects\plugins\src\echarts\echarts.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  // 系列类型的定义后缀都为 SeriesOption
  BarSeriesOption,
  LineSeriesOption,
} from 'echarts/charts';
import type {
  DatasetComponentOption,
  GridComponentOption,
  // 组件类型的定义后缀都为 ComponentOption
  TitleComponentOption,
  TooltipComponentOption,
} from 'echarts/components';
import type { ComposeOption } from 'echarts/core';

import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts';
import {
  // 数据集组件
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  // 内置数据转换器组件 (filter, sort)
  TransformComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { LabelLayout, UniversalTransition } from 'echarts/features';
import { CanvasRenderer } from 'echarts/renderers';

/**
 * 项目内 ECharts 图表配置项的收窄类型。
 *
 * @remarks
 * 为了配合按需引入减小打包体积，这里用 `ComposeOption` 只组合出**当前已注册**的
 * 系列与组件对应的配置类型，而非 echarts 完整的 `EChartsOption`。
 *
 * 因此存在两个使用约束：
 * - 使用未在此列出的配置（如 `legend`、`toolbox`）时 TS 会报错，但运行时是可用的，
 *   因为对应组件已在下方 `echarts.use` 中注册——需要类型支持时请把对应 Option 补进本联合类型；
 * - 使用**未注册**的图表类型（如 `map`、`gauge`）会在运行时静默不渲染，务必先 `use` 再使用。
 *
 * 通过 `ComposeOption` 组合出只包含已注册组件与图表的 Option 类型。
 */
export type ECOption = ComposeOption<
  | BarSeriesOption
  | DatasetComponentOption
  | GridComponentOption
  | LineSeriesOption
  | TitleComponentOption
  | TooltipComponentOption
>;

// 注册必须的组件
echarts.use([
  TitleComponent,
  PieChart,
  RadarChart,
  TooltipComponent,
  GridComponent,
  DatasetComponent,
  TransformComponent,
  BarChart,
  LineChart,
  LabelLayout,
  UniversalTransition,
  CanvasRenderer,
  LegendComponent,
  ToolboxComponent,
]);

export default echarts;
