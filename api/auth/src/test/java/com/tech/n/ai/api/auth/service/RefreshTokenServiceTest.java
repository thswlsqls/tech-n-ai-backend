package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.domain.aurora.entity.auth.RefreshTokenEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.repository.reader.auth.RefreshTokenReaderRepository;
import com.tech.n.ai.domain.aurora.repository.reader.auth.UserReaderRepository;
import com.tech.n.ai.domain.aurora.repository.writer.auth.RefreshTokenWriterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService 단위 테스트")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenWriterRepository refreshTokenWriterRepository;

    @Mock
    private RefreshTokenReaderRepository refreshTokenReaderRepository;

    @Mock
    private UserReaderRepository userReaderRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Nested
    @DisplayName("saveRefreshToken")
    class SaveRefreshToken {

        @Test
        @DisplayName("정상 저장")
        void saveRefreshToken_성공() {
            // Given
            UserEntity user = new UserEntity();
            user.setId(1L);
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
            when(userReaderRepository.findById(1L)).thenReturn(Optional.of(user));
            when(refreshTokenWriterRepository.save(any(RefreshTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            RefreshTokenEntity result = refreshTokenService.saveRefreshToken(1L, "token-value", expiresAt);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getToken()).isEqualTo("token-value");
            assertThat(result.getUser()).isEqualTo(user);
            verify(refreshTokenWriterRepository).save(any(RefreshTokenEntity.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 - IllegalArgumentException")
        void saveRefreshToken_사용자_미존재() {
            when(userReaderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                refreshTokenService.saveRefreshToken(999L, "token", LocalDateTime.now().plusDays(7))
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("findRefreshToken")
    class FindRefreshToken {

        @Test
        @DisplayName("존재하는 토큰 조회")
        void findRefreshToken_존재() {
            RefreshTokenEntity entity = new RefreshTokenEntity();
            entity.setToken("token-value");
            when(refreshTokenReaderRepository.findByToken("token-value")).thenReturn(Optional.of(entity));

            Optional<RefreshTokenEntity> result = refreshTokenService.findRefreshToken("token-value");

            assertThat(result).isPresent();
            assertThat(result.get().getToken()).isEqualTo("token-value");
        }

        @Test
        @DisplayName("존재하지 않는 토큰 - empty")
        void findRefreshToken_미존재() {
            when(refreshTokenReaderRepository.findByToken("none")).thenReturn(Optional.empty());

            assertThat(refreshTokenService.findRefreshToken("none")).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteRefreshToken")
    class DeleteRefreshToken {

        @Test
        @DisplayName("정상 삭제")
        void deleteRefreshToken_성공() {
            RefreshTokenEntity entity = new RefreshTokenEntity();

            refreshTokenService.deleteRefreshToken(entity);

            verify(refreshTokenWriterRepository).delete(entity);
        }
    }

    @Nested
    @DisplayName("validateRefreshToken")
    class ValidateRefreshToken {

        @Test
        @DisplayName("유효한 토큰 - true")
        void validateRefreshToken_유효() {
            RefreshTokenEntity entity = new RefreshTokenEntity();
            entity.setIsDeleted(false);
            entity.setExpiresAt(LocalDateTime.now().plusDays(7));
            when(refreshTokenReaderRepository.findByToken("valid")).thenReturn(Optional.of(entity));

            assertThat(refreshTokenService.validateRefreshToken("valid")).isTrue();
        }

        @Test
        @DisplayName("삭제된 토큰 - false")
        void validateRefreshToken_삭제됨() {
            RefreshTokenEntity entity = new RefreshTokenEntity();
            entity.setIsDeleted(true);
            entity.setExpiresAt(LocalDateTime.now().plusDays(7));
            when(refreshTokenReaderRepository.findByToken("deleted")).thenReturn(Optional.of(entity));

            assertThat(refreshTokenService.validateRefreshToken("deleted")).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰 - false")
        void validateRefreshToken_만료됨() {
            RefreshTokenEntity entity = new RefreshTokenEntity();
            entity.setIsDeleted(false);
            entity.setExpiresAt(LocalDateTime.now().minusDays(1));
            when(refreshTokenReaderRepository.findByToken("expired")).thenReturn(Optional.of(entity));

            assertThat(refreshTokenService.validateRefreshToken("expired")).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 토큰 - false")
        void validateRefreshToken_미존재() {
            when(refreshTokenReaderRepository.findByToken("none")).thenReturn(Optional.empty());

            assertThat(refreshTokenService.validateRefreshToken("none")).isFalse();
        }
    }
}
