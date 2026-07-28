/**
 * 前端 API 类型自动生成配置
 *
 * 通过 openapi-typescript-codegen 从后端 OpenAPI 3 规范自动生成 TypeScript 类型，
 * 消除前端手动定义 VO/DTO 类型的重复工作。
 *
 * 使用方式：
 *   1. 后端启动后访问 /v3/api-docs 获取 OpenAPI JSON
 *   2. 执行 pnpm run gen:api 生成类型文件
 *   3. 生成的类型文件位于各子应用 src/api/generated/ 目录
 *
 * @author ydsz-team
 * @since 1.0.0
 */

import { defineConfig } from '@openapi-typescript-codegen/cli';

export default defineConfig({
  // 后端 OpenAPI 文档地址（各子应用按需修改）
  input: 'http://localhost:8080/v3/api-docs',

  // 输出目录（各子应用按需修改）
  output: 'src/api/generated',

  // 使用 @ydsz/request 作为 HTTP 客户端
  httpClient: 'custom',

  // 生成选项
  exportCore: false,        // 不生成核心 client（使用 @ydsz/request）
  exportServices: true,     // 生成 Service 类
  exportModels: true,       // 生成 Model 类型
  exportSchemas: false,     // 不生成 Zod schema
  useOptions: true,         // 使用 options 对象参数
  useUnionTypes: true,     // 使用联合类型

  // 后缀
  serviceSuffix: 'Service',  // Service 类后缀
  modelSuffix: 'VO',         // Model 类后缀
});
