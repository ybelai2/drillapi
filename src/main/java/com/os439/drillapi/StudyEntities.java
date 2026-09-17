package com.os439.drillapi;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity @Table(name="accounts")
class Account {
    @Id UUID id;
    @Column(nullable=false, unique=true, length=254) String email;
    @Column(nullable=false, length=100) String passwordHash;
    @Column(nullable=false, length=80) String name;
}
interface Accounts extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
}
@Entity @Table(name="auth_sessions")
class AuthSession {
    @Id @Column(length=64) String tokenHash;
    @Column(nullable=false) UUID accountId;
    @Column(nullable=false) Instant expiresAt;
}
interface AuthSessions extends JpaRepository<AuthSession, String> {}
@Entity @Table(name="courses")
class Course {
    @Id public UUID id;
    @Column(nullable=false) UUID ownerId;
    @Column(nullable=false, length=160) public String title;
    @Column(nullable=false, length=40) public String code;
    @Column(nullable=false, length=80) public String semester;
    @Column(nullable=false, length=2000) public String description;
}
interface Courses extends JpaRepository<Course, UUID> {
    List<Course> findByOwnerIdOrderByTitleAsc(UUID ownerId);
    Optional<Course> findByIdAndOwnerId(UUID id, UUID ownerId);
}
@Entity @Table(name="study_classes")
class StudyClass {
    @Id public UUID id;
    @Column(nullable=false) public UUID courseId;
    @Column(nullable=false, length=160) public String title;
    @Column(nullable=false, length=10000) public String notes;
    @Column(nullable=false) public boolean studied;
}
interface StudyClasses extends JpaRepository<StudyClass, UUID> {
    List<StudyClass> findByCourseIdOrderByTitleAsc(UUID courseId);
    Optional<StudyClass> findByIdAndCourseId(UUID id, UUID courseId);
}
@Entity @Table(name="decks")
class Deck {
    @Id public UUID id;
    @Column(nullable=false) public UUID classId;
    @Column(nullable=false, length=160) public String title;
    @Column(nullable=false, columnDefinition="text") public String content;
}
interface Decks extends JpaRepository<Deck, UUID> {
    List<Deck> findByClassIdOrderByTitleAsc(UUID classId);
    Optional<Deck> findByIdAndClassId(UUID id, UUID classId);
}
