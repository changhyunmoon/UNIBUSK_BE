package team.unibusk.backend.domain.performanceLocation.domain;

public enum ImageUploadItemStatus {
    PENDING,
    UPLOADING,
    UPLOAD_FAILED,
    UPLOADED,
    PROCESSING,
    READY,
    VALIDATION_FAILED,
    OPTIMIZE_FAILED,
    CONFIRMED,
    FINALIZE_FAILED,
    UNUSED;

    public boolean isTerminal() {
        return this == READY
                || this == CONFIRMED
                || this == FINALIZE_FAILED
                || this == UNUSED
                || this == VALIDATION_FAILED
                || this == OPTIMIZE_FAILED
                || this == UPLOAD_FAILED;
    }

    public boolean isFinalizedTerminal() {
        return this == CONFIRMED
                || this == FINALIZE_FAILED
                || this == VALIDATION_FAILED
                || this == OPTIMIZE_FAILED
                || this == UPLOAD_FAILED;
    }
}
