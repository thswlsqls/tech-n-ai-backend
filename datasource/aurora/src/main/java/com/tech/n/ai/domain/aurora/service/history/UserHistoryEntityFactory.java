package com.tech.n.ai.domain.aurora.service.history;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserEntity;
import com.tech.n.ai.domain.aurora.entity.auth.UserHistoryEntity;
import com.tech.n.ai.domain.aurora.repository.writer.history.UserHistoryWriterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * UserHistoryEntity 생성을 담당하는 Factory
 */
@Component
@RequiredArgsConstructor
public class UserHistoryEntityFactory implements HistoryEntityFactory {

    private final UserHistoryWriterRepository userHistoryWriterRepository;

    @Override
    public void createAndSave(BaseEntity entity, OperationType operationType, 
                              String beforeJson, String afterJson, 
                              Long changedBy, LocalDateTime changedAt) {
        UserEntity userEntity = (UserEntity) entity;
        UserHistoryEntity history = new UserHistoryEntity();
        history.setUser(userEntity);
        history.setUserId(userEntity.getId());
        history.setOperationType(operationType.name());
        history.setBeforeData(beforeJson);
        history.setAfterData(afterJson);
        history.setChangedBy(changedBy);
        history.setChangedAt(changedAt);
        userHistoryWriterRepository.save(history);
    }

    @Override
    public boolean supports(BaseEntity entity) {
        return entity instanceof UserEntity;
    }
}
