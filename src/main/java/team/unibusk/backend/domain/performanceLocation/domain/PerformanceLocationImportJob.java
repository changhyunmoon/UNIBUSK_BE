package team.unibusk.backend.domain.performanceLocation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.unibusk.backend.global.domain.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_import_job_status_lease", columnList = "status,leaseExpiresAt"))
public class PerformanceLocationImportJob extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long uploadSessionId;
    @Column(nullable = false, length = 512) private String excelObjectKey;
    @Column(nullable = false, length = 255) private String originalFileName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PerformanceLocationImportJobStatus status = PerformanceLocationImportJobStatus.CREATED;
    private int totalCount;
    private int processedCount;
    private int successCount;
    private int failCount;
    private int warningCount;
    private int lastProcessedRow;
    private int retryCount;
    private LocalDateTime heartbeatAt;
    private LocalDateTime leaseExpiresAt;
    @Column(length = 100) private String workerId;
    @Column(length = 500) private String failureReason;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public PerformanceLocationImportJob(Long uploadSessionId, String excelObjectKey, String originalFileName) {
        this.uploadSessionId = uploadSessionId;
        this.excelObjectKey = excelObjectKey;
        this.originalFileName = originalFileName;
    }

    public void claim(String workerId, LocalDateTime now) {
        this.workerId = workerId;
        this.heartbeatAt = now;
        this.leaseExpiresAt = now.plusMinutes(2);
        this.status = PerformanceLocationImportJobStatus.VALIDATING;
        if (startedAt == null) startedAt = now;
    }
    public void startSaving(int totalCount) { this.totalCount = totalCount; this.status = PerformanceLocationImportJobStatus.SAVING; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public void heartbeat() { this.heartbeatAt=LocalDateTime.now(); this.leaseExpiresAt=heartbeatAt.plusMinutes(2); }
    public void progress(int processed, int success, int failed, int lastRow) {
        this.processedCount += processed; this.successCount += success; this.failCount += failed;
        this.lastProcessedRow = Math.max(this.lastProcessedRow, lastRow);
        this.heartbeatAt = LocalDateTime.now(); this.leaseExpiresAt = heartbeatAt.plusMinutes(2);
    }
    public void startFinalizing() { this.status = PerformanceLocationImportJobStatus.FINALIZING_IMAGES; }
    public void complete(boolean partial) {
        this.status = partial ? PerformanceLocationImportJobStatus.PARTIAL_COMPLETED : PerformanceLocationImportJobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now(); this.leaseExpiresAt = null;
    }
    public void fail(String reason) {
        this.status = PerformanceLocationImportJobStatus.FAILED; this.failureReason = truncate(reason);
        this.completedAt = LocalDateTime.now(); this.leaseExpiresAt = null;
    }
    public void retry(String reason) { this.status = PerformanceLocationImportJobStatus.RETRY_WAIT; this.retryCount++; this.failureReason = truncate(reason); this.leaseExpiresAt = null; }
    private String truncate(String value) { return value == null ? null : value.substring(0, Math.min(500, value.length())); }
}
