-- MySQL
ALTER TABLE sys_user
    ADD COLUMN enterprise_code_first_digit_scope VARCHAR(1) NULL AFTER enterprise_id;

ALTER TABLE submission_form
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER document_no;

-- DM8
ALTER TABLE sys_user
    ADD enterprise_code_first_digit_scope VARCHAR(1);

ALTER TABLE submission_form
    ADD version BIGINT DEFAULT 0 NOT NULL;
