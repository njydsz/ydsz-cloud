package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.repository.FileVersionRepository;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.server.service.VersionDiffService.DiffResult;

/**
 * 版本对比应用服务。
 *
 * <p>协调版本内容获取与 diff 计算，提供端到端的版本对比能力。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionDiffApplicationService {

  private final FileVersionRepository fileVersionRepository;
  private final IFileStorageProvider fileStorageProvider;
  private final VersionDiffService versionDiffService;

  /**
   * 对比两个版本的差异。
   *
   * <p>从存储下载两个版本的文本内容，然后执行行级 diff。 使用存储默认 bucket（版本化文件统一存储在应用默认桶中）。
   *
   * @param fileNodeId 文件节点 ID
   * @param oldVersion 旧版本号
   * @param newVersion 新版本号
   * @return diff 结果
   */
  public DiffResult diffVersions(String fileNodeId, int oldVersion, int newVersion) {
    FileVersionVO oldVer = fileVersionRepository
        .findByFileNodeIdAndVersion(fileNodeId, oldVersion)
        .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + oldVersion));
    FileVersionVO newVer = fileVersionRepository
        .findByFileNodeIdAndVersion(fileNodeId, newVersion)
        .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + newVersion));

    // 检查是否支持 diff
    if (!versionDiffService.isDiffSupported(newVer.getMimeType(), newVer.getSize())) {
      throw new UnsupportedOperationException(
          "该文件类型不支持版本对比（仅支持文本文件，文件大小不超过 1MB）");
    }

    IFileStorage storage = fileStorageProvider.getStorage();
    if (storage == null) {
      throw new IllegalStateException("存储服务未初始化");
    }

    // 使用存储默认 bucket（传 null 使用配置默认值）
    String oldContent = downloadTextContent(storage, null, oldVer.getStorageKey());
    String newContent = downloadTextContent(storage, null, newVer.getStorageKey());

    return versionDiffService.diff(oldContent, newContent);
  }

  /**
   * 获取文件的版本历史列表（用于前端选择对比版本）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 版本 VO 列表（按版本号降序）
   */
  public List<FileVersionVO> getVersionHistory(String fileNodeId) {
    return fileVersionRepository.findByFileNodeId(fileNodeId);
  }

  // ==================== 私有方法 ====================

  /** 下载指定存储对象的文本内容 */
  private String downloadTextContent(IFileStorage storage, String bucketName, String storageKey) {
    try (InputStream is = storage.downloadAsStream(bucketName, storageKey)) {
      return versionDiffService.readTextContent(is);
    } catch (Exception e) {
      log.warn("[VersionDiffApplicationService] 下载版本内容失败: storageKey={}, err={}",
          storageKey, e.getMessage());
      return "";
    }
  }
}
