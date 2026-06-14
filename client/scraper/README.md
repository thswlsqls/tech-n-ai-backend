# Scraper Client 모듈

## 개요

`client-scraper`는 공식 API가 없는 사이트를 웹 스크래핑으로 긁어 두 종류의 데이터를 모으는 라이브러리입니다. AI 기업 기술 블로그 기사(`ScrapedTechArticle`)와 개발 대회·해커톤 정보(`ScrapedContestItem`)입니다. 정적 HTML은 Jsoup으로, JS 렌더링·Cloudflare 페이지는 Selenium으로 처리합니다. `bootJar.enabled = false`인 라이브러리라 `batch-source`가 의존성으로 가져다 씁니다.

## 주요 기능

대상 데이터가 달라 인터페이스를 둘로 나눴습니다(상속 관계 없음).

```java
public interface WebScraper {        // 대회·해커톤
    List<ScrapedContestItem> scrape();
    String getSourceName();  String getBaseUrl();
}
public interface TechBlogScraper {   // 기술 블로그 기사
    List<ScrapedTechArticle> scrapeArticles();
    String getProviderName();  String getBaseUrl();
}
```

지원 사이트:

| 스크래퍼 | 종류 | 대상 | 방식 |
|---|---|---|---|
| AnthropicNews | TechBlog | anthropic.com/news | Jsoup (Next.js RSC 파싱) |
| MetaAiBlog | TechBlog | ai.meta.com/blog | Selenium 우선, 실패 시 Jsoup |
| XaiNews | TechBlog | x.ai/news | Selenium 전용(Cloudflare) |
| LeetCode / AtCoder / Devpost / MLH / GoogleSummerOfCode | Web | 각 대회·해커톤 사이트 | Jsoup |

모든 스크래퍼는 긁기 전 `RobotsTxtChecker`로 robots.txt 허용 여부를 확인하고, 막혀 있으면 `ScrapingException`으로 중단합니다. HTTP fetch 구간은 Resilience4j 재시도(`scraperRetry`)로 감쌉니다.

## 패키지 구조

```
com.tech.n.ai.client.scraper
├── config/   ScraperConfig(WebClient.Builder), ScraperProperties, SeleniumConfig(조건부)
├── dto/      ScrapedTechArticle, ScrapedContestItem (record)
├── scraper/  WebScraper, TechBlogScraper + 구체 스크래퍼 8종
├── util/     RobotsTxtChecker, ScraperDomUtils, StructuredDataDateExtractor,
│             DateParsingUtils, ScrapedDataCleaner, ScrapedDataValidator
└── exception/ ScrapingException (BaseException 상속)
```

## 설계 포인트

- **Jsoup / Selenium 선택**: 대부분은 WebClient로 HTML을 받아 Jsoup으로 파싱하고, SPA·Cloudflare 페이지만 Selenium을 씁니다. `MetaAiBlog`는 Selenium이 켜져 있으면 Selenium, 아니면 WebClient. `XaiNews`는 Selenium이 없으면 `ScrapingException`. (공통 추상 클래스는 없고, 흐름이 각 스크래퍼에 직접 들어 있습니다.)
- **날짜 추출 다단계 fallback (`StructuredDataDateExtractor`)**: 목록에 발행일이 없으면 상세 페이지를 따로 받아 OG 메타태그 → JSON-LD `datePublished` → `<time datetime>` → 페이지 스크립트 → 본문 텍스트 순으로 찾고, 모두 실패하면 null.

## 출력 데이터

- **`ScrapedTechArticle`**: `title`, `url`, `summary`, `publishedDate`, `author`, `category`, `providerName`
- **`ScrapedContestItem`**: `title`, `url`, `description`, `startDate`, `endDate`, `organizer`, `location`, `category`, `prize`, `imageUrl`

## 유틸리티

| 클래스 | 역할 |
|---|---|
| `RobotsTxtChecker` | crawler-commons로 robots.txt 파싱, `ALLOWED`/`BLOCKED_BY_ROBOTS`/`FETCH_FAILED` 반환 |
| `ScraperDomUtils` | null-safe `extractText()` |
| `DateParsingUtils` | ISO 8601, `MMM d, yyyy` 등 여러 포맷 순차 시도 |
| `ScrapedDataCleaner` | HTML 태그 제거·엔티티 디코딩·공백 정규화 |
| `ScrapedDataValidator` | `ScrapedContestItem`의 title·url 누락과 URL 중복 제거 |

## 기술 스택

- **Jsoup 1.17.2** / **crawler-commons 1.2**(robots.txt) / **Selenium 4.18.1 + webdrivermanager 5.7.0**
- **spring-boot-starter-webflux**(WebClient), **resilience4j 2.1.0**(재시도)
- **공통 모듈**: `common-core`(`BaseException`), `common-exception`

## 설정

```yaml
scraper:
  user-agent: "ShrimpTM-Demo/1.0 (+https://github.com/your-repo)"
  selenium:
    enabled: false        # 기본 비활성. true일 때만 ChromeDriver 빈 등록
    headless: true
  sources:
    xai-news:
      base-url: https://x.ai
      requires-selenium: true
    # ... anthropic-news, meta-ai-blog, leetcode, atcoder, devpost, mlh, google-summer-of-code

resilience4j:
  retry:
    instances:
      scraperRetry:
        base-config: default   # max-attempts 3
```

## 현재 구현 범위

코드 그대로의 동작 기준입니다.

- **Selenium 기본 꺼짐**: `enabled=false`라 ChromeDriver 빈이 등록되지 않고, 이 상태에서 `XaiNews`는 `ScrapingException`을 던집니다.
- **대회 날짜 미수집**: `AtCoder`·`Devpost`·`MLH`·`GoogleSummerOfCode`는 날짜 파싱이 아직 없어 `startDate`/`endDate`가 null입니다.
- **LeetCode GraphQL 미구현**: TODO 상태라 항상 HTML 파싱으로 넘어갑니다.
- **`min-interval-seconds`는 선언만**: 코드에서 읽어 적용하지 않습니다. 간격 제어는 호출하는 쪽이 맡습니다.
- **`ScrapingException`은 재시도 대상 아님**: robots 차단·파싱 실패는 재시도 없이 전파됩니다.

## 참고 문서

- [jsoup](https://jsoup.org/) · [crawler-commons](https://github.com/crawler-commons/crawler-commons) · [Selenium](https://www.selenium.dev/documentation/)
- [Robots Exclusion Protocol (RFC 9309)](https://datatracker.ietf.org/doc/html/rfc9309) · [Resilience4j](https://resilience4j.readme.io/)
