package vn.edu.multigame.admin.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.admin.dto.AdminPage;
import vn.edu.multigame.user.enums.UserRole;

/**
 * Explicit safe projection: never select credentials, session IDs, Room codes
 * or answer payloads.
 */
@Repository
@Profile("mysql")
public class AdminReadRepository {
    private final JdbcTemplate jdbc;

    public AdminReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    public AdminPage<AdminPage.User> users(int page,int size,UserRole role,String query,String status) {
        var parameters=new java.util.ArrayList<Object>();
        String where=role==null?" where 1=1":" where deleted_at_ms is null";
        if(role!=null){where+=" and role=?";parameters.add(role.name());}
        if(status!=null){where+=" and account_status=?";parameters.add(status);}
        if(!query.isEmpty()) {
            where+=" and (username like ? escape '!' or display_name like ? escape '!' or ma_sinh_vien like ? escape '!')";
            String pattern="%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            parameters.add(pattern);parameters.add(pattern);parameters.add(pattern);
        }
        long total=jdbc.queryForObject("select count(*) from app_user"+where,Long.class,parameters.toArray());
        parameters.add(size);parameters.add(page*size);
        var rows = jdbc.query(
                "select id,username,display_name,role,created_at_ms,deleted_at_ms,ban_kind,banned_until_ms,ban_version,ma_sinh_vien,account_status from app_user"+where+" order by id desc limit ? offset ?",
                (r, n) -> new AdminPage.User(r.getLong("id"), r.getString("username"), r.getString("display_name"),
                        UserRole.valueOf(r.getString("role")), r.getLong("created_at_ms"),
                        r.getObject("deleted_at_ms", Long.class), r.getString("ban_kind"),
                        r.getObject("banned_until_ms", Long.class), r.getLong("ban_version"),
                        r.getString("ban_kind").equals("INDEFINITE") || r.getString("ban_kind").equals("TEMP")
                                && System.currentTimeMillis() < r.getLong("banned_until_ms"),
                        System.currentTimeMillis(),r.getString("ma_sinh_vien"),r.getString("account_status")),
                parameters.toArray());
        return new AdminPage<>(rows, page, size,total);
    }

}
