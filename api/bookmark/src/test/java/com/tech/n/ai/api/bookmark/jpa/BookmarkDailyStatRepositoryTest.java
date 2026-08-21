package com.tech.n.ai.api.bookmark.jpa;

import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkDailyStatEntity;
import com.tech.n.ai.domain.aurora.entity.bookmark.BookmarkViewEventEntity;
import com.tech.n.ai.domain.aurora.repository.reader.bookmark.BookmarkDailyStatReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkDailyStatWriterJpaRepository;
import com.tech.n.ai.domain.aurora.repository.writer.bookmark.BookmarkViewEventWriterJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 집계 리포지토리를 실제 DB(H2) 로 돌리는 테스트.
 *
 * 이 두 결함은 JPQL 과 영속성 컨텍스트에서 나기 때문에,
 * 리포지토리를 목으로 대체하는 서비스 단위 테스트로는 잡히지 않는다.
 */
@DataJpaTest(properties = {
    "spring.config.name=bookmark-jpa-test",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ContextConfiguration(classes = BookmarkDailyStatRepositoryTest.TestConfig.class)
@DisplayName("북마크 집계 리포지토리 DB 테스트")
class BookmarkDailyStatRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final LocalDate STAT_DATE = LocalDate.of(2026, 8, 20);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookmarkDailyStatReaderRepository readerRepository;

    @Autowired
    private BookmarkDailyStatWriterJpaRepository statWriterRepository;

    @Autowired
    private BookmarkViewEventWriterJpaRepository viewEventWriterRepository;

    @Nested
    @DisplayName("findRange")
    class FindRange {

        @Test
        @DisplayName("provider 를 안 주면 그 구간의 모든 제공자를 돌려준다")
        void findRange_provider미지정() {
            persistStat(USER_ID, STAT_DATE, "github", 3L);
            persistStat(USER_ID, STAT_DATE, "rss", 2L);
            flushAndClear();

            List<BookmarkDailyStatEntity> stats =
                readerRepository.findRange(USER_ID, STAT_DATE, STAT_DATE, null);

            assertThat(stats).hasSize(2);
            assertThat(stats).extracting(BookmarkDailyStatEntity::getProvider)
                .containsExactlyInAnyOrder("github", "rss");
        }

        @Test
        @DisplayName("provider 를 주면 그 제공자만 돌려준다")
        void findRange_provider지정() {
            persistStat(USER_ID, STAT_DATE, "github", 3L);
            persistStat(USER_ID, STAT_DATE, "rss", 2L);
            flushAndClear();

            List<BookmarkDailyStatEntity> stats =
                readerRepository.findRange(USER_ID, STAT_DATE, STAT_DATE, "github");

            assertThat(stats).hasSize(1);
            assertThat(stats.get(0).getProvider()).isEqualTo("github");
        }

        @Test
        @DisplayName("provider 미지정이어도 남의 집계는 섞이지 않는다")
        void findRange_provider미지정_사용자경계() {
            persistStat(USER_ID, STAT_DATE, "github", 3L);
            persistStat(OTHER_USER_ID, STAT_DATE, "github", 7L);
            flushAndClear();

            List<BookmarkDailyStatEntity> stats =
                readerRepository.findRange(USER_ID, STAT_DATE, STAT_DATE, null);

            assertThat(stats).hasSize(1);
            assertThat(stats.get(0).getViewCount()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("increaseViewCount")
    class IncreaseViewCount {

        @Test
        @DisplayName("아직 flush 되지 않은 조회 이벤트 INSERT 를 버리지 않는다")
        void increaseViewCount_대기중인_INSERT를_지키다() {
            persistStat(USER_ID, STAT_DATE, "github", 0L);
            flushAndClear();

            // 서비스가 하는 순서 그대로다. save() 는 persist 만 하고 INSERT 를 미룬다.
            viewEventWriterRepository.save(BookmarkViewEventEntity.of(
                100L, USER_ID, "github", LocalDateTime.of(2026, 8, 20, 10, 0), "web"));

            int updated = statWriterRepository.increaseViewCount(USER_ID, STAT_DATE, "github");

            assertThat(updated).isEqualTo(1);
            assertThat(viewEventWriterRepository.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("조회 수를 1 올린다")
        void increaseViewCount_증가() {
            persistStat(USER_ID, STAT_DATE, "github", 5L);
            flushAndClear();

            int updated = statWriterRepository.increaseViewCount(USER_ID, STAT_DATE, "github");

            assertThat(updated).isEqualTo(1);
            assertThat(readerRepository
                .findByUserIdAndStatDateAndProviderAndIsDeletedFalse(USER_ID, STAT_DATE, "github")
                .orElseThrow()
                .getViewCount()).isEqualTo(6L);
        }
    }

    private void persistStat(Long userId, LocalDate statDate, String provider, Long viewCount) {
        BookmarkDailyStatEntity stat = BookmarkDailyStatEntity.of(userId, statDate, provider);
        stat.setViewCount(viewCount);
        entityManager.persist(stat);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.tech.n.ai.domain.aurora.entity")
    @EnableJpaRepositories("com.tech.n.ai.domain.aurora.repository")
    static class TestConfig {
    }
}
