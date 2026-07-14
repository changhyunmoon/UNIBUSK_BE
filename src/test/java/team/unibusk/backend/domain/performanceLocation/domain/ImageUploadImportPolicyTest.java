package team.unibusk.backend.domain.performanceLocation.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class ImageUploadImportPolicyTest {
    @Test void READY_세션만_import_job이_선점할_수_있다(){
        ImageUploadSession session=ImageUploadSession.builder().totalItemCount(1).totalSize(1).expiresAt(LocalDateTime.now().plusHours(1)).build();
        assertThatThrownBy(()->session.claimForImport(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test void READY_세션을_선점하면_IMPORTING으로_변경된다(){
        ImageUploadSession session=ImageUploadSession.builder().totalItemCount(1).totalSize(1).expiresAt(LocalDateTime.now().plusHours(1)).build();
        session.startUploading();session.markUploaded();session.startProcessing();session.markReady();
        session.claimForImport(10L);
        assertThat(session.getStatus()).isEqualTo(ImageUploadSessionStatus.IMPORTING);assertThat(session.getImportJobId()).isEqualTo(10L);
    }
}
