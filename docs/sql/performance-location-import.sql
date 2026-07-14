-- 운영 환경은 spring.jpa.hibernate.ddl-auto=validate이므로 배포 전에 적용한다.

ALTER TABLE image_upload_session ADD COLUMN import_job_id BIGINT NULL;

CREATE TABLE performance_location_import_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    upload_session_id BIGINT NULL,
    excel_object_key VARCHAR(512) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    last_processed_row INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    heartbeat_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    worker_id VARCHAR(100) NULL,
    failure_reason VARCHAR(500) NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_import_job_status_lease (status, lease_expires_at)
);

CREATE TABLE performance_location_import_row_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_job_id BIGINT NOT NULL,
    row_num INT NOT NULL,
    normalized_name VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    performance_location_id BIGINT NULL,
    reason_code VARCHAR(50) NULL,
    message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_import_job_row UNIQUE (import_job_id, row_num),
    INDEX idx_import_row_job (import_job_id)
);

CREATE TABLE performance_location_image_finalize_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_job_id BIGINT NOT NULL,
    performance_location_id BIGINT NOT NULL,
    image_upload_item_id BIGINT NOT NULL,
    final_object_key VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_failure_reason VARCHAR(500) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_finalize_task_image_item UNIQUE (image_upload_item_id),
    INDEX idx_finalize_task_due (status, next_retry_at),
    INDEX idx_finalize_task_job (import_job_id)
);
