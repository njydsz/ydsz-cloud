package com.njydsz.userinfo.server.service;

import java.io.InputStream;

import com.njydsz.userinfo.domain.dto.UserImportDTO;
import com.njydsz.userinfo.domain.dto.UserImportResultDTO;

/**
 * 用户 Excel 导入导出服务接口
 *
 * <p>提供用户数据的 Excel 批量导入和导出能力，基于 common-excel 模块实现。
 *
 * <p><b>导入流程：</b>
 *
 * <ol>
 *   <li>读取 Excel 文件解析为 {@link UserImportDTO} 列表
 *   <li>逐行校验（必填字段、用户名唯一、部门编码存在等）
 *   <li>逐行创建用户（失败记入 failDetails，不中断后续行）
 *   <li>返回导入结果（总数、成功数、失败数、失败明细）
 * </ol>
 *
 * <p><b>导出流程：</b>
 *
 * <ol>
 *   <li>查询用户列表（可按条件过滤）
 *   <li>转换为导出 DTO
 *   <li>使用 common-excel 生成 Excel 字节数组
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserExcelService {

  /**
   * 批量导入用户（Excel）
   *
   * <p>一次性读取全部数据到内存后处理，建议文件大小不超过 5MB（约 1 万行）。
   *
   * @param inputStream Excel 文件输入流
   * @param originalFilename 原始文件名（用于日志）
   * @return 导入结果
   */
  UserImportResultDTO importUsers(InputStream inputStream, String originalFilename);

  /**
   * 导出用户列表为 Excel 字节数组
   *
   * <p>导出全部用户数据（不区分分页），建议仅在数据量可控时使用。
   *
   * @return Excel 文件字节数组
   */
  byte[] exportUsers();

  /**
   * 获取导入模板 Excel 字节数组
   *
   * <p>下载空模板 + 一行示例数据，供业务方填写后上传。
   *
   * @return 模板 Excel 文件字节数组
   */
  byte[] getImportTemplate();
}
