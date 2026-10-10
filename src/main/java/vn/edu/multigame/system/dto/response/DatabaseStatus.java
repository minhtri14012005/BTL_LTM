package vn.edu.multigame.system.dto.response;

public record DatabaseStatus(String status, String message) {
    public boolean ready() {
        return "UP".equals(status);
    }
}
