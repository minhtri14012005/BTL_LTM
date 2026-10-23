package vn.edu.multigame.lesson.service;

/** Payload-free notification; each recipient is authorized again after commit. */
public record LessonChanged(long lessonId) {}
