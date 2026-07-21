CREATE TABLE validation_rule (
    id uuid NOT NULL,
    profile_id uuid NOT NULL,
    field_name varchar(255) NOT NULL,
    rule_type varchar(32) NOT NULL,
    parameters_json jsonb NOT NULL,
    severity varchar(16) NOT NULL,
    enabled boolean NOT NULL,
    CONSTRAINT pk_validation_rule PRIMARY KEY (id),
    CONSTRAINT fk_validation_rule_profile
        FOREIGN KEY (profile_id)
        REFERENCES validation_profile (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_validation_rule_field_name_not_blank
        CHECK (char_length(btrim(field_name)) > 0),
    CONSTRAINT ck_validation_rule_type
        CHECK (rule_type IN (
            'REQUIRED_FIELD',
            'DATA_TYPE',
            'UNIQUENESS',
            'NUMERIC_RANGE',
            'DATE_FORMAT'
        )),
    CONSTRAINT ck_validation_rule_parameters_object
        CHECK (jsonb_typeof(parameters_json) = 'object'),
    CONSTRAINT ck_validation_rule_severity
        CHECK (severity IN ('ERROR', 'WARNING'))
);

CREATE INDEX ix_validation_rule_profile_id_id
    ON validation_rule (profile_id, id);
