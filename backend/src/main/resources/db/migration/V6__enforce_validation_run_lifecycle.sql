ALTER TABLE validation_run
    ADD CONSTRAINT ck_validation_run_processing_state
        CHECK (
            status <> 'PROCESSING'
            OR (
                started_at IS NOT NULL
                AND finished_at IS NULL
                AND failure_reason IS NULL
            )
        ),
    ADD CONSTRAINT ck_validation_run_failed_state
        CHECK (
            status <> 'FAILED'
            OR (
                started_at IS NOT NULL
                AND finished_at IS NOT NULL
                AND failure_reason IS NOT NULL
                AND failure_reason ~ '[^[:space:]]'
                AND char_length(failure_reason) <= 255
            )
        ),
    ADD CONSTRAINT ck_validation_run_failure_reason_state
        CHECK (
            status = 'FAILED'
            OR failure_reason IS NULL
        ),
    ADD CONSTRAINT ck_validation_run_time_order
        CHECK (
            finished_at IS NULL
            OR (
                started_at IS NOT NULL
                AND finished_at >= started_at
            )
        );
