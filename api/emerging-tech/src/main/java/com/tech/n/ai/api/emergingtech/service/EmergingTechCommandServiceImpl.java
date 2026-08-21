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
import java.util.Arrays;
import java.util.HashMap;
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
        BatchPlan plan = planNewDocuments(requests, duplicates);

        generateEmbeddings(plan.newDocuments());

        List<EmergingTechDocument> saved = plan.newDocuments().isEmpty()
            ? List.of()
            : emergingTechRepository.saveAll(plan.newDocuments());

        return toResults(duplicates, plan, saved);
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
     *
     * 실패해도 문서 저장은 진행한다. 다만 <b>실패 단위가 문서 하나가 아니라 목록 전체다</b> —
     * 건별로 부르던 때는 실패한 문서만 벡터를 잃었지만, 지금은 이 호출이 실패하면
     * 그 요청의 신규 문서가 전부 벡터 없이 저장된다.
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
     * 저장 계획.
     *
     * @param newDocuments          실제로 저장할 문서. 요청 안 중복은 한 번만 담긴다
     * @param savedIndexOfPosition  요청 자리 → {@code newDocuments} 인덱스. DB 중복 자리는 -1
     * @param representative        그 자리가 문서를 새로 만든 자리인가. 접힌 자리는 false
     */
    private record BatchPlan(List<EmergingTechDocument> newDocuments,
                             int[] savedIndexOfPosition,
                             boolean[] representative) {}

    /**
     * 저장할 문서를 고른다.
     *
     * 중복 조회는 요청 처리 시작 시점의 DB 만 보므로 같은 요청 안의 앞선 항목을 보지 못한다.
     * 그대로 두면 같은 {@code url} 이 두 번 담긴 요청이 unique 인덱스에서 터진다.
     * 그래서 신규 자리끼리 externalId → url 순으로 한 번 더 접고 대표는 앞 위치로 둔다.
     * 키가 {@code null} 인 자리는 접지 않는다 — 값이 없는 것끼리 같다고 볼 수 없다.
     */
    private BatchPlan planNewDocuments(List<EmergingTechCreateRequest> requests,
                                       List<EmergingTechDocument> duplicates) {
        int size = requests.size();
        int[] savedIndexOfPosition = new int[size];
        boolean[] representative = new boolean[size];
        Arrays.fill(savedIndexOfPosition, -1);

        Map<String, Integer> byExternalId = new HashMap<>();
        Map<String, Integer> byUrl = new HashMap<>();
        List<EmergingTechDocument> newDocuments = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            if (duplicates.get(i) != null) {
                continue;
            }

            EmergingTechCreateRequest request = requests.get(i);
            Integer folded = request.externalId() != null ? byExternalId.get(request.externalId()) : null;
            if (folded == null && request.url() != null) {
                folded = byUrl.get(request.url());
            }
            if (folded != null) {
                savedIndexOfPosition[i] = savedIndexOfPosition[folded];
                continue;
            }

            representative[i] = true;
            savedIndexOfPosition[i] = newDocuments.size();
            newDocuments.add(createDocument(request));
            if (request.externalId() != null) {
                byExternalId.put(request.externalId(), i);
            }
            if (request.url() != null) {
                byUrl.put(request.url(), i);
            }
        }
        return new BatchPlan(newDocuments, savedIndexOfPosition, representative);
    }

    /**
     * 요청 순서대로 결과를 조립한다.
     * 요청 안 중복으로 접힌 자리는 대표와 같은 문서를 가리키되 {@code isNew} 가 false 다 —
     * 그래야 신규 건수가 실제 저장 건수와 맞는다.
     */
    private List<SaveResult> toResults(List<EmergingTechDocument> duplicates,
                                       BatchPlan plan,
                                       List<EmergingTechDocument> saved) {
        List<SaveResult> results = new ArrayList<>(duplicates.size());
        for (int i = 0; i < duplicates.size(); i++) {
            EmergingTechDocument duplicate = duplicates.get(i);
            if (duplicate != null) {
                results.add(new SaveResult(duplicate, false));
            } else {
                results.add(new SaveResult(saved.get(plan.savedIndexOfPosition()[i]),
                                           plan.representative()[i]));
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
