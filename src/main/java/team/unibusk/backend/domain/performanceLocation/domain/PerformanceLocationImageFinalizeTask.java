package team.unibusk.backend.domain.performanceLocation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.unibusk.backend.global.domain.BaseTimeEntity;
import java.time.LocalDateTime;

@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(indexes=@Index(name="idx_finalize_task_due", columnList="status,nextRetryAt"))
public class PerformanceLocationImageFinalizeTask extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long importJobId;
    @Column(nullable=false) private Long performanceLocationId;
    @Column(nullable=false, unique=true) private Long imageUploadItemId;
    @Column(nullable=false, length=512) private String finalObjectKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private ImageFinalizeTaskStatus status=ImageFinalizeTaskStatus.PENDING;
    private int retryCount;
    private LocalDateTime nextRetryAt;
    @Column(length=500) private String lastFailureReason;
    private LocalDateTime completedAt;
    public PerformanceLocationImageFinalizeTask(Long jobId, Long locationId, Long itemId, String finalKey) {
        this.importJobId=jobId; this.performanceLocationId=locationId; this.imageUploadItemId=itemId; this.finalObjectKey=finalKey;
    }
    public void complete() { status=ImageFinalizeTaskStatus.COMPLETED; completedAt=LocalDateTime.now(); }
    public void fail(String reason) {
        retryCount++; lastFailureReason=reason == null ? null : reason.substring(0, Math.min(500, reason.length()));
        if (retryCount >= 3) status=ImageFinalizeTaskStatus.FAILED;
        else { status=ImageFinalizeTaskStatus.RETRY_WAIT; nextRetryAt=LocalDateTime.now().plusMinutes(retryCount == 1 ? 1 : 5); }
    }
}
