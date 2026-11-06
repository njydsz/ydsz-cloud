package com.njydsz.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService.VersionCreateResult;
import com.njydsz.nextwiki.domain.service.FileVersionDomainService.VersionRollbackResult;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link FileVersionDomainService} 单元测试。
 *
 * <p>覆盖：版本创建（版本号递增/changeType）、版本回滚、清理筛选、非法节点校验。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("文件版本领域服务")
class FileVersionDomainServiceTest {

  private FileVersionDomainService service;

  @BeforeEach
  void setUp() {
    service = new FileVersionDomainService(new SnowflakeIdGenerator());
  }

  private FileNodeVO fileNode() {
    return FileNodeVO.builder().id("node-1").name("a.txt").nodeType("file").build();
  }

  private FileVersionDTO version(int number) {
    return FileVersionDTO.builder().versionNumber(number).build();
  }

  @Nested
  @DisplayName("createVersion 创建版本")
  class CreateVersion {

    @Test
    @DisplayName("空版本列表从 1 开始，changeType=create")
    void firstVersion() {
      FileNodeVO node = fileNode();
      VersionCreateResult result =
          service.createVersion(node, List.of(), "key-1", 10L, "hash-1", "text/plain", "v1", "user-1");

      assertThat(result.newVersion().getVersionNumber()).isEqualTo(1);
      assertThat(result.newVersion().getChangeType()).isEqualTo("create");
      assertThat(result.newVersion().getIsActive()).isTrue();
      assertThat(result.newVersion().getFileNodeId()).isEqualTo("node-1");
      assertThat(result.updatedFileNode()).isSameAs(node);
      assertThat(node.getCurrentVersion()).isEqualTo(1);
      assertThat(node.getStorageKey()).isEqualTo("key-1");
      assertThat(node.getSize()).isEqualTo(10L);
      assertThat(node.getFileHash()).isEqualTo("hash-1");
      assertThat(node.getMimeType()).isEqualTo("text/plain");
      assertThat(node.getUpdatedBy()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("已有版本时版本号递增，changeType=update")
    void incrementsVersion() {
      FileNodeVO node = fileNode();
      VersionCreateResult result =
          service.createVersion(
              node, List.of(version(1), version(2)), "key-3", 30L, "hash-3", "text/plain", "v3", "user-1");

      assertThat(result.newVersion().getVersionNumber()).isEqualTo(3);
      assertThat(result.newVersion().getChangeType()).isEqualTo("update");
    }

    @Test
    @DisplayName("节点为 null 抛 FILE_NOT_FOUND")
    void nullNodeThrows() {
      BusinessException ex =
          catchThrowableOfType(
              () -> service.createVersion(null, List.of(), "k", 1L, "h", "m", "r", "u"),
              BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.FILE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("节点非文件类型抛 FILE_NOT_FOUND")
    void folderNodeThrows() {
      FileNodeVO folder = FileNodeVO.builder().id("f-1").nodeType("folder").build();
      BusinessException ex =
          catchThrowableOfType(
              () -> service.createVersion(folder, List.of(), "k", 1L, "h", "m", "r", "u"),
              BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.FILE_NOT_FOUND.getCode());
    }
  }

  @Nested
  @DisplayName("rollback 版本回滚")
  class Rollback {

    @Test
    @DisplayName("回滚生成新版本并回填节点存储信息")
    void rollbackBuildsVersion() {
      FileNodeVO node = fileNode();
      FileVersionDTO target =
          FileVersionDTO.builder()
              .versionNumber(2)
              .storageKey("key-2")
              .size(20L)
              .fileHash("hash-2")
              .mimeType("text/plain")
              .build();

      VersionRollbackResult result =
          service.rollback(node, target, List.of(version(1), version(2)), "user-1");

      assertThat(result.targetVersionNumber()).isEqualTo(2);
      assertThat(result.newVersion().getVersionNumber()).isEqualTo(3);
      assertThat(result.newVersion().getChangeType()).isEqualTo("rollback");
      assertThat(result.newVersion().getStorageKey()).isEqualTo("key-2");
      assertThat(result.newVersion().getSize()).isEqualTo(20L);
      assertThat(result.newVersion().getFileHash()).isEqualTo("hash-2");
      assertThat(result.updatedFileNode()).isSameAs(node);
      assertThat(node.getCurrentVersion()).isEqualTo(3);
      assertThat(node.getStorageKey()).isEqualTo("key-2");
      assertThat(node.getSize()).isEqualTo(20L);
    }

    @Test
    @DisplayName("目标版本为 null 抛 VERSION_NOT_FOUND")
    void nullTargetThrows() {
      BusinessException ex =
          catchThrowableOfType(() -> service.rollback(fileNode(), null, List.of(), "u"), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.VERSION_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("节点为 null 抛 FILE_NOT_FOUND")
    void nullNodeThrows() {
      BusinessException ex =
          catchThrowableOfType(() -> service.rollback(null, version(1), List.of(), "u"), BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.FILE_NOT_FOUND.getCode());
    }
  }

  @Nested
  @DisplayName("findVersionsToCleanup 清理筛选")
  class FindVersionsToCleanup {

    @Test
    @DisplayName("未超 20 个版本返回空列表")
    void underLimitReturnsEmpty() {
      List<FileVersionDTO> versions = new ArrayList<>();
      for (int i = 1; i <= 20; i++) {
        versions.add(version(i));
      }
      assertThat(service.findVersionsToCleanup(versions)).isEmpty();
    }

    @Test
    @DisplayName("21 个版本返回最旧的 1 个")
    void overLimitReturnsOldest() {
      List<FileVersionDTO> versions = new ArrayList<>();
      for (int i = 1; i <= 21; i++) {
        versions.add(version(i));
      }
      List<FileVersionDTO> toClean = service.findVersionsToCleanup(versions);
      assertThat(toClean).hasSize(1);
      assertThat(toClean.get(0).getVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("25 个版本返回最旧的 5 个且升序")
    void returnsOldestFiveInOrder() {
      List<FileVersionDTO> versions = new ArrayList<>();
      for (int i = 1; i <= 25; i++) {
        versions.add(version(i));
      }
      assertThat(service.findVersionsToCleanup(versions))
          .extracting(FileVersionDTO::getVersionNumber)
          .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("乱序输入按版本号升序返回最旧的 5 个")
    void sortsByVersionNumber() {
      // 25 个乱序版本，保留最近 20 个 → 返回最旧 5 个
      int[] shuffled = {25, 3, 21, 1, 20, 2, 19, 4, 18, 5, 17, 6, 16, 7, 15, 8, 14, 9, 13, 10, 12, 11, 24, 22, 23};
      List<FileVersionDTO> many = new ArrayList<>();
      for (int n : shuffled) {
        many.add(version(n));
      }
      assertThat(many).hasSize(25);
      assertThat(service.findVersionsToCleanup(many))
          .extracting(FileVersionDTO::getVersionNumber)
          .containsExactly(1, 2, 3, 4, 5);
    }
  }
}
