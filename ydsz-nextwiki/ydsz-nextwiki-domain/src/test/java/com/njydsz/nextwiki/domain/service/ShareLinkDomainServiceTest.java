package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link ShareLinkDomainService} 单元测试。
 *
 * <p>覆盖：分享创建、访问验证（过期/次数/提取码）、撤销、到期筛选、用户过滤。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("分享链接领域服务")
class ShareLinkDomainServiceTest {

  private ShareLinkDomainService service;

  @BeforeEach
  void setUp() {
    service = new ShareLinkDomainService(new SnowflakeIdGenerator());
  }

  private FileNodeVO node() {
    return FileNodeVO.builder().id("node-1").name("test.txt").build();
  }

  private ShareLinkDTO activeLink() {
    return ShareLinkDTO.builder()
        .id("share-1")
        .fileNodeId("node-1")
        .shareCode("123")
        .extractCode("1234")
        .shareType("view")
        .status("active")
        .accessCount(0)
        .maxAccessCount(10)
        .isReminderSent(false)
        .createdBy("user-1")
        .build();
  }

  @Nested
  @DisplayName("createShare 创建分享")
  class CreateShare {

    @Test
    @DisplayName("公开分享：shareTargetType=PUBLIC，无接收者")
    void publicShare() {
      ShareLinkDomainService.CreateShareResult result =
          service.createShare(node(), "view", null, null, null, "user-1");

      assertThat(result.shareLink().getShareTargetType()).isEqualTo("PUBLIC");
      assertThat(result.shareLink().getStatus()).isEqualTo("active");
      assertThat(result.shareLink().getAccessCount()).isZero();
      assertThat(result.shareLink().getCreatedBy()).isEqualTo("user-1");
      assertThat(result.recipients()).isEmpty();
    }

    @Test
    @DisplayName("定向分享：shareTargetType=USER，接收者数量匹配")
    void targetedShare() {
      ShareLinkDomainService.CreateShareResult result =
          service.createShare(
              node(), "view", null, null, null, List.of("u1", "u2", "u3"), "title", "user-1");

      assertThat(result.shareLink().getShareTargetType()).isEqualTo("USER");
      assertThat(result.shareLink().getTitle()).isEqualTo("title");
      assertThat(result.recipients()).hasSize(3);
      assertThat(result.recipients())
          .extracting(r -> r.getRecipientId())
          .containsExactlyInAnyOrder("u1", "u2", "u3");
    }

    @Test
    @DisplayName("提取码为 4 位数字")
    void extractCodeIsFourDigits() {
      ShareLinkDomainService.CreateShareResult result =
          service.createShare(node(), "view", null, null, null, "user-1");
      String code = result.shareLink().getExtractCode();
      assertThat(code).hasSize(4).matches("\\d{4}");
    }

    @Test
    @DisplayName("增强版与基础版 shareTargetType 一致")
    void basicAndEnhancedAligned() {
      ShareLinkDomainService.CreateShareResult basic =
          service.createShare(node(), "view", null, null, null, "user-1");
      ShareLinkDomainService.CreateShareResult enhanced =
          service.createShare(node(), "view", null, null, null, null, null, "user-1");
      assertThat(basic.shareLink().getShareTargetType())
          .isEqualTo(enhanced.shareLink().getShareTargetType());
    }
  }

  @Nested
  @DisplayName("verifyAccess 访问验证")
  class VerifyAccess {

    @Test
    @DisplayName("有效链接 + 正确提取码 → 通过")
    void validLinkPasses() {
      ShareLinkDTO link = activeLink();
      ShareLinkDTO result = service.verifyAccess(link, "1234");
      assertThat(result).isSameAs(link);
    }

    @Test
    @DisplayName("提取码错误 → 抛 SHARE_EXTRACT_CODE_ERROR")
    void wrongExtractCodeThrows() {
      ShareLinkDTO link = activeLink();
      BusinessException ex = catchThrowableOfType(() -> service.verifyAccess(link, "9999"), BusinessException.class);
          assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.SHARE_EXTRACT_CODE_ERROR.getCode());
    }

    @Test
    @DisplayName("无提取码的链接跳过校验")
    void noExtractCodeSkipsCheck() {
      ShareLinkDTO link = activeLink();
      link.setExtractCode(null);
      assertThat(service.verifyAccess(link, null)).isSameAs(link);
    }

    @Test
    @DisplayName("已过期 → 状态置 EXPIRED 并抛 SHARE_EXPIRED")
    void expiredLinkThrowsAndMarks() {
      ShareLinkDTO link = activeLink();
      link.setExpireTime(LocalDateTime.now().minusHours(1));
      BusinessException ex = catchThrowableOfType(() -> service.verifyAccess(link, "1234"), BusinessException.class);
          assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.SHARE_EXPIRED.getCode());
      assertThat(link.getStatus()).isEqualTo("expired");
    }

    @Test
    @DisplayName("超过最大访问次数 → 抛 SHARE_ACCESS_LIMIT")
    void accessLimitThrows() {
      ShareLinkDTO link = activeLink();
      link.setAccessCount(10);
      link.setMaxAccessCount(10);
      BusinessException ex = catchThrowableOfType(() -> service.verifyAccess(link, "1234"), BusinessException.class);
          assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.SHARE_ACCESS_LIMIT.getCode());
    }

    @Test
    @DisplayName("终态（REVOKED）→ 抛 SHARE_EXPIRED")
    void revokedLinkThrows() {
      ShareLinkDTO link = activeLink();
      link.setStatus("revoked");
      BusinessException ex = catchThrowableOfType(() -> service.verifyAccess(link, "1234"), BusinessException.class);
          assertThat(ex.getCode()).isEqualTo(NextwikiExceptionCode.SHARE_EXPIRED.getCode());
    }
  }

  @Nested
  @DisplayName("revoke 撤销分享")
  class Revoke {

    @Test
    @DisplayName("状态置 REVOKED，记录操作人")
    void revokeSetsStatusAndOperator() {
      ShareLinkDTO link = activeLink();
      service.revoke(link, "admin-1");
      assertThat(link.getStatus()).isEqualTo("revoked");
      assertThat(link.getUpdatedBy()).isEqualTo("admin-1");
    }
  }

  @Nested
  @DisplayName("findExpiringShares 到期筛选")
  class FindExpiringShares {

    @Test
    @DisplayName("只返回 active 且未提醒且在窗口内的链接")
    void filtersExpiring() {
      ShareLinkDTO expiringSoon = activeLink();
      expiringSoon.setId("a");
      expiringSoon.setExpireTime(LocalDateTime.now().plusHours(1));

      ShareLinkDTO expiredAlready = activeLink();
      expiredAlready.setId("b");
      expiredAlready.setExpireTime(LocalDateTime.now().minusHours(1));

      ShareLinkDTO farFuture = activeLink();
      farFuture.setId("c");
      farFuture.setExpireTime(LocalDateTime.now().plusHours(48));

      ShareLinkDTO reminded = activeLink();
      reminded.setId("d");
      reminded.setExpireTime(LocalDateTime.now().plusHours(1));
      reminded.setIsReminderSent(true);

      ShareLinkDTO revoked = activeLink();
      revoked.setId("e");
      revoked.setStatus("revoked");
      revoked.setExpireTime(LocalDateTime.now().plusHours(1));

      ShareLinkDTO noExpire = activeLink();
      noExpire.setId("f");
      noExpire.setExpireTime(null);

      List<ShareLinkDTO> result =
          service.findExpiringShares(
              List.of(expiringSoon, expiredAlready, farFuture, reminded, revoked, noExpire), 24);

      assertThat(result).extracting(ShareLinkDTO::getId).containsExactly("a");
    }

    @Test
    @DisplayName("窗口内无到期链接返回空列表")
    void emptyWhenNoneExpiring() {
      assertThat(service.findExpiringShares(List.of(), 24)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByUserId 用户过滤")
  class FindByUserId {

    @Test
    @DisplayName("只返回指定用户的 active 链接")
    void filtersByUser() {
      ShareLinkDTO mine = activeLink();
      mine.setId("mine");

      ShareLinkDTO others = activeLink();
      others.setId("others");
      others.setCreatedBy("user-2");

      ShareLinkDTO revoked = activeLink();
      revoked.setId("revoked");
      revoked.setStatus("revoked");

      List<ShareLinkDTO> result = service.findByUserId(List.of(mine, others, revoked), "user-1");
      assertThat(result).extracting(ShareLinkDTO::getId).containsExactly("mine");
    }
  }

  @Nested
  @DisplayName("markReminderSent 提醒标记")
  class MarkReminderSent {

    @Test
    @DisplayName("标记为已提醒")
    void marksReminderSent() {
      ShareLinkDTO link = activeLink();
      service.markReminderSent(link);
      assertThat(link.getIsReminderSent()).isTrue();
    }
  }
}
