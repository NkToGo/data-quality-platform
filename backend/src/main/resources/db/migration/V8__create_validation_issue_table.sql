CREATE TABLE validation_issue (
    id uuid NOT NULL,
    run_id uuid NOT NULL,
    row_number bigint NOT NULL,
    field_name varchar(255) NOT NULL,
    rule_type varchar(32) NOT NULL,
    severity varchar(16) NOT NULL,
    message varchar(500) NOT NULL,
    observed_value text,
    CONSTRAINT pk_validation_issue PRIMARY KEY (id),
    CONSTRAINT fk_validation_issue_validation_run
        FOREIGN KEY (run_id)
        REFERENCES validation_run (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_validation_issue_row_number
        CHECK (row_number >= 2),
    CONSTRAINT ck_validation_issue_field_name_not_blank
        CHECK (field_name ~ '[^[:space:]]'),
    CONSTRAINT ck_validation_issue_rule_type
        CHECK (rule_type IN (
            'REQUIRED_FIELD',
            'DATA_TYPE',
            'UNIQUENESS',
            'NUMERIC_RANGE',
            'DATE_FORMAT'
        )),
    CONSTRAINT ck_validation_issue_severity
        CHECK (severity IN ('ERROR', 'WARNING')),
    CONSTRAINT ck_validation_issue_message_not_blank
        CHECK (message ~ '[^[:space:]]')
);

CREATE INDEX ix_validation_issue_run_row_field_type_id
    ON validation_issue (
        run_id,
        row_number,
        field_name,
        rule_type,
        id
    );
