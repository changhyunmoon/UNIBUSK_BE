package team.unibusk.backend.domain.performanceLocation.application.dto.response;
import team.unibusk.backend.domain.performanceLocation.domain.PerformanceLocationImportJobStatus;
public record PerformanceLocationImportCreateResponse(Long importJobId, PerformanceLocationImportJobStatus status, String message) {}
