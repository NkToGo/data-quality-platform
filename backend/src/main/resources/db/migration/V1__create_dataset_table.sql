CREATE TABLE dataset (
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    description varchar(2000),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_dataset PRIMARY KEY (id),
    CONSTRAINT ck_dataset_name_not_blank
        CHECK (char_length(btrim(name)) > 0)
);
