package vn.edu.multigame.system.service;

import vn.edu.multigame.system.dto.response.DatabaseStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class JdbcDatabaseProbe implements DatabaseProbe {
    private final DataSource dataSource;

    public JdbcDatabaseProbe(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DatabaseStatus check() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(3);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                if (result.next() && result.getInt(1) == 1) {
                    return new DatabaseStatus("UP", "Đã truy vấn MySQL thành công.");
                }
            }
        } catch (SQLException ignored) {
            // Credentials/host/SQL details must not be sent to public clients.
            return new DatabaseStatus("DOWN", "Chưa truy vấn được MySQL. Kiểm tra cấu hình kết nối trên Server.");
        }
        return new DatabaseStatus("DOWN", "MySQL chưa trả kết quả kiểm tra hợp lệ.");
    }
}
