package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.WorkflowFormDO;
import com.njydsz.pmis.workflow.mapper.WorkflowFormMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkflowFormServiceImpl 单元测试
 */
@DisplayName("WorkflowFormServiceImpl 测试")
class WorkflowFormServiceImplTest {

    private WorkflowFormMapper mapper;
    private WorkflowFormServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(WorkflowFormMapper.class);
        service = new WorkflowFormServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 重复 formKey 应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(mapper.selectByFormKey("leave_form")).thenReturn(form(1L, "leave_form"));
        WorkflowFormDO f = form(null, "leave_form");
        assertThatThrownBy(() -> service.create(f))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 缺 formKey 应抛 BAD_REQUEST")
    void create_noKey() {
        WorkflowFormDO f = new WorkflowFormDO();
        assertThatThrownBy(() -> service.create(f))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 正常路径应设置默认 version/status/tenant")
    void create_ok() {
        when(mapper.selectByFormKey("k1")).thenReturn(null);
        when(mapper.insert(any(WorkflowFormDO.class))).thenAnswer(inv -> {
            WorkflowFormDO f = inv.getArgument(0);
            f.setId(10L);
            return 1;
        });
        WorkflowFormDO f = form(null, "k1");
        Long id = service.create(f);
        assertThat(id).isEqualTo(10L);
        assertThat(f.getVersion()).isEqualTo(1);
        assertThat(f.getStatus()).isEqualTo("ENABLED");
        assertThat(f.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("update 不存在应抛 NOT_FOUND")
    void update_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        WorkflowFormDO f = form(99L, "k");
        assertThatThrownBy(() -> service.update(f))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("update 修改 formKey 应拒绝")
    void update_changeKey() {
        when(mapper.selectById(1L)).thenReturn(form(1L, "old_key"));
        WorkflowFormDO f = form(1L, "new_key");
        f.setFormName("改名");
        assertThatThrownBy(() -> service.update(f))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("getById 不存在应抛 NOT_FOUND")
    void getById_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    private WorkflowFormDO form(Long id, String key) {
        WorkflowFormDO f = new WorkflowFormDO();
        f.setId(id);
        f.setFormKey(key);
        f.setFormName("测试表单");
        f.setSchemaJson("{}");
        return f;
    }
}
