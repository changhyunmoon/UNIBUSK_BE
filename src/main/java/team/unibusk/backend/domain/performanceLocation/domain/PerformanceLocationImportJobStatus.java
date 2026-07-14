package team.unibusk.backend.domain.performanceLocation.domain;

public enum PerformanceLocationImportJobStatus {
    CREATED, VALIDATING, SAVING, FINALIZING_IMAGES, RETRY_WAIT,
    COMPLETED, PARTIAL_COMPLETED, FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == PARTIAL_COMPLETED || this == FAILED;
    }
}
