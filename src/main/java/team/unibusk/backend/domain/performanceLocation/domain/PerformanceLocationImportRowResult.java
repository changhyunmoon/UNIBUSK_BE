package team.unibusk.backend.domain.performanceLocation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.unibusk.backend.global.domain.BaseTimeEntity;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name="uk_import_job_row", columnNames={"import_job_id","rowNum"}))
public class PerformanceLocationImportRowResult extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="import_job_id", nullable=false) private Long importJobId;
    @Column(nullable=false) private int rowNum;
    @Column(length=255) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private PerformanceLocationImportRowStatus status;
    private Long performanceLocationId;
    @Column(length=50) private String reasonCode;
    @Column(length=500) private String message;
    public PerformanceLocationImportRowResult(Long jobId, int rowNum, String name, PerformanceLocationImportRowStatus status, Long locationId, String code, String message) {
        this.importJobId=jobId; this.rowNum=rowNum; this.normalizedName=name; this.status=status;
        this.performanceLocationId=locationId; this.reasonCode=code; this.message=message;
    }
}
