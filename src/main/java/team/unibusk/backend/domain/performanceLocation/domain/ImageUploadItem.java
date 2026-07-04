package team.unibusk.backend.domain.performanceLocation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.unibusk.backend.global.domain.BaseTimeEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        indexes = {
                @Index(name = "idx_image_upload_item_session_status", columnList = "session_id,status"),
                @Index(name = "idx_image_upload_item_session_original_file_name", columnList = "session_id,originalFileName")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_image_upload_item_session_original_file_name",
                        columnNames = {"session_id", "originalFileName"}
                )
        }
)
public class ImageUploadItem extends BaseTimeEntity {

    private static final int MAX_PRESIGNED_URL_ISSUE_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ImageUploadSession session;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 512)
    private String rawObjectKey;

    @Column(length = 512)
    private String processedObjectKey;

    @Column(length = 512)
    private String finalObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImageUploadItemStatus status;

    @Column(nullable = false)
    private int presignedUrlIssueCount;

    @Column(length = 500)
    private String failureReason;

    @Builder
    private ImageUploadItem(
            ImageUploadSession session,
            String originalFileName,
            String contentType,
            long fileSize,
            String rawObjectKey
    ) {
        this.session = session;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.rawObjectKey = rawObjectKey;
        this.status = ImageUploadItemStatus.PENDING;
        this.presignedUrlIssueCount = 0;
    }

    public boolean canIssuePresignedUrl() {
        return canMarkUploadFailed()
                && presignedUrlIssueCount < MAX_PRESIGNED_URL_ISSUE_COUNT;
    }

    public boolean isPresignedUrlIssueLimitReached() {
        return presignedUrlIssueCount >= MAX_PRESIGNED_URL_ISSUE_COUNT;
    }

    public void markPresignedUrlIssued() {
        if (!canIssuePresignedUrl()) {
            throw new IllegalStateException("Presigned URL 발급이 불가능한 상태입니다.");
        }

        this.presignedUrlIssueCount++;
        this.status = ImageUploadItemStatus.UPLOADING;
    }

    public boolean canCompleteUpload() {
        return this.status == ImageUploadItemStatus.UPLOADING
                || this.status == ImageUploadItemStatus.UPLOADED;
    }

    public void validateUploadCompletable() {
        if (!canCompleteUpload()) {
            throw new IllegalStateException("업로드 완료 처리가 불가능한 상태입니다. 현재 상태: " + this.status);
        }
    }

    public void markUploaded() {
        validateUploadCompletable();
        this.status = ImageUploadItemStatus.UPLOADED;
        this.failureReason = null;
    }

    public boolean canMarkUploadFailed() {
        return this.status == ImageUploadItemStatus.PENDING
                || this.status == ImageUploadItemStatus.UPLOADING
                || this.status == ImageUploadItemStatus.UPLOAD_FAILED;
    }

    public void validateUploadFailMarkable() {
        if (!canMarkUploadFailed()) {
            throw new IllegalStateException("업로드 실패 처리가 불가능한 상태입니다. 현재 상태: " + this.status);
        }
    }

    public void markUploadFailed(String reason) {
        validateUploadFailMarkable();
        this.status = ImageUploadItemStatus.UPLOAD_FAILED;
        this.failureReason = reason;
    }

    public void startProcessing() {
        if (this.status == ImageUploadItemStatus.UPLOADED) {
            this.status = ImageUploadItemStatus.PROCESSING;
        }
    }

    public void markReady(String processedObjectKey) {
        this.processedObjectKey = processedObjectKey;
        this.status = ImageUploadItemStatus.READY;
    }

    public void markValidationFailed(String reason) {
        this.status = ImageUploadItemStatus.VALIDATION_FAILED;
        this.failureReason = reason;
    }

    public void markOptimizeFailed(String reason) {
        this.status = ImageUploadItemStatus.OPTIMIZE_FAILED;
        this.failureReason = reason;
    }

    public void confirm(String finalObjectKey) {
        this.finalObjectKey = finalObjectKey;
        this.status = ImageUploadItemStatus.CONFIRMED;
    }

    public void markFinalizeFailed(String reason) {
        this.status = ImageUploadItemStatus.FINALIZE_FAILED;
        this.failureReason = reason;
    }
}
