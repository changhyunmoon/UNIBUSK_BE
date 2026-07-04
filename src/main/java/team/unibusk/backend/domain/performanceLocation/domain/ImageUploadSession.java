package team.unibusk.backend.domain.performanceLocation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.unibusk.backend.global.domain.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageUploadSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImageUploadSessionStatus status;

    @Column(nullable = false)
    private int totalItemCount;

    @Column(nullable = false)
    private long totalSize;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    private ImageUploadSession(
            int totalItemCount,
            long totalSize,
            LocalDateTime expiresAt
    ) {
        this.status = ImageUploadSessionStatus.CREATED;
        this.totalItemCount = totalItemCount;
        this.totalSize = totalSize;
        this.expiresAt = expiresAt;
    }

    public void startUploading() {
        if (this.status == ImageUploadSessionStatus.CREATED) {
            this.status = ImageUploadSessionStatus.UPLOADING;
        }
    }

    public void markUploaded() {
        if (this.status == ImageUploadSessionStatus.CREATED
                || this.status == ImageUploadSessionStatus.UPLOADING) {
            this.status = ImageUploadSessionStatus.UPLOADED;
        }
    }

    public void startProcessing() {
        if (this.status == ImageUploadSessionStatus.UPLOADED
                || this.status == ImageUploadSessionStatus.UPLOADING) {
            this.status = ImageUploadSessionStatus.PROCESSING;
        }
    }

    public void markReady() {
        this.status = ImageUploadSessionStatus.READY;
    }

    public void complete() {
        this.status = ImageUploadSessionStatus.COMPLETED;
    }

    public void expire() {
        this.status = ImageUploadSessionStatus.EXPIRED;
    }
}
