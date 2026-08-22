package com.njydsz.system.server.service;

import java.io.InputStream;

import com.njydsz.system.domain.vo.ImportResult;

/**
 * 系统配置 Excel 导入导出服务。
 *
 * <p>从 {@link ConfigServiceImpl} 拆分（P1-1 单类职责收敛），承载配置的 Excel 环境迁移能力：
 *
 * <ul>
 *   <li><b>导出</b>：{@link #exportConfigs} — 按分组导出为 Excel（基于 ydsz-common-excel 注解驱动）
 *   <li><b>导入</b>：{@link #importConfigs} — 从 Excel 读取、逐条校验（必填 / 值类型 / DB 唯一性）、
 *       批量插入（insertBatch 消除 N+1）、精准缓存失效、搜索索引同步
 * </ul>
 *
 * <p><b>部分成功语义：</b>导入过程对单条错误不中断（跳过并收集错误明细），返回 {@link ImportResult}
 * 包含成功/跳过/失败条数与逐条错误列表；Excel 解析级异常（文件损坏等）直接抛出，
 * 由全局异常处理器统一返回错误响应。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigService 配置主服务
 */
public interface ConfigExcelService {

  /**
   * 按配置分组导出配置为 Excel 字节数组。
   *
   * @param configGroup 配置分组（为空时导出全部）
   * @return Excel 文件字节数组；无数据时返回空数组
   */
  byte[] exportConfigs(String configGroup);

  /**
   * 从 Excel 流导入配置。
   *
   * @param inputStream Excel 输入流（.xlsx）
   * @return 导入结果（成功/跳过/失败条数 + 逐条错误明细）
   */
  ImportResult importConfigs(InputStream inputStream);
}
