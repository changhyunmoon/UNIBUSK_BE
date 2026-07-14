package team.unibusk.backend.domain.performanceLocation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import team.unibusk.backend.domain.applicationguide.domain.*;
import team.unibusk.backend.domain.performanceLocation.application.dto.PerformanceLocationImportRow;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import team.unibusk.backend.domain.performanceLocation.infrastructure.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PerformanceLocationImportSaveService {
    private final PerformanceLocationJpaRepository locationRepository;
    private final ApplicationGuideRepository guideRepository;
    private final PerformanceLocationImportRowResultJpaRepository rowResultRepository;
    private final PerformanceLocationImageFinalizeTaskJpaRepository taskRepository;
    private final ImageUploadItemJpaRepository itemRepository;
    private final ImageUploadStorageService storageService;
    private final PlatformTransactionManager transactionManager;

    public SaveResult saveWithIsolation(Long jobId,List<PerformanceLocationImportRow> rows){
        try{
            TransactionTemplate tx=new TransactionTemplate(transactionManager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tx.execute(status->saveChunk(jobId,rows));
        }catch(DataIntegrityViolationException e){
            if(rows.size()==1){ saveFailure(jobId,rows.get(0),"DB_CONSTRAINT_VIOLATION","DB 제약 조건을 위반했습니다.");return new SaveResult(0,1); }
            int mid=rows.size()/2; SaveResult a=saveWithIsolation(jobId,rows.subList(0,mid)); SaveResult b=saveWithIsolation(jobId,rows.subList(mid,rows.size()));return new SaveResult(a.success+b.success,a.failed+b.failed);
        }
    }
    private SaveResult saveChunk(Long jobId,List<PerformanceLocationImportRow> rows){
        for(PerformanceLocationImportRow r:rows){
            PerformanceLocation location=locationRepository.saveAndFlush(PerformanceLocation.builder().name(r.name()).address(r.address()).operatorName(r.operatorName())
                    .operatorPhoneNumber(r.operatorPhoneNumber()).availableHours(r.availableHours()).operatorUrl(r.operatorUrl()).latitude(r.latitude()).longitude(r.longitude()).build());
            List<ApplicationGuide> guides=java.util.stream.Stream.of(r.guide1(),r.guide2(),r.guide3()).filter(v->v!=null&&!v.isBlank()).map(v->ApplicationGuide.create(v,location.getId())).toList();
            if(!guides.isEmpty())guideRepository.saveAll(guides);
            if(r.imageUploadItemId()!=null){ ImageUploadItem item=itemRepository.findById(r.imageUploadItemId()).orElseThrow(); taskRepository.save(new PerformanceLocationImageFinalizeTask(jobId,location.getId(),item.getId(),storageService.createFinalObjectKey(item))); }
            rowResultRepository.save(new PerformanceLocationImportRowResult(jobId,r.rowNum(),normalize(r.name()),PerformanceLocationImportRowStatus.SUCCESS,location.getId(),null,null));
        }
        return new SaveResult(rows.size(),0);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void saveFailure(Long jobId,PerformanceLocationImportRow row,String code,String message){
        if(!rowResultRepository.existsByImportJobIdAndRowNumAndStatus(jobId,row.rowNum(),PerformanceLocationImportRowStatus.FAILED))
            rowResultRepository.save(new PerformanceLocationImportRowResult(jobId,row.rowNum(),normalize(row.name()),PerformanceLocationImportRowStatus.FAILED,null,code,message));
    }
    private String normalize(String v){return v==null?null:v.trim().toLowerCase();}
    public record SaveResult(int success,int failed){}
}
