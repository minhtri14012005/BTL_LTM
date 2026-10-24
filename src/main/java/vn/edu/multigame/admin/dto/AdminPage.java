package vn.edu.multigame.admin.dto;

import java.util.List;
import vn.edu.multigame.user.enums.UserRole;

public record AdminPage<T>(List<T> items,int page,int size,long totalElements) {
    public record User(long id,String username,String displayName,UserRole role,long createdAtMs,Long deletedAtMs,String banKind,Long bannedUntilMs,long banVersion,boolean banned,long serverTimeMs,String maSinhVien,String status) {}
}
