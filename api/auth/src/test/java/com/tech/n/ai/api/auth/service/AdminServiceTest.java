package com.tech.n.ai.api.auth.service;

import com.tech.n.ai.api.auth.dto.LoginRequest;
import com.tech.n.ai.api.auth.dto.RefreshTokenRequest;
import com.tech.n.ai.api.auth.dto.TokenResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminCreateRequest;
import com.tech.n.ai.api.auth.dto.admin.AdminResponse;
import com.tech.n.ai.api.auth.dto.admin.AdminUpdateRequest;
import com.tech.n.ai.common.exception.exception.ConflictException;
import com.tech.n.ai.common.exception.exception.ForbiddenException;
import com.tech.n.ai.common.exception.exception.ResourceNotFoundException;
import com.tech.n.ai.common.exception.exception.UnauthorizedException;
import com.tech.n.ai.common.security.jwt.JwtTokenPayload;
import com.tech.n.ai.domain.mariadb.entity.auth.AdminEntity;
import com.tech.n.ai.domain.mariadb.entity.auth.RefreshTokenEntity;
import com.tech.n.ai.domain.mariadb.repository.reader.auth.AdminReaderRepository;
import com.tech.n.ai.domain.mariadb.repository.writer.auth.AdminWriterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService 단위 테스트")
class AdminServiceTest {

    @Mock
    private AdminReaderRepository adminReaderRepository;

    @Mock
    private AdminWriterRepository adminWriterRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminService adminService;

    // ========== createAdmin 테스트 ==========

    @Nested
    @DisplayName("createAdmin")
    class CreateAdmin {

        @Test
        @DisplayName("정상 관리자 생성 - AdminResponse 반환")
        void createAdmin_성공() {
            // Given
            AdminCreateRequest request = new AdminCreateRequest("admin@example.com", "adminuser", "Password1!");
            when(adminReaderRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
            when(adminReaderRepository.findByUsername("adminuser")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Password1!")).thenReturn("$2a$10$encoded");
            when(adminWriterRepository.save(any(AdminEntity.class))).thenAnswer(invocation -> {
                AdminEntity saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            AdminResponse response = adminService.createAdmin(request, 100L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.email()).isEqualTo("admin@example.com");
            assertThat(response.username()).isEqualTo("adminuser");
            assertThat(response.role()).isEqualTo("ADMIN");
            assertThat(response.isActive()).isTrue();
            verify(adminWriterRepository).save(any(AdminEntity.class));
        }

        @Test
        @DisplayName("이메일 중복 시 ConflictException")
        void createAdmin_이메일_중복() {
            // Given
            AdminCreateRequest request = new AdminCreateRequest("dup@example.com", "adminuser", "Password1!");
            when(adminReaderRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(new AdminEntity()));

            // When & Then
            assertThatThrownBy(() -> adminService.createAdmin(request, 100L))
                .isInstanceOf(ConflictException.class);
            verify(adminWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("사용자명 중복 시 ConflictException")
        void createAdmin_사용자명_중복() {
            // Given
            AdminCreateRequest request = new AdminCreateRequest("admin@example.com", "dupuser", "Password1!");
            when(adminReaderRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
            when(adminReaderRepository.findByUsername("dupuser")).thenReturn(Optional.of(new AdminEntity()));

            // When & Then
            assertThatThrownBy(() -> adminService.createAdmin(request, 100L))
                .isInstanceOf(ConflictException.class);
            verify(adminWriterRepository, never()).save(any());
        }
    }

    // ========== listAdmins 테스트 ==========

    @Nested
    @DisplayName("listAdmins")
    class ListAdmins {

        @Test
        @DisplayName("활성 관리자 목록 반환")
        void listAdmins_성공() {
            // Given
            AdminEntity admin1 = createAdminEntity(1L, "a1@example.com", "admin1");
            AdminEntity admin2 = createAdminEntity(2L, "a2@example.com", "admin2");
            when(adminReaderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(admin1, admin2));

            // When
            List<AdminResponse> result = adminService.listAdmins();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).email()).isEqualTo("a1@example.com");
            assertThat(result.get(1).email()).isEqualTo("a2@example.com");
        }

        @Test
        @DisplayName("활성 관리자가 없으면 빈 리스트 반환")
        void listAdmins_빈_목록() {
            // Given
            when(adminReaderRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of());

            // When
            List<AdminResponse> result = adminService.listAdmins();

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========== getAdmin 테스트 ==========

    @Nested
    @DisplayName("getAdmin")
    class GetAdmin {

        @Test
        @DisplayName("활성 관리자 조회 성공")
        void getAdmin_성공() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));

            // When
            AdminResponse response = adminService.getAdmin(1L);

            // Then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("admin@example.com");
        }

        @Test
        @DisplayName("존재하지 않는 관리자 조회 시 ResourceNotFoundException")
        void getAdmin_미존재() {
            // Given
            when(adminReaderRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminService.getAdmin(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("삭제된 관리자 조회 시 ResourceNotFoundException")
        void getAdmin_삭제된_관리자() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setIsDeleted(true);
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));

            // When & Then
            assertThatThrownBy(() -> adminService.getAdmin(1L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ========== updateAdmin 테스트 ==========

    @Nested
    @DisplayName("updateAdmin")
    class UpdateAdmin {

        @Test
        @DisplayName("사용자명과 비밀번호 모두 변경")
        void updateAdmin_전체_변경() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "oldname");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(adminReaderRepository.findByUsername("newname")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("NewPassword1!")).thenReturn("$2a$10$newEncoded");
            when(adminWriterRepository.save(any(AdminEntity.class))).thenReturn(admin);

            AdminUpdateRequest request = new AdminUpdateRequest("newname", "NewPassword1!");

            // When
            AdminResponse response = adminService.updateAdmin(1L, request, 100L);

            // Then
            assertThat(response.username()).isEqualTo("newname");
            verify(passwordEncoder).encode("NewPassword1!");
            verify(adminWriterRepository).save(admin);
        }

        @Test
        @DisplayName("사용자명만 변경")
        void updateAdmin_사용자명만_변경() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "oldname");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(adminReaderRepository.findByUsername("newname")).thenReturn(Optional.empty());
            when(adminWriterRepository.save(any(AdminEntity.class))).thenReturn(admin);

            AdminUpdateRequest request = new AdminUpdateRequest("newname", null);

            // When
            adminService.updateAdmin(1L, request, 100L);

            // Then
            verify(passwordEncoder, never()).encode(any());
            verify(adminWriterRepository).save(admin);
        }

        @Test
        @DisplayName("비밀번호만 변경")
        void updateAdmin_비밀번호만_변경() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(passwordEncoder.encode("NewPassword1!")).thenReturn("$2a$10$newEncoded");
            when(adminWriterRepository.save(any(AdminEntity.class))).thenReturn(admin);

            AdminUpdateRequest request = new AdminUpdateRequest(null, "NewPassword1!");

            // When
            adminService.updateAdmin(1L, request, 100L);

            // Then
            verify(passwordEncoder).encode("NewPassword1!");
            verify(adminReaderRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("동일한 사용자명으로 변경 시 중복 체크 안 함")
        void updateAdmin_동일_사용자명() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "samename");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(adminWriterRepository.save(any(AdminEntity.class))).thenReturn(admin);

            AdminUpdateRequest request = new AdminUpdateRequest("samename", null);

            // When
            adminService.updateAdmin(1L, request, 100L);

            // Then
            verify(adminReaderRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("사용자명 중복 시 ConflictException")
        void updateAdmin_사용자명_중복() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "oldname");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(adminReaderRepository.findByUsername("dupname")).thenReturn(Optional.of(new AdminEntity()));

            AdminUpdateRequest request = new AdminUpdateRequest("dupname", null);

            // When & Then
            assertThatThrownBy(() -> adminService.updateAdmin(1L, request, 100L))
                .isInstanceOf(ConflictException.class);
            verify(adminWriterRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 관리자 수정 시 ResourceNotFoundException")
        void updateAdmin_미존재() {
            // Given
            when(adminReaderRepository.findById(999L)).thenReturn(Optional.empty());
            AdminUpdateRequest request = new AdminUpdateRequest("newname", null);

            // When & Then
            assertThatThrownBy(() -> adminService.updateAdmin(999L, request, 100L))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("빈 문자열 사용자명은 변경하지 않음")
        void updateAdmin_빈_사용자명() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(adminWriterRepository.save(any(AdminEntity.class))).thenReturn(admin);

            AdminUpdateRequest request = new AdminUpdateRequest("  ", null);

            // When
            AdminResponse response = adminService.updateAdmin(1L, request, 100L);

            // Then
            assertThat(response.username()).isEqualTo("admin1");
            verify(adminReaderRepository, never()).findByUsername(any());
        }
    }

    // ========== deleteAdmin 테스트 ==========

    @Nested
    @DisplayName("deleteAdmin")
    class DeleteAdmin {

        @Test
        @DisplayName("정상 관리자 삭제 - Refresh Token도 함께 삭제")
        void deleteAdmin_성공() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));

            // When
            adminService.deleteAdmin(1L, 2L);

            // Then
            verify(refreshTokenService).deleteAllAdminRefreshTokens(1L);
            verify(adminWriterRepository).delete(admin);
            assertThat(admin.getIsActive()).isFalse();
            assertThat(admin.getDeletedBy()).isEqualTo(2L);
        }

        @Test
        @DisplayName("자기 자신 삭제 시 ForbiddenException")
        void deleteAdmin_자기_삭제() {
            // When & Then
            assertThatThrownBy(() -> adminService.deleteAdmin(1L, 1L))
                .isInstanceOf(ForbiddenException.class);
            verify(adminWriterRepository, never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 관리자 삭제 시 ResourceNotFoundException")
        void deleteAdmin_미존재() {
            // Given
            when(adminReaderRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminService.deleteAdmin(999L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("이미 삭제된 관리자 삭제 시 ResourceNotFoundException")
        void deleteAdmin_이미_삭제됨() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setIsDeleted(true);
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(admin));

            // When & Then
            assertThatThrownBy(() -> adminService.deleteAdmin(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ========== login 테스트 ==========

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상 로그인 - TokenResponse 반환 및 실패 카운터 초기화")
        void login_성공() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setPassword("$2a$10$encodedPassword");
            admin.setFailedLoginAttempts(3);
            when(adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse("admin@example.com"))
                .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("Password1!", "$2a$10$encodedPassword")).thenReturn(true);

            TokenResponse expected = new TokenResponse("access", "refresh", "Bearer", 900L, 86400L);
            when(tokenService.generateTokens(1L, "admin@example.com", "ADMIN")).thenReturn(expected);

            LoginRequest request = new LoginRequest("admin@example.com", "Password1!");

            // When
            TokenResponse response = adminService.login(request);

            // Then
            assertThat(response).isEqualTo(expected);
            assertThat(admin.getFailedLoginAttempts()).isEqualTo(0);
            assertThat(admin.getAccountLockedUntil()).isNull();
            verify(adminWriterRepository).save(admin);
        }

        @Test
        @DisplayName("존재하지 않는 이메일 로그인 시 UnauthorizedException")
        void login_이메일_미존재() {
            // Given
            when(adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse("noexist@example.com"))
                .thenReturn(Optional.empty());
            LoginRequest request = new LoginRequest("noexist@example.com", "Password1!");

            // When & Then
            assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("비밀번호 불일치 시 실패 카운터 증가 및 UnauthorizedException")
        void login_비밀번호_불일치() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setPassword("$2a$10$encodedPassword");
            admin.setFailedLoginAttempts(0);
            when(adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse("admin@example.com"))
                .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("wrong", "$2a$10$encodedPassword")).thenReturn(false);

            LoginRequest request = new LoginRequest("admin@example.com", "wrong");

            // When & Then
            assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(UnauthorizedException.class);
            assertThat(admin.getFailedLoginAttempts()).isEqualTo(1);
            verify(adminWriterRepository).save(admin);
        }

        @Test
        @DisplayName("5회 실패 시 15분 계정 잠금")
        void login_5회_실패_잠금() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setPassword("$2a$10$encodedPassword");
            admin.setFailedLoginAttempts(4);
            when(adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse("admin@example.com"))
                .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("wrong", "$2a$10$encodedPassword")).thenReturn(false);

            LoginRequest request = new LoginRequest("admin@example.com", "wrong");

            // When & Then
            assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(UnauthorizedException.class);
            assertThat(admin.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(admin.getAccountLockedUntil()).isNotNull();
            assertThat(admin.getAccountLockedUntil()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("10회 실패 시 1시간 계정 잠금")
        void login_10회_실패_잠금() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setPassword("$2a$10$encodedPassword");
            admin.setFailedLoginAttempts(9);
            when(adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse("admin@example.com"))
                .thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("wrong", "$2a$10$encodedPassword")).thenReturn(false);

            LoginRequest request = new LoginRequest("admin@example.com", "wrong");

            // When & Then
            assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(UnauthorizedException.class);
            assertThat(admin.getFailedLoginAttempts()).isEqualTo(10);
            assertThat(admin.getAccountLockedUntil()).isNotNull();
            assertThat(admin.getAccountLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(59));
        }

        @Test
        @DisplayName("계정 잠금 상태에서 로그인 시도 시 UnauthorizedException")
        void login_계정_잠금() {
            // Given
            AdminEntity admin = createAdminEntity(1L, "admin@example.com", "admin1");
            admin.setPassword("$2a$10$encodedPassword");
            admin.setAccountLockedUntil(LocalDateTime.now().plusMinutes(10));
            when(adminReaderRepository.findByEmailAndIsActiveTrueAndIsDeletedFalse("admin@example.com"))
                .thenReturn(Optional.of(admin));

            LoginRequest request = new LoginRequest("admin@example.com", "Password1!");

            // When & Then
            assertThatThrownBy(() -> adminService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("잠겨");
            verify(passwordEncoder, never()).matches(any(), any());
        }
    }

    // ========== logout 테스트 ==========

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("정상 로그아웃 - Refresh Token 삭제")
        void logout_성공() {
            // Given
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(1L, "refresh-token");
            when(refreshTokenService.findRefreshToken("refresh-token")).thenReturn(Optional.of(tokenEntity));

            // When
            adminService.logout(1L, "refresh-token");

            // Then
            verify(refreshTokenService).deleteRefreshToken(tokenEntity);
        }

        @Test
        @DisplayName("존재하지 않는 Refresh Token으로 로그아웃 시 UnauthorizedException")
        void logout_토큰_미존재() {
            // Given
            when(refreshTokenService.findRefreshToken("invalid-token")).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminService.logout(1L, "invalid-token"))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("다른 Admin의 Refresh Token으로 로그아웃 시 UnauthorizedException")
        void logout_소유권_불일치() {
            // Given
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(2L, "refresh-token");
            when(refreshTokenService.findRefreshToken("refresh-token")).thenReturn(Optional.of(tokenEntity));

            // When & Then
            assertThatThrownBy(() -> adminService.logout(1L, "refresh-token"))
                .isInstanceOf(UnauthorizedException.class);
            verify(refreshTokenService, never()).deleteRefreshToken(any());
        }
    }

    // ========== refreshToken 테스트 ==========

    @Nested
    @DisplayName("refreshToken")
    class RefreshToken {

        @Test
        @DisplayName("정상 토큰 갱신 - rotate-on-use")
        void refreshToken_성공() {
            // Given
            String oldToken = "old-refresh-token";
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(1L, oldToken);
            tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(1));

            AdminEntity activeAdmin = createAdminEntity(1L, "admin@example.com", "admin1");

            when(tokenService.validateToken(oldToken)).thenReturn(true);
            when(tokenService.getPayloadFromToken(oldToken))
                .thenReturn(new JwtTokenPayload("1", "admin@example.com", "ADMIN"));
            when(refreshTokenService.findRefreshToken(oldToken)).thenReturn(Optional.of(tokenEntity));
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(activeAdmin));

            TokenResponse expected = new TokenResponse("new-access", "new-refresh", "Bearer", 900L, 86400L);
            when(tokenService.generateTokens(1L, "admin@example.com", "ADMIN")).thenReturn(expected);

            // When
            TokenResponse response = adminService.refreshToken(new RefreshTokenRequest(oldToken));

            // Then
            assertThat(response).isEqualTo(expected);
            verify(refreshTokenService).deleteRefreshToken(tokenEntity);
            verify(tokenService).generateTokens(1L, "admin@example.com", "ADMIN");
        }

        @Test
        @DisplayName("유효하지 않은 JWT 서명 시 UnauthorizedException")
        void refreshToken_잘못된_서명() {
            // Given
            when(tokenService.validateToken("invalid")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest("invalid")))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("User role 토큰으로 Admin 갱신 시 UnauthorizedException")
        void refreshToken_USER_role_거부() {
            // Given
            String token = "user-refresh-token";
            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.getPayloadFromToken(token))
                .thenReturn(new JwtTokenPayload("1", "user@example.com", "USER"));

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest(token)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("관리자 전용");
        }

        @Test
        @DisplayName("만료된 Refresh Token으로 갱신 시 UnauthorizedException")
        void refreshToken_만료() {
            // Given
            String token = "expired-token";
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(1L, token);
            tokenEntity.setExpiresAt(LocalDateTime.now().minusHours(1));

            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.getPayloadFromToken(token))
                .thenReturn(new JwtTokenPayload("1", "admin@example.com", "ADMIN"));
            when(refreshTokenService.findRefreshToken(token)).thenReturn(Optional.of(tokenEntity));

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest(token)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("만료");
        }

        @Test
        @DisplayName("DB에 없는 Refresh Token으로 갱신 시 UnauthorizedException")
        void refreshToken_DB_미존재() {
            // Given
            String token = "not-in-db";
            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.getPayloadFromToken(token))
                .thenReturn(new JwtTokenPayload("1", "admin@example.com", "ADMIN"));
            when(refreshTokenService.findRefreshToken(token)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest(token)))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("비활성화된 Admin 계정으로 토큰 갱신 시 UnauthorizedException")
        void refreshToken_비활성_계정() {
            // Given
            String token = "valid-token";
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(1L, token);
            tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(1));

            AdminEntity inactiveAdmin = createAdminEntity(1L, "admin@example.com", "admin1");
            inactiveAdmin.setIsActive(false);

            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.getPayloadFromToken(token))
                .thenReturn(new JwtTokenPayload("1", "admin@example.com", "ADMIN"));
            when(refreshTokenService.findRefreshToken(token)).thenReturn(Optional.of(tokenEntity));
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(inactiveAdmin));

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest(token)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("비활성화");
        }

        @Test
        @DisplayName("삭제된 Admin 계정으로 토큰 갱신 시 UnauthorizedException")
        void refreshToken_삭제된_계정() {
            // Given
            String token = "valid-token";
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(1L, token);
            tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(1));

            AdminEntity deletedAdmin = createAdminEntity(1L, "admin@example.com", "admin1");
            deletedAdmin.setIsDeleted(true);

            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.getPayloadFromToken(token))
                .thenReturn(new JwtTokenPayload("1", "admin@example.com", "ADMIN"));
            when(refreshTokenService.findRefreshToken(token)).thenReturn(Optional.of(tokenEntity));
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.of(deletedAdmin));

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest(token)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("비활성화");
        }

        @Test
        @DisplayName("존재하지 않는 Admin 계정으로 토큰 갱신 시 UnauthorizedException")
        void refreshToken_미존재_계정() {
            // Given
            String token = "valid-token";
            RefreshTokenEntity tokenEntity = createAdminRefreshTokenEntity(1L, token);
            tokenEntity.setExpiresAt(LocalDateTime.now().plusDays(1));

            when(tokenService.validateToken(token)).thenReturn(true);
            when(tokenService.getPayloadFromToken(token))
                .thenReturn(new JwtTokenPayload("1", "admin@example.com", "ADMIN"));
            when(refreshTokenService.findRefreshToken(token)).thenReturn(Optional.of(tokenEntity));
            when(adminReaderRepository.findById(1L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminService.refreshToken(new RefreshTokenRequest(token)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("비활성화");
        }
    }

    // ========== 테스트 헬퍼 ==========

    private AdminEntity createAdminEntity(Long id, String email, String username) {
        AdminEntity admin = new AdminEntity();
        admin.setId(id);
        admin.setEmail(email);
        admin.setUsername(username);
        admin.setPassword("$2a$10$encodedPassword");
        admin.setRole("ADMIN");
        admin.setIsActive(true);
        admin.setIsDeleted(false);
        admin.setFailedLoginAttempts(0);
        return admin;
    }

    private RefreshTokenEntity createAdminRefreshTokenEntity(Long adminId, String token) {
        RefreshTokenEntity entity = RefreshTokenEntity.createForAdmin(adminId, token, LocalDateTime.now().plusDays(1));
        return entity;
    }
}
