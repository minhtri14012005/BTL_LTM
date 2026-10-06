package vn.edu.quiz.system.dto.response;

/** Public system status response; fields preserved from SystemController.SystemStatus. */
public record SystemStatus(String application, String server, DatabaseStatus database,
        long serverTimeMs, String gameplay) {
}

