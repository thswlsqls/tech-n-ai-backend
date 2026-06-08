package com.tech.n.ai.batch.source.domain.emergingtech;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * RSS·스크래퍼 Processor가 공유하는 변환 헬퍼
 */
public final class EmergingTechProcessorUtils {

    private EmergingTechProcessorUtils() {
    }

    public static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public static List<String> extractTags(String category) {
        if (category == null || category.isBlank()) return List.of();
        return List.of(category.split(",")).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
