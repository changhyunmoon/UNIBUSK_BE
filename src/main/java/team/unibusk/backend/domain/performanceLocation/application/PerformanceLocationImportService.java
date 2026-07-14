package team.unibusk.backend.domain.performanceLocation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.*;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import team.unibusk.backend.domain.performanceLocation.infrastructure.*;
import java.io.IOException;
import java.util.*;

@Service @RequiredArgsConstructor
public class PerformanceLocationImportService {
    private static final long MAX_EXCEL_SIZE=50L*1024*1024;
    private final PerformanceLocationImportJobJpaRepository jobRepository;
    private final PerformanceLocationImportRowResultJpaRepository rowRepository;
    private final ImageUploadSessionJpaRepository sessionRepository;
    private final ImageUploadStorageService storageService;

    @Transactional
    public PerformanceLocationImportCreateResponse create(MultipartFile file, Long sessionId) throws IOException {
        validate(file);
        String key="temp/performance-location-imports/excels/"+UUID.randomUUID()+".xlsx";
        storageService.saveImportExcel(key,file.getInputStream(),file.getSize());
        try {
            PerformanceLocationImportJob job=jobRepository.save(new PerformanceLocationImportJob(sessionId,key,file.getOriginalFilename()));
            if(sessionId!=null){
                ImageUploadSession session=sessionRepository.findWithLockById(sessionId).orElseThrow(()->new IllegalArgumentException("이미지 업로드 세션을 찾을 수 없습니다."));
                session.claimForImport(job.getId());
            }
            return new PerformanceLocationImportCreateResponse(job.getId(),job.getStatus(),"공연장소 업로드 작업이 등록되었습니다.");
        } catch(RuntimeException e){ storageService.deleteObject(key); throw e; }
    }
    @Transactional(readOnly=true)
    public PerformanceLocationImportStatusResponse getStatus(Long id){
        PerformanceLocationImportJob j=jobRepository.findById(id).orElseThrow(()->new IllegalArgumentException("업로드 작업을 찾을 수 없습니다."));
        List<PerformanceLocationImportRowFailureResponse> failures=rowRepository.findByImportJobIdOrderByRowNum(id).stream()
                .filter(r->r.getStatus()==PerformanceLocationImportRowStatus.FAILED)
                .map(r->new PerformanceLocationImportRowFailureResponse(r.getRowNum(),r.getNormalizedName(),r.getReasonCode(),r.getMessage())).toList();
        return new PerformanceLocationImportStatusResponse(j.getId(),j.getStatus(),j.getTotalCount(),j.getProcessedCount(),j.getSuccessCount(),j.getFailCount(),j.getWarningCount(),failures,j.getCompletedAt());
    }
    private void validate(MultipartFile f){
        if(f==null||f.isEmpty())throw new IllegalArgumentException("엑셀 파일은 필수입니다.");
        String n=f.getOriginalFilename();if(n==null||!n.toLowerCase().endsWith(".xlsx"))throw new IllegalArgumentException("xlsx 파일만 업로드할 수 있습니다.");
        if(f.getSize()>MAX_EXCEL_SIZE)throw new IllegalArgumentException("엑셀 파일은 최대 50MB입니다.");
    }
}
