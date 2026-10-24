package vn.edu.multigame.lesson.dto;

import java.util.List;
import vn.edu.multigame.questionbank.enums.GameMode;
import vn.edu.multigame.questionbank.dto.response.QuestionResponse;

/** Teacher-only snapshot. Student/event DTOs must use StudentQuestion's explicit allowlist. */
public record GameContentView(long gameId,long sourceQuizId,long sourceRevision,long revision,
        GameMode mode,String title,List<QuestionResponse> questions,long subjectId,String subjectName) {}
