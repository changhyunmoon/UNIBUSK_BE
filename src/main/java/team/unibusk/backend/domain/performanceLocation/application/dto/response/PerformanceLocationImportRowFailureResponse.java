package team.unibusk.backend.domain.performanceLocation.application.dto.response;
public record PerformanceLocationImportRowFailureResponse(int rowNum, String name, String reasonCode, String message) {}
