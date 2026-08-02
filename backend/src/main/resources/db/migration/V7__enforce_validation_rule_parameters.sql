ALTER TABLE validation_rule
    ADD CONSTRAINT ck_validation_rule_required_field_parameters
        CHECK (
            rule_type <> 'REQUIRED_FIELD'
            OR parameters_json = '{}'::jsonb
        ),
    ADD CONSTRAINT ck_validation_rule_uniqueness_parameters
        CHECK (
            rule_type <> 'UNIQUENESS'
            OR parameters_json = '{}'::jsonb
        ),
    ADD CONSTRAINT ck_validation_rule_data_type_parameters
        CHECK (
            rule_type <> 'DATA_TYPE'
            OR CASE
                WHEN jsonb_typeof(parameters_json) IS DISTINCT FROM 'object'
                    THEN FALSE
                WHEN parameters_json - 'type' <> '{}'::jsonb
                    THEN FALSE
                WHEN NOT (parameters_json ? 'type')
                    THEN FALSE
                WHEN jsonb_typeof(parameters_json -> 'type') IS DISTINCT FROM 'string'
                    THEN FALSE
                ELSE parameters_json ->> 'type' IN (
                    'INTEGER',
                    'DECIMAL',
                    'BOOLEAN',
                    'STRING'
                )
            END
        ),
    ADD CONSTRAINT ck_validation_rule_numeric_range_parameters
        CHECK (
            rule_type <> 'NUMERIC_RANGE'
            OR CASE
                WHEN jsonb_typeof(parameters_json) IS DISTINCT FROM 'object'
                    THEN FALSE
                WHEN parameters_json - 'minimum' - 'maximum' <> '{}'::jsonb
                    THEN FALSE
                WHEN NOT (
                    parameters_json ? 'minimum'
                    OR parameters_json ? 'maximum'
                )
                    THEN FALSE
                WHEN (
                    parameters_json ? 'minimum'
                    AND jsonb_typeof(parameters_json -> 'minimum')
                        IS DISTINCT FROM 'number'
                )
                    THEN FALSE
                WHEN (
                    parameters_json ? 'maximum'
                    AND jsonb_typeof(parameters_json -> 'maximum')
                        IS DISTINCT FROM 'number'
                )
                    THEN FALSE
                WHEN (
                    parameters_json ? 'minimum'
                    AND parameters_json ? 'maximum'
                )
                    THEN (parameters_json ->> 'minimum')::numeric
                        <= (parameters_json ->> 'maximum')::numeric
                ELSE TRUE
            END
        ),
    ADD CONSTRAINT ck_validation_rule_date_format_parameters
        CHECK (
            rule_type <> 'DATE_FORMAT'
            OR CASE
                WHEN jsonb_typeof(parameters_json) IS DISTINCT FROM 'object'
                    THEN FALSE
                WHEN parameters_json - 'format' <> '{}'::jsonb
                    THEN FALSE
                WHEN NOT (parameters_json ? 'format')
                    THEN FALSE
                WHEN jsonb_typeof(parameters_json -> 'format') IS DISTINCT FROM 'string'
                    THEN FALSE
                ELSE parameters_json ->> 'format' IN (
                    'ISO_DATE',
                    'DAY_MONTH_YEAR',
                    'MONTH_DAY_YEAR'
                )
            END
        );
