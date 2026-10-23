package vn.edu.multigame.lesson.domain;

public record LessonState(long id,long courseClassId,int number,String title,boolean visible,String status,
        long revision,String createFingerprint) {}
