package team.unibusk.backend.domain.performanceLocation.application.dto;
public record PerformanceLocationImportRow(int rowNum, String name, String address, String operatorName,
 String operatorPhoneNumber, String availableHours, String operatorUrl, Double latitude, Double longitude,
 String imageFileName, String guide1, String guide2, String guide3, Long imageUploadItemId) {
 public PerformanceLocationImportRow withImageItem(Long id) { return new PerformanceLocationImportRow(rowNum,name,address,operatorName,operatorPhoneNumber,availableHours,operatorUrl,latitude,longitude,imageFileName,guide1,guide2,guide3,id); }
}
