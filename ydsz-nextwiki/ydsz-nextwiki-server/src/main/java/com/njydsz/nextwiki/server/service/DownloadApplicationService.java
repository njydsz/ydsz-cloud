package com.njydsz.nextwiki.server.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

/**
 * 文件下载服务。
 *
 * <p>处理单文件/批量/断点续传下载。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadApplicationService {

  private final FileNodeRepository fileNodeRepository;
  private final DownloadRateLimitService rateLimitService;

  @Autowired(required = false)
  private IFileStorageProvider fileStorageProvider;

  /**
   * 准备下载：校验文件存在性 → 限流 → 解析存储。
   *
   * <p>仅组装下载所需的 {@link DownloadContext}（FileNodeVO + IFileStorage），不实际传输字节， 真正的流式下载由 Controller 持有
   * storage 后执行。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID（参与用户级限流）
   * @param ip 客户端 IP（参与 IP 级限流）
   * @return 下载上下文（含文件节点与存储实例）
   * @throws BusinessException 文件不存在/非文件（FILE_NOT_FOUND）或限流触发（RATE_LIMIT_EXCEEDED）
   * @complexity O(1)（一次 DB 查询 + 一次 Redis 原子限流）
   * @note 无事务边界（只读 + 限流计数）；存储实例可能为 {@code null}（未配置时），由调用方处理
   * @concurrency 限流基于 Redis 固定窗口，天然支持多实例；结果不可缓存
   */
  public DownloadContext prepareDownload(String nodeId, String userId, String ip) {
    FileNodeVO node = fileNodeRepository.findById(nodeId).orElse(null);
    if (node == null || !node.isFile()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    DownloadRateLimitService.RateLimitResult rateResult =
        rateLimitService.checkRateLimit(userId, ip, nodeId);
    if (!rateResult.isAllowed()) {
      throw BusinessException.of(NextwikiExceptionCode.RATE_LIMIT_EXCEEDED)
          .data("message", rateResult.getMessage());
    }

    IFileStorage storage = resolveStorage();
    return DownloadContext.builder().node(node).storage(storage).build();
  }

  /**
   * 生成签名下载 URL（时效性 + 用户/IP 绑定，防盗链）。
   *
   * <p>先校验文件存在，再委托 {@link DownloadRateLimitService#generateSignedDownloadUrl} 生成签名 URL。
   *
   * @param nodeId 文件节点 ID
   * @param userId 用户 ID（写入签名，校验时绑定）
   * @param ip 客户端 IP（写入签名，校验时绑定）
   * @return 签名下载 URL（如 {@code /nextwiki/download/{sign}?expires=...}）
   * @throws BusinessException 文件不存在/非文件时抛出
   * @complexity O(1)（一次 DB 查询 + 一次 Redis 写入）
   * @note 无事务边界；URL 有效期由 {@code nextwiki.download.signed-url-expire-seconds} 控制
   */
  public String generateSignedUrl(String nodeId, String userId, String ip) {
    FileNodeVO node = fileNodeRepository.findById(nodeId).orElse(null);
    if (node == null || !node.isFile()) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }
    return rateLimitService.generateSignedDownloadUrl(node.getStorageKey(), userId, ip);
  }

  /**
   * 解析签名下载 URL：校验签名与有效期 → 还原 storageKey → 解析存储。
   *
   * <p>校验失败（过期或签名无效）抛出 {@code SIGN_URL_EXPIRED}；成功后由限流服务标记签名已使用（一次性）。
   *
   * @param sign 签名串（URL 路径中的 {sign}）
   * @param expireTime 签名中的过期时间戳（秒级）
   * @return 签名下载上下文（含 storageKey 与存储实例）
   * @throws BusinessException 签名过期或无效时抛出 SIGN_URL_EXPIRED
   * @complexity O(1)（一次 Redis 读取 + 删除）
   * @note 无事务边界；存储实例可能为 {@code null}
   * @security 签名一次性使用，校验后即删除，防止重放
   */
  public SignedDownloadContext resolveSignedDownload(String sign, long expireTime) {
    String storageKey = rateLimitService.verifySignedUrl(sign, expireTime);
    if (storageKey == null) {
      throw new BusinessException(NextwikiExceptionCode.SIGN_URL_EXPIRED);
    }
    IFileStorage storage = resolveStorage();
    return SignedDownloadContext.builder().storageKey(storageKey).storage(storage).build();
  }

  private IFileStorage resolveStorage() {
    if (fileStorageProvider != null) {
      return fileStorageProvider.getStorage();
    }
    return null;
  }

  /**
   * 获取存储实例（供 Controller 在文件夹打包下载等场景直接调用）。
   *
   * <p>仅透传 {@link #resolveStorage()}；未配置存储时返回 {@code null}。
   *
   * @return 文件存储实例，未配置时返回 {@code null}
   * @note 只读，无副作用
   */
  public IFileStorage resolveStorageForDownload() {
    return resolveStorage();
  }

  /** 下载上下文 */
  @Data
  @Builder
  public static class DownloadContext {
    /** 待下载文件节点（含 storageKey、大小、后缀等元数据） */
    private FileNodeVO node;

    /** 文件存储实例，未配置时为 {@code null} */
    private IFileStorage storage;
  }

  /** 签名下载上下文（经签名 URL 校验后还原的下载目标）。 */
  @Data
  @Builder
  public static class SignedDownloadContext {
    /** 存储对象键（由签名还原，对应对象存储中的实际文件） */
    private String storageKey;

    /** 文件存储实例，未配置时为 {@code null} */
    private IFileStorage storage;
  }
}
