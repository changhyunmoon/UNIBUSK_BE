package team.unibusk.backend.domain.performanceLocation.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import team.unibusk.backend.domain.performanceLocation.application.dto.PerformanceLocationImportRow;
import team.unibusk.backend.domain.performanceLocation.domain.*;
import team.unibusk.backend.domain.performanceLocation.infrastructure.*;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

@Slf4j @Component @RequiredArgsConstructor
public class PerformanceLocationImportWorker {
    private static final int CHUNK_SIZE=500;
    private final PerformanceLocationImportJobJpaRepository jobRepository;
    private final PerformanceLocationImportRowResultJpaRepository rowResultRepository;
    private final PerformanceLocationJpaRepository locationRepository;
    private final ImageUploadItemJpaRepository itemRepository;
    private final ImageUploadSessionJpaRepository sessionRepository;
    private final ImageUploadStorageService storageService;
    private final PerformanceLocationExcelParser parser;
    private final PerformanceLocationImportSaveService saveService;
    private final PerformanceLocationImageFinalizer finalizer;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString="${performance-location.import.worker-delay-ms:1000}")
    public void poll(){
        recoverExpiredLeases();
        Long jobId=tx(()->jobRepository.findFirstByStatusInOrderById(List.of(PerformanceLocationImportJobStatus.CREATED,PerformanceLocationImportJobStatus.RETRY_WAIT))
                .map(j->{j.claim(UUID.randomUUID().toString(),java.time.LocalDateTime.now());return j.getId();}).orElse(null));
        if(jobId!=null)process(jobId);
        finalizer.processDueTasks();
    }

    private void recoverExpiredLeases(){
        tx(()->{jobRepository.findByStatusInAndLeaseExpiresAtBefore(
                List.of(PerformanceLocationImportJobStatus.VALIDATING,PerformanceLocationImportJobStatus.SAVING),java.time.LocalDateTime.now())
                .forEach(j->j.retry("작업자 lease가 만료되어 마지막 체크포인트부터 재시도합니다."));return null;});
    }

    private void process(Long jobId){
        PerformanceLocationImportJob snapshot=jobRepository.findById(jobId).orElseThrow();
        try{
            // 첫 번째 순회에서는 파일 전체 정책(세션 누락 등)을 확인해 일부 저장 후 치명적 실패하는 상황을 막는다.
            int total;
            try(InputStream in=storageService.openImportExcel(snapshot.getExcelObjectKey())){
                java.util.concurrent.atomic.AtomicInteger validated=new java.util.concurrent.atomic.AtomicInteger();
                total=parser.parse(in,row->{
                    if(snapshot.getUploadSessionId()==null&&StringUtils.hasText(row.imageFileName()))throw new IllegalArgumentException("UPLOAD_SESSION_REQUIRED: 이미지 파일명이 있으면 uploadSessionId가 필요합니다.");
                    if(validated.incrementAndGet()%CHUNK_SIZE==0)tx(()->{jobRepository.findById(jobId).orElseThrow().heartbeat();return null;});
                });
            }
            int finalTotal=total; tx(()->{jobRepository.findById(jobId).orElseThrow().startSaving(finalTotal);return null;});

            Map<String,ImageUploadItem> images=loadReadyImages(snapshot.getUploadSessionId());
            Set<String> names=new HashSet<>(), imageNames=new HashSet<>();
            List<PerformanceLocationImportRow> chunk=new ArrayList<>(CHUNK_SIZE);
            try(InputStream in=storageService.openImportExcel(snapshot.getExcelObjectKey())){
                parser.parse(in,row->{
                    if(rowResultRepository.existsByImportJobIdAndRowNumAndStatus(jobId,row.rowNum(),PerformanceLocationImportRowStatus.SUCCESS))return;
                    String error=validate(row,names,imageNames,images,snapshot.getUploadSessionId());
                    if(error!=null){saveService.saveFailure(jobId,row,error.substring(0,error.indexOf(':')),error.substring(error.indexOf(':')+1));progress(jobId,1,0,1,row.rowNum());return;}
                    ImageUploadItem item=StringUtils.hasText(row.imageFileName())?images.get(normalize(row.imageFileName())):null;
                    chunk.add(row.withImageItem(item==null?null:item.getId()));
                    if(chunk.size()>=CHUNK_SIZE)flush(jobId,chunk);
                });
            }
            flush(jobId,chunk);
            tx(()->{jobRepository.findById(jobId).orElseThrow().startFinalizing();return null;});
            finalizer.finalizeJob(jobId,snapshot.getUploadSessionId());
        }catch(TransientDataAccessException e){ retryOrFail(jobId,e); }
        catch(Exception e){ fail(jobId,snapshot.getUploadSessionId(),e); }
    }
    private void flush(Long jobId,List<PerformanceLocationImportRow> chunk){
        if(chunk.isEmpty())return;
        Set<String> existing=locationRepository.findByNameIn(chunk.stream().map(PerformanceLocationImportRow::name).toList()).stream().map(p->normalize(p.getName())).collect(java.util.stream.Collectors.toSet());
        List<PerformanceLocationImportRow> valid=new ArrayList<>();int failed=0;
        for(var r:chunk){if(existing.contains(normalize(r.name()))){saveService.saveFailure(jobId,r,"DUPLICATED_NAME","이미 존재하는 공연장소명입니다.");failed++;}else valid.add(r);}
        PerformanceLocationImportSaveService.SaveResult result=valid.isEmpty()?new PerformanceLocationImportSaveService.SaveResult(0,0):saveService.saveWithIsolation(jobId,List.copyOf(valid));
        int last=chunk.get(chunk.size()-1).rowNum();progress(jobId,chunk.size(),result.success(),failed+result.failed(),last);chunk.clear();
    }
    private String validate(PerformanceLocationImportRow r,Set<String> names,Set<String> imageNames,Map<String,ImageUploadItem> images,Long sessionId){
        if(!StringUtils.hasText(r.name()))return "REQUIRED_FIELD:장소명은 필수입니다.";
        if(!StringUtils.hasText(r.address())||!StringUtils.hasText(r.operatorName())||!StringUtils.hasText(r.operatorPhoneNumber()))return "REQUIRED_FIELD:필수 항목이 비어 있습니다.";
        if(r.name().length()>255||r.address().length()>255||r.operatorName().length()>255||r.operatorPhoneNumber().length()>255)return "FIELD_TOO_LONG:기본 정보는 최대 255자입니다.";
        if(r.latitude()==null||r.latitude()<-90||r.latitude()>90)return "INVALID_LATITUDE:위도 범위가 올바르지 않습니다.";
        if(r.longitude()==null||r.longitude()<-180||r.longitude()>180)return "INVALID_LONGITUDE:경도 범위가 올바르지 않습니다.";
        if(!names.add(normalize(r.name())))return "DUPLICATED_NAME:엑셀 안에 중복된 공연장소명이 있습니다.";
        if(StringUtils.hasText(r.operatorUrl())){if(r.operatorUrl().length()>255)return "FIELD_TOO_LONG:운영 URL은 최대 255자입니다.";try{URI.create(r.operatorUrl()).toURL();}catch(Exception e){return "INVALID_OPERATOR_URL:운영 URL 형식이 올바르지 않습니다.";}}
        if(java.util.stream.Stream.of(r.guide1(),r.guide2(),r.guide3()).filter(Objects::nonNull).anyMatch(v->v.length()>255))return "FIELD_TOO_LONG:이용 안내는 최대 255자입니다.";
        if(StringUtils.hasText(r.imageFileName())){
            String key=normalize(r.imageFileName());if(r.imageFileName().length()>255||r.imageFileName().contains("/")||r.imageFileName().contains("\\"))return "INVALID_IMAGE_FILE_NAME:이미지 파일명이 올바르지 않습니다.";
            if(!imageNames.add(key))return "DUPLICATED_IMAGE_FILE_NAME:하나의 이미지는 한 공연장소에서만 사용할 수 있습니다.";
            if(!images.containsKey(key))return "IMAGE_NOT_FOUND:매칭되는 READY 이미지가 없습니다.";
        }
        return null;
    }
    private Map<String,ImageUploadItem> loadReadyImages(Long sessionId){if(sessionId==null)return Map.of();Map<String,ImageUploadItem> m=new HashMap<>();for(var i:itemRepository.findBySessionId(sessionId))if(i.getStatus()==ImageUploadItemStatus.READY)m.put(normalize(i.getOriginalFileName()),i);return m;}
    private void progress(Long id,int p,int s,int f,int row){tx(()->{jobRepository.findById(id).orElseThrow().progress(p,s,f,row);return null;});}
    private void retryOrFail(Long id,Exception e){tx(()->{var j=jobRepository.findById(id).orElseThrow();if(j.getRetryCount()<3)j.retry(e.getMessage());else j.fail(e.getMessage());return null;});}
    private void fail(Long id,Long sessionId,Exception e){
        int success=tx(()->{var j=jobRepository.findById(id).orElseThrow();j.fail(e.getMessage());if(sessionId!=null&&j.getSuccessCount()==0)jobSession(sessionId).releaseImport();return j.getSuccessCount();});
        // 이미 커밋된 행이 있으면 관련 이미지는 끝까지 확정하고 세션도 종료해 IMPORTING 고착을 방지한다.
        if(success>0)finalizer.finalizeJob(id,sessionId);
        log.error("공연장소 import job 실패. jobId={}",id,e);
    }
    private ImageUploadSession jobSession(Long id){return sessionRepository.findById(id).orElseThrow();}
    private String normalize(String v){return v==null?"":v.trim().toLowerCase();}
    private <T>T tx(java.util.concurrent.Callable<T> c){return new TransactionTemplate(transactionManager).execute(s->{try{return c.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});}
}
