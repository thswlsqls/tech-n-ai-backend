package com.tech.n.ai.api.emergingtech.facade;

import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechBatchRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechListRequest;
import com.tech.n.ai.api.emergingtech.dto.response.EmergingTechBatchResponse;
import com.tech.n.ai.api.emergingtech.service.EmergingTechCommandService;
import com.tech.n.ai.api.emergingtech.service.EmergingTechQueryService;
import com.tech.n.ai.common.core.constants.ErrorCodeConstants;
import com.tech.n.ai.common.core.exception.BusinessException;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * EmergingTechFacade 단위 테스트 — 다건 생성 집계
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmergingTechFacade 단위 테스트")
class EmergingTechFacadeTest {

    @Mock
    private EmergingTechQueryService queryService;

    @Mock
    private EmergingTechCommandService commandService;

    @InjectMocks
    private EmergingTechFacade facade;

    @Nested
    @DisplayName("createEmergingTechBatch")
    class CreateEmergingTechBatch {

        @Test
        @DisplayName("신규와 중복을 나눠 센다")
        void 신규와_중복을_나눠_센다() {
            // Given: 3건 중 2건이 신규, 1건이 중복
            EmergingTechBatchRequest request = batchRequest(3);
            when(commandService.saveEmergingTechAll(anyList())).thenReturn(List.of(
                new EmergingTechCommandService.SaveResult(document(), true),
                new EmergingTechCommandService.SaveResult(document(), false),
                new EmergingTechCommandService.SaveResult(document(), true)));

            // When
            EmergingTechBatchResponse response = facade.createEmergingTechBatch(request);

            // Then
            assertThat(response.totalCount()).isEqualTo(3);
            assertThat(response.newCount()).isEqualTo(2);
            assertThat(response.duplicateCount()).isEqualTo(1);
            assertThat(response.successCount()).isEqualTo(3);
            assertThat(response.failureCount()).isZero();
            assertThat(response.failureMessages()).isEmpty();
        }

        @Test
        @DisplayName("요청 안 중복으로 접힌 자리는 신규가 아니라 중복으로 센다")
        void 접힌_자리는_중복으로_센다() {
            // Given: 2건이 같은 문서를 가리키고 뒤쪽이 접힌 자리다
            EmergingTechDocument shared = document();
            EmergingTechBatchRequest request = batchRequest(2);
            when(commandService.saveEmergingTechAll(anyList())).thenReturn(List.of(
                new EmergingTechCommandService.SaveResult(shared, true),
                new EmergingTechCommandService.SaveResult(shared, false)));

            // When
            EmergingTechBatchResponse response = facade.createEmergingTechBatch(request);

            // Then: newCount 가 실제 저장 건수와 맞아야 한다
            assertThat(response.newCount()).isEqualTo(1);
            assertThat(response.duplicateCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("저장이 실패하면 그 요청을 전건 실패로 보고한다")
        void 저장_실패는_전건_실패다() {
            // Given
            EmergingTechBatchRequest request = batchRequest(10);
            when(commandService.saveEmergingTechAll(anyList()))
                .thenThrow(new RuntimeException("duplicate key"));

            // When
            EmergingTechBatchResponse response = facade.createEmergingTechBatch(request);

            // Then: 항목 단위 집계가 아니다 — failureCount 는 0 아니면 totalCount 다
            assertThat(response.totalCount()).isEqualTo(10);
            assertThat(response.failureCount()).isEqualTo(10);
            assertThat(response.newCount()).isZero();
            assertThat(response.duplicateCount()).isZero();
            assertThat(response.successCount()).isZero();
            assertThat(response.failureMessages()).hasSize(1);
            assertThat(response.failureMessages().getFirst()).contains("count=10", "duplicate key");
        }
    }

    @Nested
    @DisplayName("getEmergingTechList - 날짜 형식 검증")
    class GetEmergingTechListDateValidation {

        @Test
        @DisplayName("startDate가 YYYY-MM-DD 형식이 아니면 BAD_REQUEST 예외가 나고 QueryService를 호출하지 않는다")
        void startDate_형식이_틀리면_BAD_REQUEST() {
            // Given: 구분자가 '/'인 startDate
            EmergingTechListRequest request = listRequest("2024/01/01", null);

            // When & Then
            assertThatThrownBy(() -> facade.getEmergingTechList(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startDate")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCodeConstants.BAD_REQUEST);
            verifyNoInteractions(queryService);
        }

        @Test
        @DisplayName("endDate가 없는 날짜(13월)면 BAD_REQUEST 예외가 나고 QueryService를 호출하지 않는다")
        void endDate_없는_날짜면_BAD_REQUEST() {
            // Given: 13월인 endDate
            EmergingTechListRequest request = listRequest(null, "2024-13-01");

            // When & Then
            assertThatThrownBy(() -> facade.getEmergingTechList(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endDate")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCodeConstants.BAD_REQUEST);
            verifyNoInteractions(queryService);
        }

        @Test
        @DisplayName("올바른 날짜와 null은 같은 값 그대로 QueryService에 넘긴다")
        void 올바른_날짜와_null은_그대로_넘긴다() {
            // Given: startDate는 올바른 형식, endDate는 없음
            EmergingTechListRequest request = listRequest("2024-01-01", null);
            when(queryService.findEmergingTechs(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            // When
            facade.getEmergingTechList(request);

            // Then
            verify(queryService).findEmergingTechs(
                isNull(), isNull(), isNull(), isNull(), eq("2024-01-01"), isNull(), any(Pageable.class));
        }
    }

    private EmergingTechListRequest listRequest(String startDate, String endDate) {
        return new EmergingTechListRequest(1, 20, null, null, null, null, startDate, endDate, null);
    }

    private EmergingTechBatchRequest batchRequest(int size) {
        List<EmergingTechCreateRequest> items = java.util.stream.IntStream.range(0, size)
            .mapToObj(i -> EmergingTechCreateRequest.builder()
                .provider("GITHUB")
                .updateType("FRAMEWORK_UPDATE")
                .title("Test Title " + i)
                .url("https://example.com/" + i)
                .sourceType("RSS")
                .status("PENDING")
                .externalId("ext-" + i)
                .build())
            .toList();
        return new EmergingTechBatchRequest(items);
    }

    private EmergingTechDocument document() {
        EmergingTechDocument doc = new EmergingTechDocument();
        doc.setId(new ObjectId());
        doc.setTitle("Test Title");
        doc.setCreatedAt(LocalDateTime.now());
        return doc;
    }
}
