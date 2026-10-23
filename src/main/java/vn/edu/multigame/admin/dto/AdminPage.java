package vn.edu.multigame.admin.dto;

import java.util.List;
import vn.edu.multigame.user.enums.UserRole;

public record AdminPage<T>(List<T> items,int page,int size,long totalElements) {
    public record User(long id,String username,String displayName,UserRole role,long createdAtMs,Long deletedAtMs,String banKind,Long bannedUntilMs,long banVersion,boolean banned,long serverTimeMs,String maSinhVien,String status) {}
    public record Game(long id,long roomId,long hostUserId,String title,String phase,int questionIndex,int questionCount,int schemaVersion,long startedAtMs) {}
    public record KickTarget(long userId,String displayName,String role,String participation,UserRole accountRole,boolean restricted) {}
    public record OrphanRoom(long id,long hostUserId,String name,String status,String reason,long createdAtMs,long revision) {}
}
