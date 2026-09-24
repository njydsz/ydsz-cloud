package com.njydsz.generator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.service.TemplateImportExportService;
import com.njydsz.generator.vo.TemplateZipVO;

/**
 * 模板导入导出 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@Secured("ROLE_GENERATOR_USER")
@RestController
@RequestMapping("/generator/import-export")
@RequiredArgsConstructor
public class ImportExportController {

  private final TemplateImportExportService importExportService;

  /**
   * 将分组下所有 Velocity 模板导出为 zip 压缩包。
   *
   * <p>zip 包内包含该分组下所有模板文件（文件名保留原始命名），
   * 可直接用于模板迁移或备份。返回二进制文件流，前端需处理下载。
   *
   * <p><b>API-RESP 豁免说明：</b>本端点返回二进制文件流（zip 下载），
   * 非平台统一 JSON 响应体适用场景，豁免 {@code YdszResponse} 包装。
   *
   * @param groupId 模板分组 ID
   * @return zip 二进制流，响应头包含 Content-Disposition: attachment; filename="xxx.zip"
   */
  @GetMapping("/export")
  @Audit(module = "模板管理", action = AuditAction.EXPORT, content = "'导出模板分组:' + #groupId", recordRequest = false)
  public ResponseEntity<byte[]> exportTemplates(@RequestParam Long groupId) {
    log.info("导出模板 groupId={}", groupId);
    TemplateZipVO zip = importExportService.exportZip(groupId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDispositionFormData("attachment", zip.getFileName());
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

    return new ResponseEntity<>(zip.getData(), headers, HttpStatus.OK);
  }

  /**
   * 从 zip 压缩包导入 Velocity 模板到指定分组。
   *
   * <p>zip 包中的每个文件将被导入为一个独立 Velocity 模板。
   * 文件格式要求：zip 内包含模板文本文件（.java/.xml/.vue/.html 等），
   * 文件名作为模板的 fileName 字段。
   *
   * @param groupId   目标模板分组 ID
   * @param file      zip 压缩包文件（multipart/form-data 上传）
   * @param overwrite 是否覆盖同名模板：true 覆盖已有模板，false 跳过同名文件
   * @return 实际导入成功的模板数量
   */
  @PostMapping("/import")
  @Audit(module = "模板管理", action = AuditAction.IMPORT, content = "'导入模板:' + #groupId", recordRequest = false)
  public YdszResponse<Integer> importTemplates(
      @RequestParam Long groupId,
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "false") boolean overwrite) {
    try {
      byte[] data = file.getBytes();
      int count = importExportService.importZip(groupId, data, overwrite);
      log.info("导入模板完成 groupId={} count={}", groupId, count);
      return YdszResponse.success(count);
    } catch (Exception e) {
      log.error("导入模板失败 err={}", e.getMessage(), e);
      return YdszResponse.error("导入失败: " + e.getMessage());
    }
  }
}
