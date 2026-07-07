package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.entity.GlueCodeDO;
import com.njydsz.pmis.cronjob.mapper.GlueCodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GlueCodeServiceImpl} 单元测试（P1-2 GLUE 在线编码）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>save: 版本号递增（首次=1，后续=max+1）</li>
 *   <li>save: 参数校验（jobId/sourceCode 为空抛异常）</li>
 *   <li>getLatest: 透传 mapper.selectLatestByJobId</li>
 *   <li>listVersions: 按版本号降序返回</li>
 *   <li>rollback: 创建新版本（内容为目标版本代码）</li>
 *   <li>rollback: 目标版本不存在抛异常</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("GlueCodeServiceImpl GLUE 代码服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlueCodeServiceImplTest {

    @Mock
    private GlueCodeMapper glueCodeMapper;

    @InjectMocks
    private GlueCodeServiceImpl glueCodeService;

    // ==================== save ====================

    @Test
    @DisplayName("save: 首次保存版本号为 1")
    void save_firstVersion_is1() {
        when(glueCodeMapper.selectLatestByJobId("job-1")).thenReturn(null);

        GlueCodeDO result = glueCodeService.save("job-1", "println 'hello'", "GROOVY", "初始版本");

        ArgumentCaptor<GlueCodeDO> captor = ArgumentCaptor.forClass(GlueCodeDO.class);
        verify(glueCodeMapper).insert(captor.capture());
        GlueCodeDO saved = captor.getValue();
        assertEquals("job-1", saved.getJobId());
        assertEquals("println 'hello'", saved.getSourceCode());
        assertEquals("GROOVY", saved.getLanguage());
        assertEquals(1, saved.getVersion());
        assertEquals("初始版本", saved.getRemark());
        // 返回值与传入实体一致
        assertEquals(saved, result);
    }

    @Test
    @DisplayName("save: 后续保存版本号递增")
    void save_subsequentVersion_increments() {
        GlueCodeDO latest = new GlueCodeDO();
        latest.setVersion(3);
        when(glueCodeMapper.selectLatestByJobId("job-1")).thenReturn(latest);

        glueCodeService.save("job-1", "println 'v4'", null, null);

        ArgumentCaptor<GlueCodeDO> captor = ArgumentCaptor.forClass(GlueCodeDO.class);
        verify(glueCodeMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getVersion());
        // language 为空时默认 GROOVY
        assertEquals("GROOVY", captor.getValue().getLanguage());
    }

    @Test
    @DisplayName("save: jobId 为空抛 BizException")
    void save_blankJobId_throwsException() {
        assertThrows(BizException.class, () -> glueCodeService.save("", "code", "GROOVY", null));
        assertThrows(BizException.class, () -> glueCodeService.save(null, "code", "GROOVY", null));
        verify(glueCodeMapper, never()).insert(any(GlueCodeDO.class));
    }

    @Test
    @DisplayName("save: sourceCode 为空抛 BizException")
    void save_blankSource_throwsException() {
        assertThrows(BizException.class, () -> glueCodeService.save("job-1", "", "GROOVY", null));
        assertThrows(BizException.class, () -> glueCodeService.save("job-1", null, "GROOVY", null));
        verify(glueCodeMapper, never()).insert(any(GlueCodeDO.class));
    }

    // ==================== getLatest ====================

    @Test
    @DisplayName("getLatest: 透传 mapper.selectLatestByJobId")
    void getLatest_delegatesToMapper() {
        GlueCodeDO expected = new GlueCodeDO();
        expected.setJobId("job-1");
        expected.setVersion(5);
        when(glueCodeMapper.selectLatestByJobId("job-1")).thenReturn(expected);

        GlueCodeDO result = glueCodeService.getLatest("job-1");

        assertEquals(expected, result);
        verify(glueCodeMapper).selectLatestByJobId("job-1");
    }

    @Test
    @DisplayName("getLatest: 不存在时返回 null")
    void getLatest_notExists_returnsNull() {
        when(glueCodeMapper.selectLatestByJobId("job-x")).thenReturn(null);

        GlueCodeDO result = glueCodeService.getLatest("job-x");

        assertNull(result);
    }

    @Test
    @DisplayName("getLatest: jobId 为空返回 null")
    void getLatest_blankJobId_returnsNull() {
        assertNull(glueCodeService.getLatest(""));
        assertNull(glueCodeService.getLatest(null));
        verify(glueCodeMapper, never()).selectLatestByJobId(any());
    }

    // ==================== listVersions ====================

    @Test
    @DisplayName("listVersions: 返回版本列表（按版本号降序）")
    void listVersions_returnsList() {
        GlueCodeDO v2 = new GlueCodeDO();
        v2.setVersion(2);
        GlueCodeDO v1 = new GlueCodeDO();
        v1.setVersion(1);
        when(glueCodeMapper.selectList(any())).thenReturn(List.of(v2, v1));

        List<GlueCodeDO> result = glueCodeService.listVersions("job-1");

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersion());
        assertEquals(1, result.get(1).getVersion());
    }

    @Test
    @DisplayName("listVersions: 无记录返回空列表")
    void listVersions_noRecords_returnsEmptyList() {
        when(glueCodeMapper.selectList(any())).thenReturn(List.of());

        List<GlueCodeDO> result = glueCodeService.listVersions("job-empty");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listVersions: jobId 为空返回空列表")
    void listVersions_blankJobId_returnsEmptyList() {
        List<GlueCodeDO> result = glueCodeService.listVersions("");

        assertTrue(result.isEmpty());
        verify(glueCodeMapper, never()).selectList(any());
    }

    // ==================== rollback ====================

    @Test
    @DisplayName("rollback: 创建新版本（内容为目标版本代码）")
    void rollback_createsNewVersionWithTargetSource() {
        // 目标版本 v2
        GlueCodeDO target = new GlueCodeDO();
        target.setJobId("job-1");
        target.setSourceCode("println 'v2'");
        target.setLanguage("GROOVY");
        target.setVersion(2);
        when(glueCodeMapper.selectOne(any())).thenReturn(target);
        // 当前最新 v5
        GlueCodeDO latest = new GlueCodeDO();
        latest.setVersion(5);
        when(glueCodeMapper.selectLatestByJobId("job-1")).thenReturn(latest);

        GlueCodeDO result = glueCodeService.rollback("job-1", 2);

        ArgumentCaptor<GlueCodeDO> captor = ArgumentCaptor.forClass(GlueCodeDO.class);
        verify(glueCodeMapper).insert(captor.capture());
        GlueCodeDO newVersion = captor.getValue();
        // 新版本号 = max + 1
        assertEquals(6, newVersion.getVersion());
        // 内容为目标版本代码
        assertEquals("println 'v2'", newVersion.getSourceCode());
        assertEquals("GROOVY", newVersion.getLanguage());
        assertTrue(newVersion.getRemark().contains("rollback to v2"));
        // 返回值为新创建的实体
        assertEquals(newVersion, result);
    }

    @Test
    @DisplayName("rollback: 目标版本不存在抛 BizException")
    void rollback_targetNotFound_throwsException() {
        when(glueCodeMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> glueCodeService.rollback("job-1", 99));
        verify(glueCodeMapper, never()).insert(any(GlueCodeDO.class));
    }

    @Test
    @DisplayName("rollback: jobId 为空抛 BizException")
    void rollback_blankJobId_throwsException() {
        assertThrows(BizException.class, () -> glueCodeService.rollback("", 1));
        assertThrows(BizException.class, () -> glueCodeService.rollback(null, 1));
        verify(glueCodeMapper, never()).insert(any(GlueCodeDO.class));
    }

    @Test
    @DisplayName("rollback: version 无效抛 BizException")
    void rollback_invalidVersion_throwsException() {
        assertThrows(BizException.class, () -> glueCodeService.rollback("job-1", 0));
        assertThrows(BizException.class, () -> glueCodeService.rollback("job-1", -1));
        assertThrows(BizException.class, () -> glueCodeService.rollback("job-1", null));
        verify(glueCodeMapper, never()).insert(any(GlueCodeDO.class));
    }
}
