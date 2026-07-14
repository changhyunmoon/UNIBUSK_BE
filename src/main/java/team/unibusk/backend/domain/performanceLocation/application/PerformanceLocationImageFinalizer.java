package team.unibusk.backend.domain.performanceLocation.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import team.unibusk.backend.domain.performanceLocation.infrastructure.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class PerformanceLocationImageFinalizer {
    private final PerformanceLocationImageFinalizeTaskJpaRepository taskRepository;
    private final PerformanceLocationImportJobJpaRepository jobRepository;
    private final PerformanceLocationJpaRepository locationRepository;
    private final ImageUploadItemJpaRepository itemRepository;
    private final ImageUploadSessionJpaRepository sessionRepository;
    private final ImageUploadStorageService storageService;
    private final PlatformTransactionManager transactionManager;

    public void processDueTasks(){
        Set<Long> jobs=new HashSet<>();
        for(var task:taskRepository.findDue(ImageFinalizeTaskStatus.PENDING,ImageFinalizeTaskStatus.RETRY_WAIT,LocalDateTime.now(),PageRequest.of(0,20))){jobs.add(task.getImportJobId());process(task.getId());}
        jobs.forEach(id->{var j=jobRepository.findById(id).orElse(null);if(j!=null)finishIfPossible(id,j.getUploadSessionId());});
    }
    public void finalizeJob(Long jobId,Long sessionId){
        taskRepository.findByImportJobIdAndStatusIn(jobId,List.of(ImageFinalizeTaskStatus.PENDING)).forEach(t->process(t.getId()));
        finishIfPossible(jobId,sessionId);
    }
    private void process(Long taskId){
        var task=taskRepository.findById(taskId).orElseThrow();
        try{
            var item=itemRepository.findById(task.getImageUploadItemId()).orElseThrow();
            storageService.finalizeObject(item,task.getFinalObjectKey());
            tx(()->{
                var managedTask=taskRepository.findById(taskId).orElseThrow();var managedItem=itemRepository.findById(managedTask.getImageUploadItemId()).orElseThrow();
                var location=locationRepository.findById(managedTask.getPerformanceLocationId()).orElseThrow();
                location.getImages().add(PerformanceLocationImage.builder().imageUrl(storageService.createPublicUrl(managedTask.getFinalObjectKey())).build());
                managedItem.confirm(managedTask.getFinalObjectKey());managedTask.complete();return null;
            });
        }catch(Exception e){
            tx(()->{var t=taskRepository.findById(taskId).orElseThrow();t.fail(e.getMessage());if(t.getStatus()==ImageFinalizeTaskStatus.FAILED)itemRepository.findById(t.getImageUploadItemId()).orElseThrow().markFinalizeFailed(e.getMessage());return null;});
            log.warn("이미지 finalization 실패. taskId={}",taskId,e);
        }
    }
    private void finishIfPossible(Long jobId,Long sessionId){
        long pending=taskRepository.findByImportJobIdAndStatusIn(jobId,List.of(ImageFinalizeTaskStatus.PENDING,ImageFinalizeTaskStatus.RETRY_WAIT)).size();
        if(pending>0)return;
        tx(()->{
            var job=jobRepository.findById(jobId).orElseThrow();
            if(sessionId!=null){var session=sessionRepository.findById(sessionId).orElseThrow();itemRepository.findBySessionId(sessionId).forEach(ImageUploadItem::markUnused);session.complete();}
            if(job.getStatus()!=PerformanceLocationImportJobStatus.FAILED){
                boolean partial=job.getFailCount()>0||taskRepository.countByImportJobIdAndStatus(jobId,ImageFinalizeTaskStatus.FAILED)>0;job.complete(partial);
            }
            return null;
        });
        try{storageService.deleteObject(jobRepository.findById(jobId).orElseThrow().getExcelObjectKey());}catch(Exception e){log.warn("임시 엑셀 정리 실패. jobId={}",jobId,e);}
    }
    private <T>T tx(java.util.concurrent.Callable<T> c){return new TransactionTemplate(transactionManager).execute(s->{try{return c.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});}
}
