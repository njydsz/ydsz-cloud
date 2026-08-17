package com.njydsz.nextwiki.server.service.upload;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * 安全校验步骤：检查上传文件大小、类型、扩展名黑名单。
 *
 * <p>校验失败时抛 {@link BusinessException}（FILE_TOO_LARGE / FILE_TYPE_NOT_ALLOWED / FILE_NAME_INVALID），
 * 管道终止。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityValidationStep implements UploadStep {

  /** 禁止上传的文件扩展名（安全黑名单） */
  private static final Set<String> BLOCKED_EXTENSIONS =
      Set.of("exe", "bat", "cmd", "sh", "com", "msi", "dll", "scr", "vbs", "jar", "war");

  private final long maxFileSize;

  public SecurityValidationStep(long maxFileSize) {
    this.maxFileSize = maxFileSize;
  }

  @Override
  public boolean execute(UploadContext context) {
    MultipartFile file = context.getFile();

    // 1. 文件为空校验
    if (file == null || file.isEmpty()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_UPLOAD_EMPTY);
    }

    // 2. 文件大小校验
    if (file.getSize() > maxFileSize) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_TOO_LARGE)
          .data("maxSize", maxFileSize)
          .data("actualSize", file.getSize());
    }

    // 3. 文件名校验
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || originalFilename.trim().isEmpty()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NAME_EMPTY);
    }
    if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NAME_INVALID)
          .data("fileName", originalFilename);
    }

    // 4. 扩展名黑名单校验
    String suffix = extractSuffix(originalFilename);
    if (BLOCKED_EXTENSIONS.contains(suffix.toLowerCase())) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_TYPE_NOT_ALLOWED)
          .data("suffix", suffix);
    }

    log.debug("[SecurityValidationStep] 安全校验通过: name={}, size={}", originalFilename, file.getSize());
    return true;
  }

  @Override
  public String getName() {
    return "security_validation";
  }

  /**
   * 提取文件后缀（不含点，小写）。
   *
   * @param fileName 文件名
   * @return 后缀字符串
   */
  private String extractSuffix(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dotIdx = fileName.lastIndexOf('.');
    return (dotIdx >= 0 && dotIdx < fileName.length() - 1)
        ? fileName.substring(dotIdx + 1).toLowerCase()
        : "";
  }
}
