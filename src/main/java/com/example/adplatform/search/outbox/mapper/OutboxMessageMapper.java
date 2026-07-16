package com.example.adplatform.search.outbox.mapper;

import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface OutboxMessageMapper {

    @Insert("""
            INSERT INTO outbox_message
                (event_id, topic, message_key, message_type, payload, status, retry_count, next_retry_at)
            VALUES
                (#{eventId}, #{topic}, #{messageKey}, #{messageType}, CAST(#{payload} AS JSON),
                 'PENDING', 0, NOW())
            """)
    int insert(OutboxMessageEntity entity);

    @Select("""
            SELECT id, event_id AS eventId, topic, message_key AS messageKey,
                   message_type AS messageType, payload, status, retry_count AS retryCount,
                   next_retry_at AS nextRetryAt, created_at AS createdAt, sent_at AS sentAt
            FROM outbox_message
            WHERE status = 'PENDING' AND next_retry_at <= NOW()
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxMessageEntity> lockPendingBatch(@Param("limit") int limit);

    @Update("""
            UPDATE outbox_message
            SET status = 'SENT', sent_at = NOW()
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE outbox_message
            SET retry_count = retry_count + 1,
                next_retry_at = DATE_ADD(NOW(), INTERVAL LEAST(60, POW(2, LEAST(retry_count, 5))) SECOND)
            WHERE id = #{id}
            """)
    int scheduleRetry(@Param("id") Long id);

    @Delete("""
            DELETE FROM outbox_message
            WHERE status = 'SENT'
              AND sent_at < DATE_SUB(NOW(), INTERVAL #{retentionDays} DAY)
            """)
    int deleteSentBefore(@Param("retentionDays") int retentionDays);
}
