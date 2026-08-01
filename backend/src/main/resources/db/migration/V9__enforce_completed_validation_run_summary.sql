ALTER TABLE validation_run
    ADD CONSTRAINT ck_validation_run_completed_state
        CHECK (
            status <> 'COMPLETED'
            OR (
                started_at IS NOT NULL
                AND finished_at IS NOT NULL
                AND failure_reason IS NULL
                AND finished_at >= started_at
                AND valid_rows + invalid_rows = total_rows
                AND issue_count >= invalid_rows
            )
        );
