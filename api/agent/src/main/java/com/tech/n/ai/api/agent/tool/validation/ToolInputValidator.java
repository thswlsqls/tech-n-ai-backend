package com.tech.n.ai.api.agent.tool.validation;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LangChain4j Tool 입력값 검증 유틸리티
 * 모든 검증 메서드는 검증 성공 시 null, 실패 시 에러 메시지 String을 반환
 */
public final class ToolInputValidator {

    private ToolInputValidator() {
        // 유틸리티 클래스
    }

    // 상수 정의
    private static final int MAX_STRING_LENGTH = 2000;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern GITHUB_OWNER_PATTERN = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$");
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private static final Set<String> VALID_PROVIDERS = Set.of("OPENAI", "ANTHROPIC", "GOOGLE", "META", "XAI");
    private static final Set<String> VALID_UPDATE_TYPES = Set.of("MODEL_RELEASE", "API_UPDATE", "SDK_RELEASE", "PRODUCT_LAUNCH", "PLATFORM_UPDATE", "BLOG_POST");
    private static final Set<String> VALID_SOURCE_TYPES = Set.of("GITHUB_RELEASE", "RSS", "WEB_SCRAPING");
    private static final Set<String> VALID_STATUSES = Set.of("DRAFT", "PENDING", "PUBLISHED", "REJECTED");
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("^[a-fA-F0-9]{24}$");

    /** SSRF 방어: 차단 대상 호스트명 */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0",
        "::1", "[::1]",
        "169.254.169.254",  // AWS/GCP metadata endpoint
        "metadata.google.internal"
    );

    /**
     * LLM이 자주 틀리는 GitHub org 이름 교정 맵
     * key: 잘못된 이름 (lowercase), value: 올바른 이름
     */
    private static final Map<String, String> GITHUB_OWNER_CORRECTIONS = Map.of(
        "anthropic", "anthropics",
        "meta", "meta-llama",
        "facebook", "facebookresearch",
        "xai", "xai-org"
    );

    /**
     * LLM이 다양한 케이스로 입력할 수 있으므로 대소문자 정규화 매핑 사용
     */
    private static final Map<String, String> GROUP_FIELD_MAP = Map.of(
        "provider", "provider",
        "source_type", "source_type",
        "update_type", "update_type"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /**
     * 필수 입력값 검증 (null/blank 체크)
     *
     * @param value 검증할 값
     * @param fieldName 필드명 (에러 메시지용)
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return String.format("Error: %s는 필수 입력값입니다.", fieldName);
        }
        if (value.length() > MAX_STRING_LENGTH) {
            return String.format("Error: %s는 최대 %d자까지 입력 가능합니다.", fieldName, MAX_STRING_LENGTH);
        }
        return null;
    }

    /**
     * URL 형식 검증
     *
     * @param url 검증할 URL
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateUrl(String url) {
        String requiredError = validateRequired(url, "URL");
        if (requiredError != null) {
            return requiredError;
        }

        if (url.length() > MAX_URL_LENGTH) {
            return String.format("Error: URL은 최대 %d자까지 입력 가능합니다.", MAX_URL_LENGTH);
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return "Error: URL은 http 또는 https 프로토콜만 지원합니다.";
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "Error: 유효하지 않은 URL 형식입니다. 호스트가 필요합니다.";
            }

            // SSRF 방어: 내부 네트워크 접근 차단
            String ssrfError = checkSsrf(host);
            if (ssrfError != null) {
                return ssrfError;
            }

            return null;
        } catch (Exception e) {
            return "Error: 유효하지 않은 URL 형식입니다: " + e.getMessage();
        }
    }

    /**
     * SSRF 방어: 내부 네트워크 및 메타데이터 엔드포인트 접근 차단
     *
     * @param host 검증할 호스트명
     * @return 에러 메시지 또는 null (검증 성공)
     */
    static String checkSsrf(String host) {
        String lowerHost = host.toLowerCase();

        // 차단 목록 확인
        if (BLOCKED_HOSTS.contains(lowerHost)) {
            return "Error: 내부 네트워크 주소로의 접근은 허용되지 않습니다.";
        }

        // IP 주소 기반 private 대역 확인
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                return "Error: 내부 네트워크 주소로의 접근은 허용되지 않습니다.";
            }
        } catch (Exception e) {
            // DNS 확인 실패 시 호스트명 패턴만으로 검증 (차단하지 않음)
        }

        return null;
    }

    /**
     * AI Provider 검증 (선택적)
     * 빈 값은 허용 (전체 검색 시 사용)
     *
     * @param provider 검증할 provider 값
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateProviderOptional(String provider) {
        // 빈 값은 허용 (전체 검색)
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return validateEnum(provider, "provider", VALID_PROVIDERS);
    }

    /**
     * AI Provider 검증 (필수)
     * createDraftPost 등 provider가 필수인 경우 사용
     *
     * @param provider 검증할 provider 값
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateProviderRequired(String provider) {
        String requiredError = validateRequired(provider, "provider");
        if (requiredError != null) {
            return requiredError;
        }
        return validateEnum(provider, "provider", VALID_PROVIDERS);
    }

    /**
     * Update Type 검증
     *
     * @param updateType 검증할 updateType 값
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateUpdateType(String updateType) {
        String requiredError = validateRequired(updateType, "updateType");
        if (requiredError != null) {
            return requiredError;
        }
        return validateEnum(updateType, "updateType", VALID_UPDATE_TYPES);
    }

    /**
     * GitHub 저장소 파라미터 검증
     *
     * @param owner 저장소 소유자
     * @param repo 저장소 이름
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateGitHubRepo(String owner, String repo) {
        String ownerError = validateRequired(owner, "owner");
        if (ownerError != null) {
            return ownerError;
        }

        String repoError = validateRequired(repo, "repo");
        if (repoError != null) {
            return repoError;
        }

        if (!GITHUB_OWNER_PATTERN.matcher(owner).matches()) {
            return "Error: GitHub owner 형식이 올바르지 않습니다. 영문자, 숫자, 하이픈만 사용 가능합니다: " + owner;
        }

        if (!GITHUB_REPO_PATTERN.matcher(repo).matches()) {
            return "Error: GitHub repo 형식이 올바르지 않습니다. 영문자, 숫자, 점, 밑줄, 하이픈만 사용 가능합니다: " + repo;
        }

        return null;
    }

    /**
     * GitHub owner 이름을 교정합니다.
     * LLM이 자주 틀리는 org 이름(예: anthropic → anthropics)을 올바른 이름으로 변환합니다.
     *
     * @param owner 입력된 owner
     * @return 교정된 owner (교정 불필요 시 원본 반환)
     */
    public static String correctGitHubOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return owner;
        }
        return GITHUB_OWNER_CORRECTIONS.getOrDefault(owner.toLowerCase(), owner);
    }

    /**
     * 집계 기준 필드 검증
     *
     * <p>LLM이 "Provider", "SOURCE_TYPE" 등 다양한 케이스로 입력할 수 있으므로
     * toLowerCase()로 정규화 후 매핑한다.
     *
     * @param groupBy 집계 기준 필드
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateGroupByField(String groupBy) {
        String requiredError = validateRequired(groupBy, "groupBy");
        if (requiredError != null) {
            return requiredError;
        }
        if (!GROUP_FIELD_MAP.containsKey(groupBy.toLowerCase())) {
            return String.format("Error: groupBy는 다음 값 중 하나여야 합니다: %s (입력값: %s)",
                    String.join(", ", GROUP_FIELD_MAP.keySet()), groupBy);
        }
        return null;
    }

    /**
     * groupBy 필드를 MongoDB 필드명으로 변환
     *
     * @param groupBy 입력값
     * @return MongoDB 필드명 또는 null (매핑 실패)
     */
    public static String resolveGroupByField(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return null;
        }
        return GROUP_FIELD_MAP.get(groupBy.toLowerCase());
    }

    /**
     * UpdateType 검증 (선택적, 빈 문자열 허용)
     */
    public static String validateUpdateTypeOptional(String updateType) {
        if (updateType == null || updateType.isBlank()) {
            return null;
        }
        return validateEnum(updateType, "updateType", VALID_UPDATE_TYPES);
    }

    /**
     * SourceType 검증 (선택적, 빈 문자열 허용)
     */
    public static String validateSourceTypeOptional(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return null;
        }
        return validateEnum(sourceType, "sourceType", VALID_SOURCE_TYPES);
    }

    /**
     * 날짜 형식 검증 (YYYY-MM-DD, 빈 문자열 허용)
     *
     * @param date 검증할 날짜 문자열
     * @param fieldName 필드명 (에러 메시지용)
     * @return 에러 메시지 또는 null (검증 성공)
     */
    public static String validateDateOptional(String date, String fieldName) {
        if (date == null || date.isBlank()) {
            return null;
        }
        if (!DATE_PATTERN.matcher(date).matches()) {
            return String.format("Error: %s는 YYYY-MM-DD 형식이어야 합니다 (입력값: %s)", fieldName, date);
        }
        try {
            java.time.LocalDate.parse(date);
            return null;
        } catch (java.time.format.DateTimeParseException e) {
            return String.format("Error: %s는 유효한 날짜가 아닙니다 (입력값: %s)", fieldName, date);
        }
    }

    /**
     * Status 검증 (선택적, 빈 문자열 허용)
     */
    public static String validateStatusOptional(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return validateEnum(status, "status", VALID_STATUSES);
    }

    /**
     * MongoDB ObjectId 검증
     */
    public static String validateObjectId(String id) {
        String requiredError = validateRequired(id, "id");
        if (requiredError != null) {
            return requiredError;
        }
        if (!OBJECT_ID_PATTERN.matcher(id).matches()) {
            return String.format("Error: id는 24자리 16진수(MongoDB ObjectId)여야 합니다 (입력값: %s)", id);
        }
        return null;
    }

    /**
     * 페이지 번호 정규화 (1 미만이면 1)
     */
    public static int normalizePage(int page) {
        return Math.max(1, page);
    }

    /**
     * 페이지 크기 정규화 (0 이하면 20, 100 초과면 100)
     */
    public static int normalizeSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, 100);
    }

    private static final Set<String> VALID_RSS_PROVIDERS = Set.of("OPENAI", "GOOGLE");
    private static final Set<String> VALID_SCRAPER_PROVIDERS = Set.of("ANTHROPIC", "META");

    /**
     * RSS 수집 대상 Provider 검증 (선택적, 빈 문자열 허용)
     */
    public static String validateRssProviderOptional(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return validateEnum(provider, "provider", VALID_RSS_PROVIDERS);
    }

    /**
     * 스크래핑 수집 대상 Provider 검증 (선택적, 빈 문자열 허용)
     */
    public static String validateScraperProviderOptional(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return validateEnum(provider, "provider", VALID_SCRAPER_PROVIDERS);
    }

    /**
     * Enum 값 검증 (내부 헬퍼)
     */
    private static String validateEnum(String value, String fieldName, Set<String> validValues) {
        String upperValue = value.toUpperCase();
        if (!validValues.contains(upperValue)) {
            return String.format("Error: %s는 다음 값 중 하나여야 합니다: %s (입력값: %s)",
                    fieldName, String.join(", ", validValues), value);
        }
        return null;
    }
}
