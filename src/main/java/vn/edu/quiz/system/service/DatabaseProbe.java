package vn.edu.quiz.system.service;

import vn.edu.quiz.system.dto.response.DatabaseStatus;

public interface DatabaseProbe {
    DatabaseStatus check();
}
