package com.tech.n.ai.api.emergingtech.service;

import com.tech.n.ai.api.emergingtech.dto.request.EmergingTechCreateRequest;
import com.tech.n.ai.domain.mongodb.document.EmergingTechDocument;
import com.tech.n.ai.domain.mongodb.enums.PostStatus;
import com.tech.n.ai.domain.mongodb.repository.EmergingTechRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        return saveEmergingTechAll(List.of(request)).get(0);
    }

    /**
     * 요청 건수와 무관하게 중복 조회 2회, 임베딩 1회, 저장 1회로 처리한다.
     */
    @Override
    public List<SaveResult> saveEmergingTechAll(List<EmergingTechCreateRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<EmergingTechDocument> duplicates = findDuplicates(requests);

        List<EmergingTechDocument> newDocuments = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            if (duplicates.get(i) == null) {
                newDocuments.add(createDocument(requests.get(i)));
            }
        }

        generateEmbeddings(newDocuments);

        List<EmergingTechDocument> saved = newDocuments.isEmpty()
            ? List.of()
            : emergingTechRepository.saveAll(newDocuments);

        return toResults(duplicates, saved);
    }

    @Override
    public EmergingTechDocument updateStatus(String id, PostStatus status) {
        EmergingTechDocument document = queryService.findEmergingTechById(id);
        document.setStatus(status.name());
        document.setUpdatedAt(LocalDateTime.now());
        return emergingTechRepository.save(document);
    }

    /**
     * externalId, url 기준 중복 검사.
     * 요청과 같은 길이의 목록을 돌려주며, 신규인 자리는 null 이다.
     */
    private List<EmergingTechDocument> findDuplicates(List<EmergingTechCreateRequest> requests) {
        Set<String> externalIds = keysOf(requests, EmergingTechCreateRequest::externalId);
        Set<String> urls = keysOf(requests, EmergingTechCreateRequest::url);

        Map<String, EmergingTechDocument> byExternalId = externalIds.isEmpty() ? Map.of()
            : indexBy(emergingTechRepository.findByExternalIdIn(externalIds),
                      EmergingTechDocument::getExternalId);
        Map<String, EmergingTechDocument> byUrl = urls.isEmpty() ? Map.of()
            : indexBy(emergingTechRepository.findByUrlIn(urls), EmergingTechDocument::getUrl);

        List<EmergingTechDocument> duplicates = new ArrayList<>(requests.size());
        for (EmergingTechCreateRequest request : requests) {
            EmergingTechDocument found = null;
            if (request.externalId() != null) {
                found = byExternalId.get(request.externalId());
            }
            if (found == null && request.url() != null) {
                found = byUrl.get(request.url());
            }
            duplicates.add(found);
        }
        return duplicates;
    }

    private Set<String> keysOf(List<EmergingTechCreateRequest> requests,
                               Function<EmergingTechCreateRequest, String> keyOfRequest) {
        return requests.stream()
            .map(keyOfRequest)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private Map<String, EmergingTechDocument> indexBy(List<EmergingTechDocument> documents,
                                                      Function<EmergingTechDocument, String> keyOfDocument) {
        Map<String, EmergingTechDocument> index = new HashMap<>();
        for (EmergingTechDocument document : documents) {
            index.putIfAbsent(keyOfDocument.apply(document), document);
        }
        return index;
    }

    /**
     * 요청 DTO → Document 변환. 임베딩 벡터는 {@link #generateEmbeddings}가 채운다.
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

        EmergingTechDocument.EmergingTechMetadata metadata = document.getMetadata();
        List<String> tags = metadata != null ? metadata.getTags() : null;
        String githubRepo = metadata != null ? metadata.getGithubRepo() : null;
        document.setEmbeddingText(buildEmbeddingText(
            document.getProvider(), githubRepo,
            document.getTitle(), document.getSummary(), tags));

        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        return document;
    }

    /**
     * 임베딩 벡터를 한 번의 호출로 생성한다.
     * 실패 시에도 문서 저장은 진행됩니다.
     * 임베딩 텍스트가 빈 문서는 TextSegment 가 거부하므로 호출 대상에서 뺀다.
     */
    private void generateEmbeddings(List<EmergingTechDocument> documents) {
        List<EmergingTechDocument> targets = documents.stream()
            .filter(d -> d.getEmbeddingText() != null && !d.getEmbeddingText().isBlank())
            .toList();

        if (targets.isEmpty()) {
            return;
        }

        try {
            List<TextSegment> segments = targets.stream()
                .map(d -> TextSegment.from(d.getEmbeddingText()))
                .toList();

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            for (int i = 0; i < targets.size(); i++) {
                targets.get(i).setEmbeddingVector(embeddings.get(i).vectorAsList());
            }

            log.info("임베딩 생성 완료: count={}, vectorSize={}",
                targets.size(), embeddings.getFirst().vectorAsList().size());
        } catch (Exception e) {
            log.error("임베딩 생성 실패: count={}, error={}", targets.size(), e.getMessage(), e);
        }
    }

    /**
     * 요청 순서대로 결과를 조립한다. 신규 자리는 저장 결과에서 순서대로 꺼낸다.
     */
    private List<SaveResult> toResults(List<EmergingTechDocument> duplicates,
                                       List<EmergingTechDocument> saved) {
        List<SaveResult> results = new ArrayList<>(duplicates.size());
        Iterator<EmergingTechDocument> savedIterator = saved.iterator();
        for (EmergingTechDocument duplicate : duplicates) {
            if (duplicate != null) {
                results.add(new SaveResult(duplicate, false));
            } else {
                results.add(new SaveResult(savedIterator.next(), true));
            }
        }
        return results;
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
