# RSS Client 모듈

## 개요

`client-rss`는 외부 기술 블로그의 RSS/Atom 피드를 가져와 공통 형식(`RssFeedItem`)으로 파싱하는 라이브러리입니다. Rome로 피드를 해석하고 WebClient로 내려받습니다. `bootJar.enabled = false`인 라이브러리라 `batch-source`가 의존성으로 가져다 씁니다.

## 주요 기능

파서 하나가 피드 하나를 담당합니다.

```java
public interface RssParser {
    List<RssFeedItem> parse();   // 피드를 가져와 아이템 리스트로 변환
    String getSourceName();      // 표시용 소스 이름
    String getFeedUrl();
}
```

지원 피드(구체 파서 6종): TechCrunch, Google Developers Blog(Atom 1.0), Ars Technica, Google AI Blog, Medium Technology, OpenAI Blog. 실제 피드 URL은 `application-rss.yml`의 `rss.sources.*.feed-url`에서 관리합니다.

## 패키지 구조

```
com.tech.n.ai.client.rss
├── config/   RssParserConfig(rssWebClientBuilder 빈), RssProperties
├── dto/      RssFeedItem (record)
├── parser/   RssParser → AbstractRssParser → 구체 파서 6종
├── util/     RssDataCleaner(HTML 정제·요약), RssFeedValidator(검증·중복 제거)
└── exception/ RssParsingException (BaseException 상속)
```

## 설계 패턴

### Template Method — `AbstractRssParser`
상위 클래스가 `parse()` 흐름을 고정하고 하위 클래스는 달라지는 부분만 채웁니다.

1. 소스 키로 설정 조회(없으면 `RssParsingException`) → 2. WebClient 구성 → 3. Resilience4j 재시도(`rssRetry`)로 감싸 피드 fetch → 4. `prepareContent()`로 원문 정리 → 5. Rome `SyndFeedInput` 파싱 → 6. `RssFeedItem` 변환

하위 클래스가 반드시 구현하는 것은 `getSourceKey()` 하나이고, 필요 시 `prepareContent()`·`extractCategory()`·`extractImageUrl()`을 오버라이드합니다. (예: TechCrunch는 XML 선언 앞 잡문자 제거, Google AI Blog는 이미지 URL 추출)

### WebClient 공통 빌더
`RssParserConfig`가 `rssWebClientBuilder` 하나를 등록하고 구체 파서들이 소스별 baseUrl만 바꿔 씁니다. 공통 설정: 리다이렉트 추적, gzip 압축, 타임아웃(`rss.timeout-seconds`), 브라우저형 User-Agent, 응답 버퍼 10MB.

## 출력 데이터 (`RssFeedItem`)

`title`, `link`, `description`, `publishedDate`(없으면 `updatedDate`로 대체), `author`, `category`, `guid`(`uri` 우선, 없으면 `link`), `imageUrl`(Google AI Blog만 실제 값).

## 유틸리티

- **`RssDataCleaner`**: `cleanHtml`(태그 제거·엔티티 디코딩·공백 정규화), `createSummary`(단어 단위로 잘라 요약, 기본 200자)
- **`RssFeedValidator`**: `validate`(feed null이면 예외), `validateAndRemoveDuplicates`(GUID/link 기준 중복 제거)

> 두 유틸은 빈으로 등록만 되고 `parse()` 안에서 자동 호출되지는 않습니다. 결과를 받는 쪽(`batch-source`)이 필요에 따라 주입해 씁니다.

## 기술 스택

- **rometools rome 1.19.0**: RSS 2.0 / Atom 1.0 파싱
- **spring-boot-starter-webflux** + **reactor-netty-http**(classic 모드에서 명시 선언): WebClient
- **resilience4j 2.1.0**: 재시도
- **공통 모듈**: `common-core`, `common-exception`

## 설정

```yaml
rss:
  timeout-seconds: 30
  sources:
    techcrunch:
      feed-url: https://techcrunch.com/feed/
      feed-format: RSS_2.0
    # ... google-developers-blog, ars-technica, medium-technology, openai-blog, google-ai-blog

resilience4j:
  retry:
    instances:
      rssRetry:
        base-config: default   # max-attempts 3, 지수 백오프
```

> 실제 재시도는 `resilience4j.retry.instances.rssRetry`가 제어합니다. `RssProperties`의 `max-retries`·`retry-delay-ms` 필드는 현재 재시도 로직에 연결돼 있지 않습니다.

## 사용 예시

```java
@Service
@RequiredArgsConstructor
public class FeedCollector {
    private final List<RssParser> parsers;  // 구체 파서 6종이 모두 주입됨

    public List<RssFeedItem> collectAll() {
        return parsers.stream().flatMap(p -> p.parse().stream()).toList();
    }
}
```

## 참고 문서

- [Rome](https://rometools.github.io/rome/) · [Resilience4j](https://resilience4j.readme.io/)
- [Spring WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [RSS 2.0 명세](https://www.rssboard.org/rss-specification) · [Atom 1.0 (RFC 4287)](https://datatracker.ietf.org/doc/html/rfc4287)
