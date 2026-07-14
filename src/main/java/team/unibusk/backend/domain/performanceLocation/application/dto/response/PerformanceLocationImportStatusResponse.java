package team.unibusk.backend.domain.performanceLocation.application.dto.response;
import team.unibusk.backend.domain.performanceLocation.domain.PerformanceLocationImportJobStatus;
import java.time.LocalDateTime;
import java.util.List;
public record PerformanceLocationImportStatusResponse(Long importJobId, PerformanceLocationImportJobStatus status,
 int totalCount, int processedCount, int successCount, int failCount, int warningCount,
 List<PerformanceLocationImportRowFailureResponse> failedRows, LocalDateTime completedAt) {}
