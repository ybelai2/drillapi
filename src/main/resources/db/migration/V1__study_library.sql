CREATE TABLE accounts (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 name VARCHAR(80) NOT NULL
);
CREATE TABLE auth_sessions (
 token_hash VARCHAR(64) PRIMARY KEY, account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE courses (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id),
 title VARCHAR(160) NOT NULL, code VARCHAR(40) NOT NULL, semester VARCHAR(80) NOT NULL,
 description VARCHAR(2000) NOT NULL
);
CREATE INDEX courses_owner ON courses(owner_id);
CREATE TABLE study_classes (
 id UUID PRIMARY KEY, course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
 title VARCHAR(160) NOT NULL, notes VARCHAR(10000) NOT NULL, studied BOOLEAN NOT NULL
);
CREATE INDEX classes_course ON study_classes(course_id);
CREATE TABLE decks (
 id UUID PRIMARY KEY, class_id UUID NOT NULL REFERENCES study_classes(id) ON DELETE CASCADE,
 title VARCHAR(160) NOT NULL, content TEXT NOT NULL
);
CREATE INDEX decks_class ON decks(class_id);
