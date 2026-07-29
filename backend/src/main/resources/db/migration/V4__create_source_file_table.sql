CREATE TABLE source_file (
    id uuid NOT NULL,
    dataset_id uuid NOT NULL,
    original_filename varchar(255) NOT NULL,
    content_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    content_bytes bytea NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_source_file PRIMARY KEY (id),
    CONSTRAINT fk_source_file_dataset
        FOREIGN KEY (dataset_id)
        REFERENCES dataset (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_source_file_original_filename_not_blank
        CHECK (char_length(btrim(original_filename)) > 0),
    CONSTRAINT ck_source_file_original_filename_basename
        CHECK (
            position('/' in original_filename) = 0
            AND position(chr(92) in original_filename) = 0
        ),
    CONSTRAINT ck_source_file_original_filename_csv
        CHECK (right(lower(original_filename), 4) = '.csv'),
    CONSTRAINT ck_source_file_content_type_not_blank
        CHECK (char_length(btrim(content_type)) > 0),
    CONSTRAINT ck_source_file_size_bytes_positive
        CHECK (size_bytes > 0),
    CONSTRAINT ck_source_file_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_source_file_content_size
        CHECK (octet_length(content_bytes)::bigint = size_bytes)
);

CREATE INDEX ix_source_file_dataset_id
    ON source_file (dataset_id);
