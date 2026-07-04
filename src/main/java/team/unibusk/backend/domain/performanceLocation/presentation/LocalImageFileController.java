package team.unibusk.backend.domain.performanceLocation.presentation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class LocalImageFileController {

    @Value("${image-upload.local-root:${user.dir}/build/local-image-uploads}")
    private String localRoot;

    @GetMapping("/local-image-files/{*objectKey}")
    public ResponseEntity<Resource> getLocalImage(@PathVariable String objectKey) {
        Path root = Path.of(localRoot).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey.replaceFirst("^/", "")).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new FileSystemResource(target));
    }
}
