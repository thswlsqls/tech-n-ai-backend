package com.tech.n.ai.api.chatbot.service;

import com.tech.n.ai.domain.mongodb.enums.GraphNodeType;
import com.tech.n.ai.domain.mongodb.key.GraphKeys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 질문에서 그래프 노드 키 후보를 뽑는다.
 *
 * LLM을 쓰지 않는다. 규칙만으로 만들기 때문에 같은 질문이면 항상 같은 후보가 나온다.
 * 정규화는 그래프를 만들 때 쓴 GraphKeys.normalizeName을 그대로 부른다. 여기서 다른 방식으로
 * 다듬으면 배치가 저장한 키와 어긋나서 아무것도 못 찾는다.
 */
public final class GraphSeedExtractor {

    /** 후보 키 상한. 넘으면 짧은 n-gram부터 버린다. */
    private static final int MAX_CANDIDATE_KEYS = 300;

    /** 부분 일치 리터럴 상한 */
    private static final int MAX_NAME_LITERALS = 5;

    /** 몇 단어까지 붙여서 하나의 이름으로 볼지 */
    private static final int MAX_NGRAM_SIZE = 4;

    /** 이보다 짧은 토큰은 부분 일치 리터럴로 쓰지 않는다 */
    private static final int MIN_LITERAL_LENGTH = 4;

    private static final char HANGUL_SYLLABLE_FIRST = '가';
    private static final char HANGUL_SYLLABLE_LAST = '힣';
    private static final String TRAILING_PUNCTUATION = ".,?!";

    private GraphSeedExtractor() {
    }

    /**
     * @param candidateKeys 노드 키 후보. 그대로 $in 배열에 들어간다.
     * @param nameLiterals 키 안에 들어 있으면 맞는 것으로 볼 부분 문자열
     */
    public record Seeds(List<String> candidateKeys, List<String> nameLiterals) {}

    public static Seeds extract(String question) {
        Optional<String> normalized = GraphKeys.normalizeName(question);
        if (normalized.isEmpty()) {
            return new Seeds(List.of(), List.of());
        }

        List<String> tokens = tokenize(normalized.get());
        if (tokens.isEmpty()) {
            return new Seeds(List.of(), List.of());
        }

        return new Seeds(buildCandidateKeys(tokens), pickNameLiterals(tokens));
    }

    /**
     * 공백으로 자른 뒤 토큰마다 끝에 붙은 문장부호와 한글 조사를 떼어낸다.
     * 조사는 영문·숫자가 섞인 토큰에서만 뗀다. "모델과"처럼 한글뿐인 단어는 그대로 둔다.
     */
    private static List<String> tokenize(String normalizedQuestion) {
        List<String> tokens = new ArrayList<>();
        for (String raw : normalizedQuestion.split(" ")) {
            String token = stripTrailingPunctuation(raw);
            if (containsAsciiAlphanumeric(token)) {
                token = stripTrailingHangul(token);
            }
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String stripTrailingPunctuation(String token) {
        int end = token.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(token.charAt(end - 1)) >= 0) {
            end--;
        }
        return token.substring(0, end);
    }

    private static String stripTrailingHangul(String token) {
        int end = token.length();
        while (end > 0 && isHangulSyllable(token.charAt(end - 1))) {
            end--;
        }
        return token.substring(0, end);
    }

    private static boolean isHangulSyllable(char c) {
        return c >= HANGUL_SYLLABLE_FIRST && c <= HANGUL_SYLLABLE_LAST;
    }

    private static boolean containsAsciiAlphanumeric(String token) {
        for (char c : token.toCharArray()) {
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return true;
            }
        }
        return false;
    }

    /**
     * 붙어 있는 단어 1~4개를 하나의 이름으로 보고 노드 타입 5종에 각각 키를 만든다.
     * 긴 조합부터 담아서, 상한에 걸리면 뒤에 오는 짧은 조합이 잘린다.
     */
    private static List<String> buildCandidateKeys(List<String> tokens) {
        Set<String> keys = new LinkedHashSet<>();
        for (int size = MAX_NGRAM_SIZE; size >= 1; size--) {
            for (int start = 0; start + size <= tokens.size(); start++) {
                String ngram = String.join(" ", tokens.subList(start, start + size));
                for (GraphNodeType type : GraphNodeType.values()) {
                    GraphKeys.nodeKey(type, ngram).ifPresent(keys::add);
                    if (keys.size() >= MAX_CANDIDATE_KEYS) {
                        return List.copyOf(keys);
                    }
                }
            }
        }
        return List.copyOf(keys);
    }

    /**
     * 버전 번호처럼 숫자가 섞인 토큰만 부분 일치용으로 남긴다.
     * "v2.26.0"은 노드 이름 전체가 아니라 이름 안에 들어 있는 경우가 많아 키 일치로는 못 잡는다.
     */
    private static List<String> pickNameLiterals(List<String> tokens) {
        Set<String> literals = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.length() < MIN_LITERAL_LENGTH || !containsDigit(token)) {
                continue;
            }
            literals.add(token);
            if (literals.size() >= MAX_NAME_LITERALS) {
                break;
            }
        }
        return List.copyOf(literals);
    }

    private static boolean containsDigit(String token) {
        for (char c : token.toCharArray()) {
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }
}
