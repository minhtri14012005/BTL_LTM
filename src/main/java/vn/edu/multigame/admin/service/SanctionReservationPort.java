package vn.edu.multigame.admin.service;

import java.util.List;

/** T15 extension: snapshot while target User is locked; release exact ID/version after commit.
 * Implementations must also fence ACCEPT/START on that User. No friendly system exists in T09. */
public interface SanctionReservationPort {
    record Target(String reservationId,long version) {}
    List<Target> snapshot(long userId);
    void release(String reservationId,long version,long actionId);
}
