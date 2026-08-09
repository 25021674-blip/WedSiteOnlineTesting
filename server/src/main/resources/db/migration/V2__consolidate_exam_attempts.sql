DROP TABLE IF EXISTS quiz_submission_answers;
DROP TABLE IF EXISTS quiz_submissions;

ALTER TABLE exams
    ALGORITHM=INPLACE,
    CHANGE COLUMN created_by teacher_id BIGINT NOT NULL;

ALTER TABLE exams
    CHANGE COLUMN start_time start_at DATETIME(6) NOT NULL,
    CHANGE COLUMN deadline expires_at DATETIME(6) NOT NULL,
    ADD COLUMN max_score DECIMAL(10, 2) NOT NULL DEFAULT 10.00 AFTER duration_minutes,
    MODIFY COLUMN title VARCHAR(255) NOT NULL,
    MODIFY COLUMN description TEXT NULL,
    MODIFY COLUMN duration_minutes INT NULL,
    MODIFY COLUMN type ENUM('ESSAY', 'MIXED', 'MULTIPLE_CHOICE') NOT NULL,
    ADD CONSTRAINT chk_exam_duration_by_type CHECK (
        (type = 'ESSAY' AND duration_minutes IS NULL)
        OR (type <> 'ESSAY' AND duration_minutes > 0)
    );

ALTER TABLE questions
    CHANGE COLUMN points max_score DECIMAL(5, 2) NOT NULL,
    ADD COLUMN question_type ENUM('ESSAY', 'MULTIPLE_CHOICE') NOT NULL DEFAULT 'MULTIPLE_CHOICE',
    ADD COLUMN question_order INT NULL;

UPDATE questions question_row
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY exam_id ORDER BY id) AS generated_order
    FROM questions
) ranked_questions ON ranked_questions.id = question_row.id
SET question_row.question_order = ranked_questions.generated_order;

ALTER TABLE questions
    MODIFY COLUMN question_order INT NOT NULL,
    ADD CONSTRAINT uk_question_exam_order UNIQUE (exam_id, question_order);

RENAME TABLE answer_options TO question_options;

ALTER TABLE question_options
    CHANGE COLUMN correct is_correct BIT(1) NOT NULL,
    MODIFY COLUMN content TEXT NOT NULL,
    ADD COLUMN option_order INT NULL;

UPDATE question_options option_row
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY question_id ORDER BY id) AS generated_order
    FROM question_options
) ranked_options ON ranked_options.id = option_row.id
SET option_row.option_order = ranked_options.generated_order;

ALTER TABLE question_options
    MODIFY COLUMN option_order INT NOT NULL,
    ADD CONSTRAINT uk_option_question_order UNIQUE (question_id, option_order);

CREATE TABLE exam_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NULL,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    deadline_at DATETIME(6) NOT NULL,
    last_heartbeat_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    status ENUM('AUTO_SUBMITTED', 'IN_PROGRESS', 'SUBMITTED') NOT NULL,
    screen_exit_count INT NOT NULL,
    score DECIMAL(5, 2) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attempt_exam_student UNIQUE (exam_id, student_id),
    CONSTRAINT fk_attempt_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_attempt_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NULL,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_option_id BIGINT NULL,
    essay_answer TEXT NULL,
    updated_at DATETIME(6) NOT NULL,
    client_revision BIGINT NOT NULL,
    score DECIMAL(5, 2) NULL,
    is_correct BIT(1) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_answer_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_student_answer_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts (id),
    CONSTRAINT fk_student_answer_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_student_answer_option FOREIGN KEY (selected_option_id) REFERENCES question_options (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attempt_violations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    type ENUM('FULLSCREEN_EXIT', 'PAGE_LEAVE', 'TAB_HIDDEN', 'WINDOW_BLUR') NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    client_time DATETIME(6) NOT NULL,
    metadata TEXT NULL,
    PRIMARY KEY (id),
    INDEX idx_attempt_violation_attempt_occurred (attempt_id, occurred_at),
    CONSTRAINT fk_attempt_violation_attempt FOREIGN KEY (attempt_id) REFERENCES exam_attempts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
