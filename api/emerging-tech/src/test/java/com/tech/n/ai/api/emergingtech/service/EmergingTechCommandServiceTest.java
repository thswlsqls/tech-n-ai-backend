package com.tech.n.ai.api.emergingtech.service;

import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;
import com.tech.n.ai.domain.mongodb.repository.EmergingTechRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * EmergingTechCommandService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmergingTechCommandService 단위 테스트")
class EmergingTechCommandServiceTest {

    @Mock
    private EmergingTechRepository emergingTechRepository;

    @Mock
    private EmergingTechQueryService queryService;

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private EmergingTechCommandServiceImpl commandService;

    // ========== saveEmergingTech 테스트 ==========

    @Nested
    @DisplayName("saveEmergingTech")
    class SaveEmergingTech {

        @Test
        @DisplayName("신규 저장 - SaveResult(isNew=true) 반환")
        void saveEmergingTech_신규() {
            // Given
            EmergingTechCreateRequest request = createRequest("ext-123", "https://example.com/new");

            when(emergingTechRepository.findByExternalIdIn(any())).thenReturn(List.of());
            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());
            stubEmbedAll();
            stubSaveAll();

            // When
            EmergingTechCommandService.SaveResult result = commandService.saveEmergingTech(request);

            // Then
            assertThat(result.isNew()).isTrue();
            assertThat(result.document()).isNotNull();
            assertThat(result.document().getTitle()).isEqualTo("Test Title");
            verify(emergingTechRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("externalId 중복 시 - SaveResult(isNew=false) 반환")
        void saveEmergingTech_중복_externalId() {
            // Given
            EmergingTechCreateRequest request = createRequest("ext-123", "https://example.com/new");
            EmergingTechDocument existing = createDocument();
            existing.setExternalId("ext-123");

            when(emergingTechRepository.findByExternalIdIn(any())).thenReturn(List.of(existing));
            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());

            // When
            EmergingTechCommandService.SaveResult result = commandService.saveEmergingTech(request);

            // Then
            assertThat(result.isNew()).isFalse();
            assertThat(result.document()).isEqualTo(existing);
            verify(emergingTechRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("url 중복 시 - SaveResult(isNew=false) 반환")
        void saveEmergingTech_중복_url() {
            // Given
            EmergingTechCreateRequest request = createRequest(null, "https://example.com/existing");
            EmergingTechDocument existing = createDocument();
            existing.setUrl("https://example.com/existing");

            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of(existing));

            // When
            EmergingTechCommandService.SaveResult result = commandService.saveEmergingTech(request);

            // Then
            assertThat(result.isNew()).isFalse();
            assertThat(result.document()).isEqualTo(existing);
            verify(emergingTechRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("externalId 없이 신규 저장")
        void saveEmergingTech_externalId_없이_신규() {
            // Given
            EmergingTechCreateRequest request = createRequest(null, "https://example.com/new");

            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());
            stubEmbedAll();
            stubSaveAll();

            // When
            EmergingTechCommandService.SaveResult result = commandService.saveEmergingTech(request);

            // Then
            assertThat(result.isNew()).isTrue();
            verify(emergingTechRepository, never()).findByExternalIdIn(any());
            verify(emergingTechRepository).saveAll(anyList());
        }
    }

    // ========== saveEmergingTechAll 테스트 ==========

    @Nested
    @DisplayName("saveEmergingTechAll")
    class SaveEmergingTechAll {

        @Test
        @DisplayName("건수와 무관하게 조회 2회 · 임베딩 1회 · 저장 1회로 끝난다")
        void 왕복_횟수는_건수에_비례하지_않는다() {
            // Given: 배치 잡의 chunk 크기와 같은 10건
            List<EmergingTechCreateRequest> requests = IntStream.range(0, 10)
                .mapToObj(i -> createRequest("ext-" + i, "https://example.com/" + i))
                .map(EmergingTechCreateRequest.class::cast)
                .toList();

            when(emergingTechRepository.findByExternalIdIn(any())).thenReturn(List.of());
            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());
            stubEmbedAll();
            stubSaveAll();

            // When
            List<EmergingTechCommandService.SaveResult> results = commandService.saveEmergingTechAll(requests);

            // Then
            assertThat(results).hasSize(10);
            assertThat(results).allMatch(EmergingTechCommandService.SaveResult::isNew);

            // 건별로 돌면 조회 20회 · 임베딩 10회 · 저장 10회가 된다
            verify(emergingTechRepository, times(1)).findByExternalIdIn(any());
            verify(emergingTechRepository, times(1)).findByUrlIn(any());
            verify(embeddingModel, times(1)).embedAll(anyList());
            verify(emergingTechRepository, times(1)).saveAll(anyList());
            verify(emergingTechRepository, never()).findByExternalId(any());
            verify(emergingTechRepository, never()).findByUrl(any());
            verify(emergingTechRepository, never()).save(any());
        }

        @Test
        @DisplayName("임베딩은 한 번의 호출에 전건이 실린다")
        void 임베딩_호출에_전건이_실린다() {
            // Given
            List<EmergingTechCreateRequest> requests = IntStream.range(0, 10)
                .mapToObj(i -> createRequest("ext-" + i, "https://example.com/" + i))
                .map(EmergingTechCreateRequest.class::cast)
                .toList();

            when(emergingTechRepository.findByExternalIdIn(any())).thenReturn(List.of());
            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());
            stubEmbedAll();
            stubSaveAll();

            // When
            commandService.saveEmergingTechAll(requests);

            // Then
            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingModel).embedAll(captor.capture());
            assertThat(captor.getValue()).hasSize(10);
        }

        @Test
        @DisplayName("중복과 신규가 섞여도 결과는 요청 순서를 지킨다")
        void 결과는_요청_순서를_지킨다() {
            // Given: 0번과 2번은 이미 있고 1번만 신규다
            List<EmergingTechCreateRequest> requests = List.of(
                createRequest("ext-0", "https://example.com/0"),
                createRequest("ext-1", "https://example.com/1"),
                createRequest("ext-2", "https://example.com/2"));

            EmergingTechDocument existing0 = createDocument();
            existing0.setExternalId("ext-0");
            EmergingTechDocument existing2 = createDocument();
            existing2.setExternalId("ext-2");

            when(emergingTechRepository.findByExternalIdIn(any())).thenReturn(List.of(existing0, existing2));
            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());
            stubEmbedAll();
            stubSaveAll();

            // When
            List<EmergingTechCommandService.SaveResult> results = commandService.saveEmergingTechAll(requests);

            // Then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).isNew()).isFalse();
            assertThat(results.get(0).document()).isEqualTo(existing0);
            assertThat(results.get(1).isNew()).isTrue();
            assertThat(results.get(1).document().getExternalId()).isEqualTo("ext-1");
            assertThat(results.get(2).isNew()).isFalse();
            assertThat(results.get(2).document()).isEqualTo(existing2);
        }

        @Test
        @DisplayName("빈 목록이면 아무 왕복도 하지 않는다")
        void 빈_목록은_왕복하지_않는다() {
            // When
            List<EmergingTechCommandService.SaveResult> results = commandService.saveEmergingTechAll(List.of());

            // Then
            assertThat(results).isEmpty();
            verifyNoInteractions(emergingTechRepository, embeddingModel);
        }

        @Test
        @DisplayName("임베딩이 실패해도 저장은 진행한다")
        void 임베딩_실패해도_저장한다() {
            // Given
            List<EmergingTechCreateRequest> requests = List.of(
                createRequest("ext-1", "https://example.com/1"));

            when(emergingTechRepository.findByExternalIdIn(any())).thenReturn(List.of());
            when(emergingTechRepository.findByUrlIn(any())).thenReturn(List.of());
            when(embeddingModel.embedAll(anyList())).thenThrow(new RuntimeException("임베딩 서버 오류"));
            stubSaveAll();

            // When
            List<EmergingTechCommandService.SaveResult> results = commandService.saveEmergingTechAll(requests);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).isNew()).isTrue();
            assertThat(results.get(0).document().getEmbeddingVector()).isNull();
            verify(emergingTechRepository).saveAll(anyList());
        }
    }

    // ========== updateStatus 테스트 ==========

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("PUBLISHED로 상태 변경 성공")
        void updateStatus_승인() {
            // Given
            String id = new ObjectId().toHexString();
            EmergingTechDocument document = createDocument();

            when(queryService.findEmergingTechById(id)).thenReturn(document);
            when(emergingTechRepository.save(any(EmergingTechDocument.class))).thenReturn(document);

            // When
            EmergingTechDocument result = commandService.updateStatus(id, PostStatus.PUBLISHED);

            // Then
            assertThat(result.getStatus()).isEqualTo(PostStatus.PUBLISHED.name());
            verify(emergingTechRepository).save(document);
        }

        @Test
        @DisplayName("REJECTED로 상태 변경 성공")
        void updateStatus_거부() {
            // Given
            String id = new ObjectId().toHexString();
            EmergingTechDocument document = createDocument();

            when(queryService.findEmergingTechById(id)).thenReturn(document);
            when(emergingTechRepository.save(any(EmergingTechDocument.class))).thenReturn(document);

            // When
            EmergingTechDocument result = commandService.updateStatus(id, PostStatus.REJECTED);

            // Then
            assertThat(result.getStatus()).isEqualTo(PostStatus.REJECTED.name());
        }

        @Test
        @DisplayName("존재하지 않는 ID - ResourceNotFoundException")
        void updateStatus_미존재() {
            // Given
            String id = new ObjectId().toHexString();

            when(queryService.findEmergingTechById(id))
                .thenThrow(new ResourceNotFoundException("Emerging Tech를 찾을 수 없습니다: " + id));

            // When & Then
            assertThatThrownBy(() -> commandService.updateStatus(id, PostStatus.PUBLISHED))
                .isInstanceOf(ResourceNotFoundException.class);
            verify(emergingTechRepository, never()).save(any());
        }
    }

    // ========== 헬퍼 메서드 ==========

    private EmergingTechCreateRequest createRequest(String externalId, String url) {
        return EmergingTechCreateRequest.builder()
            .provider("GITHUB")
            .updateType("FRAMEWORK_UPDATE")
            .title("Test Title")
            .summary("Test Summary")
            .url(url)
            .publishedAt(LocalDateTime.now())
            .sourceType("RSS")
            .status("PENDING")
            .externalId(externalId)
            .build();
    }

    /** 요청과 같은 개수의 벡터를 돌려준다. 실제 임베딩 서버를 부르지 않는다 */
    private void stubEmbedAll() {
        when(embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                .map(segment -> Embedding.from(List.of(0.1f, 0.2f)))
                .toList();
            return Response.from(embeddings);
        });
    }

    /** saveAll 이 받은 문서에 id 를 채워 그대로 돌려준다 */
    private void stubSaveAll() {
        when(emergingTechRepository.saveAll(anyList())).thenAnswer(invocation -> {
            Collection<EmergingTechDocument> documents = invocation.getArgument(0);
            List<EmergingTechDocument> saved = new ArrayList<>();
            for (EmergingTechDocument document : documents) {
                document.setId(new ObjectId());
                saved.add(document);
            }
            return saved;
        });
    }

    private EmergingTechDocument createDocument() {
        EmergingTechDocument doc = new EmergingTechDocument();
        doc.setId(new ObjectId());
        doc.setProvider("GITHUB");
        doc.setUpdateType("FRAMEWORK_UPDATE");
        doc.setTitle("Test Title");
        doc.setUrl("https://example.com");
        doc.setStatus("PENDING");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }
}
