package vn.edu.multigame.system.service;

import vn.edu.multigame.system.dto.response.DatabaseStatus;

public interface DatabaseProbe {
    DatabaseStatus check();
}
