CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role ENUM('ADMIN', 'STUDENT', 'TEACHER') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE exams (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    deadline DATETIME(6) NOT NULL,
    description VARCHAR(2000) NULL,
    duration_minutes INT NULL,
    start_time DATETIME(6) NOT NULL,
    status ENUM('CLOSED', 'DRAFT', 'PUBLISHED') NOT NULL,
    title VARCHAR(200) NOT NULL,
    type ENUM('ESSAY', 'MULTIPLE_CHOICE') NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exams_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content VARCHAR(2000) NOT NULL,
    points DOUBLE NOT NULL,
    exam_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_exam FOREIGN KEY (exam_id) REFERENCES exams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE answer_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content VARCHAR(1000) NOT NULL,
    correct BIT(1) NOT NULL,
    question_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_answer_options_question FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE essay_assignment_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_size BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    stored_file_name VARCHAR(100) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    exam_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_stored_file (stored_file_name),
    UNIQUE KEY uk_assignment_exam (exam_id),
    CONSTRAINT fk_assignment_exam FOREIGN KEY (exam_id) REFERENCES exams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE essay_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    feedback VARCHAR(2000) NULL,
    file_size BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    score DOUBLE NULL,
    storage_path VARCHAR(1000) NOT NULL,
    stored_file_name VARCHAR(100) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_essay_submission_exam_student (exam_id, student_id),
    UNIQUE KEY uk_essay_submission_stored_file (stored_file_name),
    CONSTRAINT fk_essay_submission_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_essay_submission_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expires_at DATETIME(6) NOT NULL,
    score DOUBLE NULL,
    started_at DATETIME(6) NOT NULL,
    status ENUM('AUTO_SUBMITTED', 'IN_PROGRESS', 'SUBMITTED') NOT NULL,
    submitted_at DATETIME(6) NULL,
    total_points DOUBLE NULL,
    version BIGINT NULL,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quiz_submission_exam_student (exam_id, student_id),
    CONSTRAINT fk_quiz_submission_exam FOREIGN KEY (exam_id) REFERENCES exams (id),
    CONSTRAINT fk_quiz_submission_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quiz_submission_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    awarded_points DOUBLE NOT NULL,
    correct BIT(1) NOT NULL,
    question_id BIGINT NOT NULL,
    selected_option_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quiz_answer_submission_question (submission_id, question_id),
    CONSTRAINT fk_quiz_answer_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT fk_quiz_answer_option FOREIGN KEY (selected_option_id) REFERENCES answer_options (id),
    CONSTRAINT fk_quiz_answer_submission FOREIGN KEY (submission_id) REFERENCES quiz_submissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
