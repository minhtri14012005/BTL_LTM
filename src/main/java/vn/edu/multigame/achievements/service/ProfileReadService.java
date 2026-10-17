package vn.edu.multigame.achievements.service;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.multigame.achievements.dto.PlayerProfileView;
import vn.edu.multigame.achievements.repository.ProfileRepository;
import vn.edu.multigame.game.service.GameFailure;

/** Every multi-query response observes a single committed MySQL snapshot. */
@Service @Profile("mysql")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class ProfileReadService {
    private final ProfileRepository profiles;
    private final JdbcTemplate jdbc;
    public ProfileReadService(ProfileRepository profiles,JdbcTemplate jdbc) { this.profiles=profiles;this.jdbc=jdbc; }
    public PlayerProfileView own(long userId) {
        var names=jdbc.query("select display_name from app_user where id=? and deleted_at_ms is null",(r,n)->r.getString(1),userId);
        if(names.isEmpty())throw new GameFailure(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND");
        return new PlayerProfileView(userId,names.getFirst(),profiles.stats(userId),profiles.badges(userId));
    }
    public PlayerProfileView.AwardPage ownAwards(long userId,int page,int size) {
        validatePage(page,size);return profiles.awards(userId,page,size);
    }
    public PlayerProfileView.PublicProfile publicProfile(long userId) {
        var names=jdbc.query("select display_name from app_user u where id=? and "+PUBLIC_ACCOUNT,
                (r,n)->r.getString(1),userId,System.currentTimeMillis());
        if(names.isEmpty())throw new GameFailure(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND");
        return new PlayerProfileView.PublicProfile(userId,names.getFirst(),profiles.stats(userId));
    }
    /** A15: visibility at one Server-time sample in the response's committed snapshot. */
    public static final String PUBLIC_ACCOUNT = "u.deleted_at_ms is null and (u.ban_kind='NONE' or (u.ban_kind='TEMP' and u.banned_until_ms<=?))";
    public static void validatePage(int page,int size) {
        if(page<0 || page>1000000 || size<1 || size>50)throw new GameFailure(HttpStatus.BAD_REQUEST,"INVALID_PAGE");
    }
}
