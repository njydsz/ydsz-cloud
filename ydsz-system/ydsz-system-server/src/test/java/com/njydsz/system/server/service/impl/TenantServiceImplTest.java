package com.njydsz.system.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.query.TenantPageQuery;
import com.njydsz.system.domain.repository.TenantRepository;
import com.njydsz.system.domain.vo.TenantVO;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @InjectMocks
    private TenantServiceImpl tenantService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantProperties tenantProperties;

    @Mock
    private I18nMessages i18nMessages;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("getById - existing tenant returns VO")
        void getById_existingTenant_returnsVo() {
            TenantVO vo = new TenantVO();
            vo.setId("t-001");
            vo.setTenantCode("ACME");
            when(tenantRepository.findById("t-001")).thenReturn(Optional.of(vo));

            TenantVO result = tenantService.getById("t-001");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("t-001");
            assertThat(result.getTenantCode()).isEqualTo("ACME");
        }

        @Test
        @DisplayName("getById - non-existing tenant returns null")
        void getById_nonExisting_returnsNull() {
            when(tenantRepository.findById("t-999")).thenReturn(Optional.empty());

            assertThat(tenantService.getById("t-999")).isNull();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("save - unique tenantCode succeeds")
        void save_uniqueCode_succeeds() {
            TenantDTO dto = new TenantDTO();
            dto.setId("t-new");
            dto.setTenantCode("NEWCO");
            dto.setTenantName("New Company");

            when(tenantRepository.countByCondition(any(TenantPageQuery.class))).thenReturn(0L);
            when(tenantRepository.insert(dto)).thenReturn(true);

            tenantService.save(dto);

            verify(tenantRepository).insert(dto);
            verify(tenantRepository).countByCondition(any(TenantPageQuery.class));
        }

        @Test
        @DisplayName("save - duplicate tenantCode throws exception")
        void save_duplicateCode_throwsException() {
            TenantDTO dto = new TenantDTO();
            dto.setTenantCode("ACME");
            dto.setTenantName("Acme Corp");

            when(tenantRepository.countByCondition(any(TenantPageQuery.class))).thenReturn(1L);

            assertThatThrownBy(() -> tenantService.save(dto))
                    .isInstanceOf(BusinessException.class);

            verify(tenantRepository, never()).insert(any(TenantDTO.class));
        }
    }

    @Nested
    @DisplayName("updateById")
    class UpdateById {

        @Test
        @DisplayName("updateById - same tenantCode skips uniqueness check")
        void updateById_sameCode_skipsCheck() {
            TenantDTO dto = new TenantDTO();
            dto.setId("t-001");
            dto.setTenantCode("ACME");

            TenantVO existing = new TenantVO();
            existing.setId("t-001");
            existing.setTenantCode("ACME");
            when(tenantRepository.findById("t-001")).thenReturn(Optional.of(existing));
            when(tenantRepository.updateById(dto)).thenReturn(true);

            boolean result = tenantService.updateById(dto);

            assertThat(result).isTrue();
            verify(tenantRepository, never()).countByCondition(any(TenantPageQuery.class));
        }

        @Test
        @DisplayName("updateById - changed tenantCode to duplicate throws exception")
        void updateById_changedDuplicateCode_throwsException() {
            TenantDTO dto = new TenantDTO();
            dto.setId("t-001");
            dto.setTenantCode("DUPE");

            TenantVO existing = new TenantVO();
            existing.setId("t-001");
            existing.setTenantCode("ACME");

            when(tenantRepository.findById("t-001")).thenReturn(Optional.of(existing));
            when(tenantRepository.countByCondition(any(TenantPageQuery.class))).thenReturn(1L);

            assertThatThrownBy(() -> tenantService.updateById(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("updateById - non-existing tenant skips all checks")
        void updateById_nonExisting_skipsChecks() {
            TenantDTO dto = new TenantDTO();
            dto.setId("t-999");
            dto.setTenantCode("ANY");

            when(tenantRepository.findById("t-999")).thenReturn(Optional.empty());
            when(tenantRepository.updateById(dto)).thenReturn(false);

            boolean result = tenantService.updateById(dto);

            assertThat(result).isFalse();
            verify(tenantRepository, never()).countByCondition(any(TenantPageQuery.class));
        }
    }

    @Nested
    @DisplayName("removeById")
    class RemoveById {

        @Test
        @DisplayName("removeById - non-existing tenant returns false")
        void removeById_nonExisting_returnsFalse() {
            when(tenantRepository.findById("t-999")).thenReturn(Optional.empty());

            boolean result = tenantService.removeById("t-999");

            assertThat(result).isFalse();
            verify(tenantRepository, never()).deleteById(any(String.class));
        }

        @Test
        @DisplayName("removeById - builtin DEFAULT tenant throws exception")
        void removeById_builtinTenant_throwsException() {
            TenantVO builtin = new TenantVO();
            builtin.setId("t-000");
            builtin.setTenantCode("DEFAULT");
            builtin.setStatus("DISABLED");
            when(tenantRepository.findById("t-000")).thenReturn(Optional.of(builtin));

            assertThatThrownBy(() -> tenantService.removeById("t-000"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("removeById - builtin MASTER tenant throws exception")
        void removeById_masterTenant_throwsException() {
            TenantVO builtin = new TenantVO();
            builtin.setId("t-001");
            builtin.setTenantCode("MASTER");
            builtin.setStatus("DISABLED");
            when(tenantRepository.findById("t-001")).thenReturn(Optional.of(builtin));

            assertThatThrownBy(() -> tenantService.removeById("t-001"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("removeById - active ENABLED tenant throws exception")
        void removeById_enabledTenant_throwsException() {
            TenantVO active = new TenantVO();
            active.setId("t-002");
            active.setTenantCode("ACME");
            active.setStatus("ENABLED");
            when(tenantRepository.findById("t-002")).thenReturn(Optional.of(active));
            when(i18nMessages.resolve(any(String.class))).thenReturn("linked protected");

            assertThatThrownBy(() -> tenantService.removeById("t-002"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("removeById - inactive non-builtin tenant succeeds")
        void removeById_inactiveNonBuiltin_succeeds() {
            TenantVO expired = new TenantVO();
            expired.setId("t-003");
            expired.setTenantCode("OLDTENANT");
            expired.setStatus("DISABLED");
            when(tenantRepository.findById("t-003")).thenReturn(Optional.of(expired));
            when(tenantRepository.deleteById("t-003")).thenReturn(true);

            boolean result = tenantService.removeById("t-003");

            assertThat(result).isTrue();
            verify(tenantRepository).deleteById("t-003");
        }
    }

    @Nested
    @DisplayName("existsByTenantCode")
    class ExistsByTenantCode {

        @Test
        @DisplayName("existsByTenantCode - existing returns true")
        void existsByTenantCode_existing_returnsTrue() {
            TenantPageQuery query = new TenantPageQuery();
            query.setTenantCode("ACME");
            when(tenantRepository.countByCondition(any(TenantPageQuery.class))).thenReturn(1L);

            assertThat(tenantService.existsByTenantCode("ACME")).isTrue();
        }

        @Test
        @DisplayName("existsByTenantCode - not existing returns false")
        void existsByTenantCode_notExisting_returnsFalse() {
            when(tenantRepository.countByCondition(any(TenantPageQuery.class))).thenReturn(0L);

            assertThat(tenantService.existsByTenantCode("GHOST")).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("existsByTenantCode - null/blank code queries repository")
        void existsByTenantCode_nullOrBlankCode_queries(String input) {
            when(tenantRepository.countByCondition(any(TenantPageQuery.class))).thenReturn(0L);

            assertThat(tenantService.existsByTenantCode(input)).isFalse();
        }
    }

    @Nested
    @DisplayName("page")
    class Page {

        @Test
        @DisplayName("page - delegates to repository")
        void page_delegatesToRepository() {
            TenantPageQuery query = new TenantPageQuery();
            PageResponse<List<TenantVO>> expected = new PageResponse<>();
            when(tenantRepository.findByPage(query)).thenReturn(expected);

            PageResponse<List<TenantVO>> result = tenantService.page(query);

            assertThat(result).isSameAs(expected);
        }
    }
}
