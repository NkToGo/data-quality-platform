ALTER TABLE source_file
    ADD CONSTRAINT uq_source_file_id_dataset_id
    UNIQUE (id, dataset_id);

ALTER TABLE validation_profile
    ADD CONSTRAINT uq_validation_profile_id_dataset_id
    UNIQUE (id, dataset_id);

CREATE TABLE validation_run (
    id uuid NOT NULL,
    dataset_id uuid NOT NULL,
    source_file_id uuid NOT NULL,
    profile_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    total_rows bigint NOT NULL,
    valid_rows bigint NOT NULL,
    invalid_rows bigint NOT NULL,
    issue_count bigint NOT NULL,
    started_at timestamp(6) with time zone,
    finished_at timestamp(6) with time zone,
    failure_reason text,
    CONSTRAINT pk_validation_run PRIMARY KEY (id),
    CONSTRAINT fk_validation_run_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES dataset (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_validation_run_source_file_dataset
        FOREIGN KEY (source_file_id, dataset_id)
        REFERENCES source_file (id, dataset_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_validation_run_profile_dataset
        FOREIGN KEY (profile_id, dataset_id)
        REFERENCES validation_profile (id, dataset_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_validation_run_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_validation_run_total_rows_nonnegative
        CHECK (total_rows >= 0),
    CONSTRAINT ck_validation_run_valid_rows_nonnegative
        CHECK (valid_rows >= 0),
    CONSTRAINT ck_validation_run_invalid_rows_nonnegative
        CHECK (invalid_rows >= 0),
    CONSTRAINT ck_validation_run_issue_count_nonnegative
        CHECK (issue_count >= 0),
    CONSTRAINT ck_validation_run_pending_state
        CHECK (
            status <> 'PENDING'
            OR (
                total_rows = 0
                AND valid_rows = 0
                AND invalid_rows = 0
                AND issue_count = 0
                AND started_at IS NULL
                AND finished_at IS NULL
                AND failure_reason IS NULL
            )
        )
);

CREATE INDEX ix_validation_run_dataset_id
    ON validation_run (dataset_id);

CREATE INDEX ix_validation_run_source_file_id
    ON validation_run (source_file_id);

CREATE INDEX ix_validation_run_profile_id
    ON validation_run (profile_id);
