package com.tech.n.ai.domain.mongodb.repository;

import com.tech.n.ai.domain.mongodb.document.ConversationMessageDocument;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ConversationMessageRepository
 */
@Repository
public interface ConversationMessageRepository extends MongoRepository<ConversationMessageDocument, ObjectId> {

    Optional<ConversationMessageDocument> findByMessageId(String messageId);

    @Query(value = "{ 'session_id': ?0 }", sort = "{ 'sequence_number': 1 }")
    List<ConversationMessageDocument> findBySessionIdOrderBySequenceNumberAsc(String sessionId);

    @Query(value = "{ 'session_id': ?0 }", sort = "{ 'sequence_number': 1 }")
    Page<ConversationMessageDocument> findBySessionIdOrderBySequenceNumberAsc(String sessionId, Pageable pageable);

    @Query(value = "{ 'session_id': ?0 }")
    Page<ConversationMessageDocument> findBySessionId(String sessionId, Pageable pageable);
}
