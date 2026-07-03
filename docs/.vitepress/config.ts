import { defineConfig } from 'vitepress'

/**
 * PMIS 文档站 VitePress 配置
 *
 * <p>覆盖系统规范、规则引擎 API、Python SDK、CLI 工具等文档。
 * 启动：npm run dev（默认 http://localhost:5173）
 * 构建：npm run build（输出到 docs/.vitepress/dist）
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
export default defineConfig({
  lang: 'zh-CN',
  title: 'PMIS 文档',
  description: '南京云顶 PMIS 项目管理系统开发者文档',
  lastUpdated: true,
  cleanUrls: true,

  head: [
    ['meta', { name: 'theme-color', content: '#3c8cff' }]
  ],

  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      {
        text: '规范',
        items: [
          { text: 'API 规范', link: '/standards/api-spec' },
          { text: '后端规范', link: '/standards/backend-spec' },
          { text: '前端规范', link: '/standards/frontend-spec' },
          { text: '数据库规范', link: '/standards/database-spec' },
          { text: '命名规范', link: '/standards/naming-convention' },
          { text: 'Git 工作流', link: '/standards/git-workflow' }
        ]
      },
      {
        text: '规则引擎',
        items: [
          { text: 'API 总览', link: '/api/rules-engine' },
          { text: 'AI 增强', link: '/api/rules-ai' },
          { text: '分布式执行', link: '/api/rules-distributed' },
          { text: '规则集市场', link: '/rules/rule-pack-market' },
          { text: '链路追踪', link: '/rules/rule-trace-replay' },
          { text: '画布编排', link: '/rules/rule-chain-graph' }
        ]
      },
      {
        text: 'SDK & CLI',
        items: [
          { text: 'Python SDK', link: '/sdk/python-sdk' },
          { text: 'CLI 工具', link: '/sdk/cli' }
        ]
      }
    ],

    sidebar: {
      '/standards/': [
        {
          text: '工程规范',
          items: [
            { text: 'API 规范', link: '/standards/api-spec' },
            { text: '后端规范', link: '/standards/backend-spec' },
            { text: '前端规范', link: '/standards/frontend-spec' },
            { text: '数据库规范', link: '/standards/database-spec' },
            { text: '命名规范', link: '/standards/naming-convention' },
            { text: '代码质量', link: '/standards/code-quality' },
            { text: 'Git 工作流', link: '/standards/git-workflow' },
            { text: '文档规范', link: '/standards/documentation' }
          ]
        }
      ],
      '/api/': [
        {
          text: '规则引擎 API',
          items: [
            { text: 'API 总览', link: '/api/rules-engine' },
            { text: 'AI 增强端点', link: '/api/rules-ai' },
            { text: '分布式执行', link: '/api/rules-distributed' }
          ]
        }
      ],
      '/sdk/': [
        {
          text: 'SDK & CLI',
          items: [
            { text: 'Python SDK', link: '/sdk/python-sdk' },
            { text: 'CLI 工具', link: '/sdk/cli' }
          ]
        }
      ],
      '/rules/': [
        {
          text: '规则引擎特性',
          items: [
            { text: 'AI 生成闭环', link: '/rules/rule-ai-generation-loop' },
            { text: '断点调试', link: '/rules/rule-breakpoint' },
            { text: '灰度发布', link: '/rules/rule-canary' },
            { text: '画布编排', link: '/rules/rule-chain-graph' },
            { text: '冲突检测', link: '/rules/rule-conflict-detection' },
            { text: '表达式校验', link: '/rules/rule-expression-validation' },
            { text: '执行指标', link: '/rules/rule-metrics' },
            { text: '规则集市场', link: '/rules/rule-pack-market' },
            { text: 'QLExpress 骨架', link: '/rules/rule-qlexpress-skeleton' },
            { text: '评分卡脚本', link: '/rules/rule-scorecard-tree-script' },
            { text: '模板 SPI', link: '/rules/rule-template-spi' },
            { text: '链路回放', link: '/rules/rule-trace-replay' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/ydsz-pmis' }
    ],

    footer: {
      message: '基于 VitePress 构建',
      copyright: 'Copyright © 2026 南京云顶 PMIS 团队'
    },

    search: {
      provider: 'local'
    },

    outline: {
      level: [2, 3],
      label: '本页目录'
    },

    docFooter: {
      prev: '上一页',
      next: '下一页'
    },

    lastUpdatedText: '最后更新',

    returnToTopLabel: '回到顶部',
    sidebarMenuLabel: '菜单',
    darkModeSwitchLabel: '主题',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式'
  }
})
