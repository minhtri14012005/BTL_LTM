import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Test-only crash/ack-loss injection, hard-scoped to quizz_task2_test and this runner's account prefixes.
 * Run with JDK source-file mode and the project's cached MySQL JDBC jar; never used by the application. */
class BanRestartFixture {
    static String setting(Properties config,String key,String fallback){return System.getenv().getOrDefault(key,config.getProperty(key,fallback));}
    public static void main(String[] args)throws Exception{
        if(args.length!=4 || !Set.of("prepare","verify","hold-game","verify-pending").contains(args[0]))throw new IllegalArgumentException("prepare|verify|hold-game|verify-pending actionId|gameId userId roomId");
        long action=Long.parseLong(args[1]),user=Long.parseLong(args[2]),room=Long.parseLong(args[3]);
        var config=new Properties();try(var file=Files.newBufferedReader(Path.of("config/application-local.properties"))){config.load(file);}
        String url="jdbc:mysql://"+setting(config,"DB_HOST","127.0.0.1")+":"+setting(config,"DB_PORT","3306")+"/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        try(var db=DriverManager.getConnection(url,setting(config,"DB_USER","quiz_app"),setting(config,"DB_PASSWORD",""))){
            try(var query=db.createStatement();var rows=query.executeQuery("select database()")){rows.next();if(!"quizz_task2_test".equals(rows.getString(1)))throw new IllegalStateException("Test DB guard");}
            if(args[0].equals("hold-game")){
                try(var q=db.prepareStatement("select count(*) from game_session g join game_member m on m.game_session_id=g.id join app_user u on u.id=m.user_id where g.id=? and g.room_id=? and g.status='ACTIVE' and u.id=? and u.username like 'ban\\_%'")){
                    q.setLong(1,action);q.setLong(2,room);q.setLong(3,user);try(var rows=q.executeQuery()){rows.next();if(rows.getInt(1)!=1)throw new IllegalStateException("Owned ACTIVE Game guard");}
                }
                db.setAutoCommit(false);
                try(var q=db.prepareStatement("select id from room where id=? for update")){q.setLong(1,room);try(var rows=q.executeQuery()){if(!rows.next())throw new IllegalStateException("Missing Room");}}
                try(var q=db.prepareStatement("select id from game_session where id=? for update")){q.setLong(1,action);try(var rows=q.executeQuery()){if(!rows.next())throw new IllegalStateException("Missing Game");}}
                Path release=Path.of("target/t09-hold-game-"+action+".release"),ready=Path.of("target/t09-hold-game-"+action+".ready");Files.writeString(ready,"Room/Game held; no User locks");System.out.println("HOLD_READY "+ready);System.out.flush();
                long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
                while(!Files.exists(release)){if(System.nanoTime()>deadline)throw new IllegalStateException("Owned hold timeout");Thread.sleep(100);}
                db.rollback();System.out.println("Released test Room/Game lock without changing data.");return;
            }
            try(var query=db.prepareStatement("select u.username,a.action,actor.username from admin_account_action a join app_user u on u.id=a.target_user_id join app_user actor on actor.id=a.admin_user_id where a.id=? and a.target_user_id=?")){
                query.setLong(1,action);query.setLong(2,user);try(var rows=query.executeQuery()){
                    if(!rows.next() || !rows.getString(1).startsWith("ban_") || !rows.getString(3).startsWith("banadm_") || rows.getString(2).equals("UNBAN"))throw new IllegalStateException("Owned ban action guard");
                }
            }
            try(var query=db.prepareStatement("select m.membership_generation,m.status,r.status from room_member m join room r on r.id=m.room_id where m.user_id=? and m.room_id=?")){
                query.setLong(1,user);query.setLong(2,room);try(var rows=query.executeQuery()){
                    if(!rows.next())throw new IllegalStateException("Missing membership");
                    if(args[0].equals("verify-pending")){
                        if(rows.getLong(1)!=1 || !rows.getString(2).equals("LEFT") || !rows.getString(3).equals("WAITING"))throw new IllegalStateException("Pending g1 cleanup after restart guard");
                    }else if(rows.getLong(1)!=2 || !rows.getString(2).equals("JOINED") || !rows.getString(3).equals("WAITING"))throw new IllegalStateException("Legal g2/WAITING setup guard");
                }
            }
            if(args[0].equals("prepare")){
                // Simulate lost acknowledgement of already committed effects. Immutable targets/receipts remain intact.
                try(var update=db.prepareStatement("update account_effect_intent set state='PENDING',completed_at_ms=null,retry_after_ms=0 where action_id=? and user_id=?")){
                    update.setLong(1,action);update.setLong(2,user);if(update.executeUpdate()<3)throw new IllegalStateException("Expected original Game/membership/session effects");
                }
                System.out.println("Prepared original intent replay; immutable target IDs retained, no occupancy or membership altered.");
            }else{
                try(var query=db.prepareStatement("select count(*) from account_effect_intent where action_id=? and user_id=? and state<>'DONE'")){
                    query.setLong(1,action);query.setLong(2,user);try(var rows=query.executeQuery()){rows.next();if(rows.getLong(1)!=0)throw new IllegalStateException("Reconciliation pending");}
                }
                if(args[0].equals("verify-pending")){
                    try(var q=db.prepareStatement("select count(*) from user_active_game where user_id=?")){q.setLong(1,user);try(var rows=q.executeQuery()){rows.next();if(rows.getInt(1)!=0)throw new IllegalStateException("Old occupancy not released");}}
                    System.out.println("Genuine pending ban reconciled after restart: intents DONE, g1 LEFT, occupancy released.");
                }else System.out.println("Restart reconciliation DONE; g2 JOINED and WAITING preserved.");
            }
        }
    }
}
