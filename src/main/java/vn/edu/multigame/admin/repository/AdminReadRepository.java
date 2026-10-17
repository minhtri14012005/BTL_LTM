package vn.edu.multigame.admin.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.multigame.admin.dto.AdminPage;
import vn.edu.multigame.user.enums.UserRole;

/** Explicit safe projection: never select credentials, session IDs, Room codes or answer payloads. */
@Repository @Profile("mysql")
public class AdminReadRepository {
    private final JdbcTemplate jdbc;
    public AdminReadRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public java.util.List<AdminPage.KickTarget> players(long game){
        return jdbc.query("select m.user_id,m.display_name_snapshot,m.role,m.participation,u.role as account_role,((r.user_id is not null) or exists(select 1 from account_effect_intent i where i.game_session_id=m.game_session_id and i.user_id=m.user_id and i.effect_type='GAME')) as restricted from game_member m join game_session g on g.id=m.game_session_id join app_user u on u.id=m.user_id left join game_participation_restriction r on r.game_session_id=m.game_session_id and r.user_id=m.user_id where m.game_session_id=? and g.status='ACTIVE' order by m.user_id",
                (r,n)->new AdminPage.KickTarget(r.getLong("user_id"),r.getString("display_name_snapshot"),r.getString("role"),r.getString("participation"),UserRole.valueOf(r.getString("account_role")),r.getBoolean("restricted")),game);
    }
    public AdminPage<AdminPage.User> users(int page,int size) {
        var rows=jdbc.query("select id,username,display_name,role,created_at_ms,deleted_at_ms,ban_kind,banned_until_ms,ban_version from app_user order by id desc limit ? offset ?",
                (r,n)->new AdminPage.User(r.getLong("id"),r.getString("username"),r.getString("display_name"),UserRole.valueOf(r.getString("role")),r.getLong("created_at_ms"),r.getObject("deleted_at_ms",Long.class),r.getString("ban_kind"),r.getObject("banned_until_ms",Long.class),r.getLong("ban_version"),r.getString("ban_kind").equals("INDEFINITE") || r.getString("ban_kind").equals("TEMP") && System.currentTimeMillis()<r.getLong("banned_until_ms"),System.currentTimeMillis()),size,page*size);
        return new AdminPage<>(rows,page,size,jdbc.queryForObject("select count(*) from app_user",Long.class));
    }
    public AdminPage<AdminPage.Game> activeGames(int page,int size) {
        var rows=jdbc.query("select g.id,g.room_id,r.host_user_id,g.quiz_title_snapshot,g.phase,g.current_question_index,g.question_count,g.schema_version,g.started_at_ms from game_session g join room r on r.id=g.room_id where g.status='ACTIVE' order by g.started_at_ms desc,g.id desc limit ? offset ?",
                (r,n)->new AdminPage.Game(r.getLong("id"),r.getLong("room_id"),r.getLong("host_user_id"),r.getString("quiz_title_snapshot"),r.getString("phase"),r.getInt("current_question_index"),r.getInt("question_count"),r.getInt("schema_version"),r.getLong("started_at_ms")),size,page*size);
        return new AdminPage<>(rows,page,size,jdbc.queryForObject("select count(*) from game_session where status='ACTIVE'",Long.class));
    }
    public AdminPage<AdminPage.OrphanRoom> orphanRooms(int page,int size) {
        String from=" from room r left join app_user u on u.id=r.host_user_id where r.status='WAITING' and (u.id is null or u.deleted_at_ms is not null or u.ban_kind='INDEFINITE' or (u.ban_kind='TEMP' and u.banned_until_ms>"+System.currentTimeMillis()+")) and not exists (select 1 from game_session g where g.room_id=r.id and g.status='ACTIVE')";
        var rows=jdbc.query("select r.id,r.host_user_id,r.name,r.status,r.created_at_ms,r.revision,case when u.id is null then 'HOST_MISSING' when u.deleted_at_ms is not null then 'HOST_DELETED' else 'HOST_BANNED' end as reason"+from+" order by r.created_at_ms,r.id limit ? offset ?",
                (r,n)->new AdminPage.OrphanRoom(r.getLong("id"),r.getLong("host_user_id"),r.getString("name"),r.getString("status"),r.getString("reason"),r.getLong("created_at_ms"),r.getLong("revision")),size,page*size);
        return new AdminPage<>(rows,page,size,jdbc.queryForObject("select count(*)"+from,Long.class));
    }
}
