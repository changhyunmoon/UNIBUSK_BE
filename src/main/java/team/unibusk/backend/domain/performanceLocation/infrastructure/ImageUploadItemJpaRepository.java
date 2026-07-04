package team.unibusk.backend.domain.performanceLocation.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadItem;
import team.unibusk.backend.domain.performanceLocation.domain.ImageUploadItemStatus;

import java.util.List;

public interface ImageUploadItemJpaRepository extends JpaRepository<ImageUploadItem, Long> {

    List<ImageUploadItem> findBySessionId(Long sessionId);

    List<ImageUploadItem> findBySessionIdAndIdIn(Long sessionId, List<Long> ids);

    boolean existsBySessionIdAndOriginalFileName(Long sessionId, String originalFileName);

    long countBySessionIdAndStatus(Long sessionId, ImageUploadItemStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImageUploadItem item
            set item.status = :processingStatus
            where item.session.id = :sessionId
              and item.id = :itemId
              and item.status = :uploadedStatus
            """)
    int markProcessingIfStatus(
            @Param("sessionId") Long sessionId,
            @Param("itemId") Long itemId,
            @Param("uploadedStatus") ImageUploadItemStatus uploadedStatus,
            @Param("processingStatus") ImageUploadItemStatus processingStatus
    );

    default int markProcessingIfUploaded(Long sessionId, Long itemId) {
        return markProcessingIfStatus(
                sessionId,
                itemId,
                ImageUploadItemStatus.UPLOADED,
                ImageUploadItemStatus.PROCESSING
        );
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImageUploadItem item
            set item.status = :readyStatus,
                item.processedObjectKey = :processedObjectKey,
                item.failureReason = null
            where item.session.id = :sessionId
              and item.id = :itemId
              and item.status = :processingStatus
            """)
    int markReadyIfProcessing(
            @Param("sessionId") Long sessionId,
            @Param("itemId") Long itemId,
            @Param("processedObjectKey") String processedObjectKey,
            @Param("processingStatus") ImageUploadItemStatus processingStatus,
            @Param("readyStatus") ImageUploadItemStatus readyStatus
    );

    default int markReadyIfProcessing(Long sessionId, Long itemId, String processedObjectKey) {
        return markReadyIfProcessing(
                sessionId,
                itemId,
                processedObjectKey,
                ImageUploadItemStatus.PROCESSING,
                ImageUploadItemStatus.READY
        );
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ImageUploadItem item
            set item.status = :failedStatus,
                item.failureReason = :failureReason
            where item.session.id = :sessionId
              and item.id = :itemId
              and item.status = :processingStatus
            """)
    int markOptimizeFailedIfProcessing(
            @Param("sessionId") Long sessionId,
            @Param("itemId") Long itemId,
            @Param("failureReason") String failureReason,
            @Param("processingStatus") ImageUploadItemStatus processingStatus,
            @Param("failedStatus") ImageUploadItemStatus failedStatus
    );

    default int markOptimizeFailedIfProcessing(Long sessionId, Long itemId, String failureReason) {
        return markOptimizeFailedIfProcessing(
                sessionId,
                itemId,
                failureReason,
                ImageUploadItemStatus.PROCESSING,
                ImageUploadItemStatus.OPTIMIZE_FAILED
        );
    }
}
