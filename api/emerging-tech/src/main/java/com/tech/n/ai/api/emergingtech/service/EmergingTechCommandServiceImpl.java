package com.tech.n.ai.api.emergingtech.service;

import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;
import com.tech.n.ai.domain.mongodb.repository.EmergingTechRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Emerging Tech 명령 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergingTechCommandServiceImpl implements EmergingTechCommandService {

    private final EmergingTechRepository emergingTechRepository;
    private final EmergingTechQueryService queryService;
    private final EmbeddingModel embeddingModel;

    @Override
    public SaveResult saveEmergingTech(EmergingTechCreateRequest request) {
        Optional<EmergingTechDocument> duplicate = findDuplicate(request);
        if (duplicate.isPresent()) {
            return new SaveResult(duplicate.get(), false);
        }

        EmergingTechDocument document = createDocument(request);
        return new SaveResult(emergingTechRepository.save(document), true);
    }

    @Override
    public EmergingTechDocument updateStatus(String id, PostStatus status) {
        EmergingTechDocument document = queryService.findEmergingTechById(id);
        document.setStatus(status.name());
        document.setUpdatedAt(LocalDateTime.now());
        return emergingTechRepository.save(document);
    }

    /**
     * externalId, url 기준 중복 검사
     */
    private Optional<EmergingTechDocument> findDuplicate(EmergingTechCreateRequest request) {
        if (request.externalId() != null) {
            var existing = emergingTechRepository.findByExternalId(request.externalId());
            if (existing.isPresent()) {
                log.debug("이미 존재하는 Emerging Tech: externalId={}", request.externalId());
                return existing;
            }
        }

        if (request.url() != null) {
            var existingByUrl = emergingTechRepository.findByUrl(request.url());
            if (existingByUrl.isPresent()) {
                log.debug("이미 존재하는 Emerging Tech: url={}", request.url());
                return existingByUrl;
            }
        }

        return Optional.empty();
    }

    /**
     * 요청 DTO → Document 변환
     */
    private EmergingTechDocument createDocument(EmergingTechCreateRequest request) {
        EmergingTechDocument document = new EmergingTechDocument();
        document.setProvider(request.provider());
        document.setUpdateType(request.updateType());
        document.setTitle(request.title());
        document.setSummary(request.summary());
        document.setUrl(request.url());
        document.setPublishedAt(request.publishedAt());
        document.setSourceType(request.sourceType());
        document.setStatus(request.status());
        document.setExternalId(request.externalId());

        if (request.metadata() != null) {
            EmergingTechDocument.EmergingTechMetadata metadata = new EmergingTechDocument.EmergingTechMetadata();
            metadata.setVersion(request.metadata().version());
            metadata.setTags(request.metadata().tags());
            metadata.setAuthor(request.metadata().author());
            metadata.setGithubRepo(request.metadata().githubRepo());
            metadata.setAdditionalInfo(request.metadata().additionalInfo());
            document.setMetadata(metadata);
        }

        // 임베딩 생성 (title + summary)
        generateEmbedding(document);

        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        return document;
    }

    /**
     * 임베딩 텍스트 및 벡터 생성
     * 실패 시에도 문서 저장은 진행됩니다.
     */
    private void generateEmbedding(EmergingTechDocument document) {
        try {
            EmergingTechDocument.EmergingTechMetadata metadata = document.getMetadata();
            List<String> tags = metadata != null ? metadata.getTags() : null;
            String githubRepo = metadata != null ? metadata.getGithubRepo() : null;
            String embeddingText = buildEmbeddingText(
                document.getProvider(), githubRepo,
                document.getTitle(), document.getSummary(), tags);
            document.setEmbeddingText(embeddingText);

            Embedding embedding = embeddingModel.embed(embeddingText).content();
            List<Float> vector = embedding.vectorAsList();
            document.setEmbeddingVector(vector);

            log.info("임베딩 생성 완료: title={}, vectorSize={}", document.getTitle(), vector.size());
        } catch (Exception e) {
            log.error("임베딩 생성 실패: title={}, error={}", document.getTitle(), e.getMessage(), e);
        }
    }

    private String buildEmbeddingText(String provider, String githubRepo,
                                      String title, String summary, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        if (provider != null) {
            sb.append(provider);
        }
        if (githubRepo != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(githubRepo);
        }
        if (title != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(title);
        }
        if (summary != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(summary);
        }
        if (tags != null && !tags.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(String.join(" ", tags));
        }
        return sb.toString();
    }
}
