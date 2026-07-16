/**
 * @fileoverview CDN 外置配置
 * @description 生产环境通过 CDN 加载第三方库，减少打包体积：
 * - vite.config.ts 会导入 CDN_DEPS 用于 external 与 index.html 注入
 * - 前端运行时可通过 CDN_ENABLED 判断是否启用 CDN
 * - 仅外置「全量导入」且「体积大、API 稳定」的库
 * @module config/cdn
 * @author ydsz-team
 * @since 1.0.0
 */

/** 单个 CDN 依赖描述 */
export interface CdnDependency {
  /** npm 包名（需与 import 时的包名一致，用于 rollup external 匹配） */
  name: string
  /** CDN UMD/IIFE 包暴露的全局变量名（用于 rollup output.globals 映射） */
  var: string
  /** CDN JS 资源 URL */
  url: string
  /** 需要注入的 CSS 资源（可选） */
  css?: string[]
}

/**
 * 需要通过 CDN 外置的依赖清单
 *
 * 版本号取自 package.json 实际安装版本（node_modules），保持与本地一致以避免运行时差异。
 *
 * 说明：
 *  - 仅外置「全量导入」且「体积大、API 稳定」的库
 *  - element-plus 当前通过 unplugin-vue-components 的 ElementPlusResolver 做按需注册，
 *    业务代码大量使用 element-plus/es/... 深路径导入；仅 external 顶层 'element-plus'
 *    会导致深路径仍被打包而顶层走全局变量，运行时不一致。改为全量 CDN 需同步调整
 *    main.ts 注册逻辑、CSS 引入方式与 resolver 配置，改动大且易回归，暂不外置。
 *  - echarts 原本按需引入（echarts/core + 子模块），外置完整 CDN 包后 src/utils/echarts.ts
 *    已改为完整包导入，API 保持兼容。
 */
export const CDN_DEPS: CdnDependency[] = [
  {
    name: 'vue',
    var: 'Vue',
    url: 'https://unpkg.com/vue@3.5.39/dist/vue.global.prod.js',
  },
  {
    name: 'vue-router',
    var: 'VueRouter',
    url: 'https://unpkg.com/vue-router@4.6.4/dist/vue-router.global.prod.js',
  },
  {
    name: 'pinia',
    var: 'Pinia',
    url: 'https://unpkg.com/pinia@2.3.1/dist/pinia.iife.prod.js',
  },
  {
    name: 'echarts',
    var: 'echarts',
    url: 'https://unpkg.com/echarts@5.6.0/dist/echarts.min.js',
  },
  {
    name: 'dayjs',
    var: 'dayjs',
    url: 'https://unpkg.com/dayjs@1.11.21/dayjs.min.js',
  },
  // element-plus 暂不外置，原因见上方注释
  // {
  //   name: 'element-plus',
  //   var: 'ElementPlus',
  //   url: 'https://unpkg.com/element-plus@2.8.4/dist/index.full.min.js',
  //   css: [
  //     'https://unpkg.com/element-plus@2.8.4/dist/index.css',
  //     'https://unpkg.com/element-plus@2.8.4/dist/theme-chalk/dark/css-vars.css',
  //   ],
  // },
]

/**
 * 是否启用 CDN（仅生产环境启用）
 *
 * 注意：本模块会被 vite.config.ts 在 Node 环境导入（用于读取 CDN_DEPS），
 * 因此对 import.meta.env 做安全访问，避免 Node 环境下访问 undefined 报错。
 * vite.config.ts 中的 external 判定使用 mode 自行计算，不依赖此常量。
 */
const __env = (typeof import.meta !== 'undefined' && (import.meta as { env?: Record<string, unknown> }).env) || {}
export const CDN_ENABLED: boolean = !!__env.PROD && __env.VITE_CDN_ENABLED !== 'false'
