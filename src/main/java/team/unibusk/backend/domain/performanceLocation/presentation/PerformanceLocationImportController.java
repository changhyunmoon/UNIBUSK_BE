package team.unibusk.backend.domain.performanceLocation.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import team.unibusk.backend.domain.performanceLocation.application.PerformanceLocationImportService;
import team.unibusk.backend.domain.performanceLocation.application.dto.response.*;
import java.io.IOException;

@RestController @RequiredArgsConstructor
@RequestMapping("/performance-locations/imports")
public class PerformanceLocationImportController {
    private final PerformanceLocationImportService service;
    @PostMapping("/excel")
    public ResponseEntity<PerformanceLocationImportCreateResponse> create(@RequestParam("file") MultipartFile file,
            @RequestParam(value="uploadSessionId",required=false) Long uploadSessionId) throws IOException {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(file,uploadSessionId));
    }
    @GetMapping("/{importJobId}")
    public ResponseEntity<PerformanceLocationImportStatusResponse> status(@PathVariable Long importJobId){return ResponseEntity.ok(service.getStatus(importJobId));}
}
