package com.tech.n.ai.domain.aurora.repository.writer;

import com.tech.n.ai.domain.aurora.entity.BaseEntity;
import com.tech.n.ai.domain.aurora.service.history.HistoryService;
import com.tech.n.ai.domain.aurora.service.history.OperationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * WriterRepository의 공통 로직을 제공하는 추상 클래스
 *
 * @param <E> 엔티티 타입 (BaseEntity를 상속)
 */
public abstract class BaseWriterRepository<E extends BaseEntity> {

    protected abstract JpaRepository<E, Long> getJpaRepository();
    protected abstract HistoryService getHistoryService();
    protected abstract EntityManager getEntityManager();
    protected abstract Class<E> getEntityClass();

    /**
     * 엔티티를 저장하고 History를 기록합니다.
     *
     * @param entity 저장할 엔티티
     * @return 저장된 엔티티
     */
    public E save(E entity) {
        boolean isNew = entity.getId() == null;
        Map<String, Object> beforeSnapshot = isNew ? null : getBeforeDataSnapshot(entity.getId());

        E saved = getJpaRepository().save(entity);

        if (isNew) {
            getHistoryService().saveHistory(saved, OperationType.INSERT, null, saved);
        } else {
            getHistoryService().saveHistory(saved, OperationType.UPDATE, beforeSnapshot, saved);
        }

        return saved;
    }

    /**
     * 엔티티를 저장하고 즉시 flush하며 History를 기록합니다.
     *
     * @param entity 저장할 엔티티
     * @return 저장된 엔티티
     */
    public E saveAndFlush(E entity) {
        boolean isNew = entity.getId() == null;
        Map<String, Object> beforeSnapshot = isNew ? null : getBeforeDataSnapshot(entity.getId());

        E saved = getJpaRepository().saveAndFlush(entity);

        if (isNew) {
            getHistoryService().saveHistory(saved, OperationType.INSERT, null, saved);
        } else {
            getHistoryService().saveHistory(saved, OperationType.UPDATE, beforeSnapshot, saved);
        }

        return saved;
    }

    /**
     * 엔티티를 soft delete하고 History를 기록합니다.
     *
     * @param entity 삭제할 엔티티
     */
    public void delete(E entity) {
        boolean wasDeleted = Boolean.TRUE.equals(entity.getIsDeleted());

        // 변경 전 데이터를 DB에서 직접 조회 (1차 캐시 우회)
        Map<String, Object> beforeSnapshot = wasDeleted ? null : getBeforeDataSnapshot(entity.getId());

        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());

        E saved = getJpaRepository().save(entity);

        if (!wasDeleted) {
            getHistoryService().saveHistory(saved, OperationType.DELETE, beforeSnapshot, saved);
        }
    }

    /**
     * ID로 엔티티를 찾아 soft delete하고 History를 기록합니다.
     *
     * @param id 삭제할 엔티티 ID
     */
    public void deleteById(Long id) {
        E entity = getJpaRepository().findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        getEntityName() + " with id " + id + " does not exist"));

        boolean wasDeleted = Boolean.TRUE.equals(entity.getIsDeleted());

        // 변경 전 데이터를 DB에서 직접 조회 (1차 캐시 우회)
        Map<String, Object> beforeSnapshot = wasDeleted ? null : getBeforeDataSnapshot(id);

        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());

        E saved = getJpaRepository().save(entity);

        if (!wasDeleted) {
            getHistoryService().saveHistory(saved, OperationType.DELETE, beforeSnapshot, saved);
        }
    }

    /**
     * 1차 캐시를 우회하여 DB에서 직접 변경 전 데이터를 조회합니다.
     * Native query를 사용하며, FlushMode.COMMIT으로 설정하여
     * 쿼리 실행 전 auto-flush를 방지합니다.
     *
     * @param id 엔티티 ID
     * @return 변경 전 데이터의 Map (컬럼명 -> 값), 없으면 null
     */
    protected Map<String, Object> getBeforeDataSnapshot(Long id) {
        if (id == null) {
            return null;
        }

        String tableName = getTableName();
        String sql = "SELECT * FROM " + tableName + " WHERE id = :id";

        try {
            Query query = getEntityManager()
                    .createNativeQuery(sql, Tuple.class)
                    .setParameter("id", id);

            // Auto-flush 방지: 쿼리 실행 전에 dirty 엔티티가 DB에 반영되지 않도록 함
            query.setFlushMode(FlushModeType.COMMIT);

            @SuppressWarnings("unchecked")
            Tuple result = (Tuple) query.getSingleResult();

            return tupleToMap(result);
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }

    /**
     * Tuple 결과를 Map으로 변환합니다.
     */
    private Map<String, Object> tupleToMap(Tuple tuple) {
        Map<String, Object> map = new HashMap<>();
        for (TupleElement<?> element : tuple.getElements()) {
            String alias = element.getAlias();
            if (alias != null) {
                map.put(alias, tuple.get(alias));
            }
        }
        return map;
    }

    /**
     * @Table 어노테이션에서 테이블명을 가져옵니다.
     * schema가 지정된 경우 "schema.table_name" 형식으로 반환합니다.
     */
    private String getTableName() {
        Class<E> entityClass = getEntityClass();
        Table tableAnnotation = entityClass.getAnnotation(Table.class);

        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            String schema = tableAnnotation.schema();
            String tableName = tableAnnotation.name();

            if (schema != null && !schema.isEmpty()) {
                return schema + "." + tableName;
            }
            return tableName;
        }

        // @Table 어노테이션이 없는 경우 엔티티 클래스명에서 유추
        String simpleName = entityClass.getSimpleName();
        if (simpleName.endsWith("Entity")) {
            simpleName = simpleName.substring(0, simpleName.length() - 6);
        }
        return camelToSnake(simpleName) + "s";
    }

    /**
     * CamelCase를 snake_case로 변환합니다.
     */
    private String camelToSnake(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 엔티티 이름을 반환합니다. 예외 메시지에 사용됩니다.
     * 
     * @return 엔티티 이름
     */
    protected abstract String getEntityName();
}
