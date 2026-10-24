package com.tech.n.ai.domain.aurora.service.history;

import com.tech.n.ai.domain.aurora.entity.auth.ProviderEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryServiceImpl 단위 테스트")
class HistoryServiceImplTest {

    @Mock
    private HistoryEntityFactory historyEntityFactory;

    @Nested
    @DisplayName("saveHistory")
    class SaveHistory {

        @Test
        @DisplayName("UserEntity 이력 JSON에 provider의 clientId·clientSecret은 들어가지 않고 providerId는 남는다")
        void saveHistory_UserEntity_provider_제외() {
            // Given
            HistoryServiceImpl historyService = new HistoryServiceImpl(List.of(historyEntityFactory));
            ProviderEntity provider = new ProviderEntity();
            provider.setId(1L);
            provider.setName("GOOGLE");
            provider.setDisplayName("Google");
            provider.setClientId("client-id-value");
            provider.setClientSecret("client-secret-value");
            UserEntity user = UserEntity.createOAuthUser("user@gmail.com", "googleuser", 1L, "google-user-123");
            user.setId(100L);
            user.setProvider(provider);
            when(historyEntityFactory.supports(user)).thenReturn(true);

            // When
            historyService.saveHistory(user, OperationType.INSERT, null, user);

            // Then
            ArgumentCaptor<String> afterJson = ArgumentCaptor.forClass(String.class);
            verify(historyEntityFactory).createAndSave(eq(user), eq(OperationType.INSERT), isNull(),
                afterJson.capture(), isNull(), any());
            assertThat(afterJson.getValue())
                .doesNotContain("client-secret-value")
                .doesNotContain("client-id-value")
                .contains("\"providerId\":1");
        }
    }
}
