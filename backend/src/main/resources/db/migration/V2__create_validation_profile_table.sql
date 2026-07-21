CREATE TABLE validation_profile (
    id uuid NOT NULL,
    dataset_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_validation_profile PRIMARY KEY (id),
    CONSTRAINT fk_validation_profile_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES dataset (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_validation_profile_name_not_blank
        CHECK (char_length(btrim(name)) > 0)
);

CREATE INDEX ix_validation_profile_dataset_created_at_id
    ON validation_profile (dataset_id, created_at, id);
