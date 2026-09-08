package com.njydsz.generator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RestController
@RequestMapping("/api/generator/import-export")
@RequiredArgsConstructor
public class ImportExportController {

  private final TemplateImportExportService importExportService;

  /**
   * 导出模板分组为 zip 文件。
   *
   * @param groupId 分组 ID
   * @return zip 二进制流
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
   * 导入模板 zip 到指定分组。
   *
   * @param groupId   目标分组 ID
   * @param file      zip 文件
   * @param overwrite 是否覆盖已有模板
   * @return 导入数量
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
