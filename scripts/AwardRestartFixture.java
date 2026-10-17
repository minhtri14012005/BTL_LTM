import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Own test DB only. Acquires a User lock AFTER result commit, delaying award, never delaying Game finish. */
class AwardRestartFixture {
    static String value(Properties c,String k,String d){return System.getenv().getOrDefault(k,c.getProperty(k,d));}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("gameId userId");long game=Long.parseLong(args[0]),user=Long.parseLong(args[1]);
        var config=new Properties();try(var f=Files.newBufferedReader(Path.of("config/application-local.properties"))){config.load(f);}
        String url="jdbc:mysql://"+value(config,"DB_HOST","127.0.0.1")+":"+value(config,"DB_PORT","3306")+"/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000";
        try(var db=DriverManager.getConnection(url,value(config,"DB_USER","quiz_app"),value(config,"DB_PASSWORD",""))){
            try(var q=db.createStatement();var r=q.executeQuery("select database()")){r.next();if(!r.getString(1).equals("quizz_task2_test"))throw new IllegalStateException("Test DB guard");}
            try(var q=db.prepareStatement("select count(*) from game_member m join app_user u on u.id=m.user_id join game_session g on g.id=m.game_session_id where m.game_session_id=? and m.user_id=? and u.username like 'award\\_%' and g.schema_version=2 and g.game_type='NORMAL' and g.status='ACTIVE'")){q.setLong(1,game);q.setLong(2,user);try(var r=q.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Owned ACTIVE Game guard");}}
            Path ready=Path.of("target/t11-award-hold-"+game+".ready"),held=Path.of("target/t11-award-hold-"+game+".held"),release=Path.of("target/t11-award-hold-"+game+".release");
            Files.writeString(ready,"Monitoring owned result; no User lock yet");long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
            while(true){
                try(var q=db.prepareStatement("select status from game_session where id=?")){q.setLong(1,game);try(var r=q.executeQuery()){r.next();if(r.getString(1).equals("FINISHED"))break;}}
                if(System.nanoTime()>deadline)throw new IllegalStateException("Result timeout");Thread.sleep(20);
            }
            db.setAutoCommit(false);
            try(var q=db.prepareStatement("select id from app_user where id=? for update")){q.setLong(1,user);q.executeQuery().close();}
            try(var q=db.prepareStatement("select d.state,g.status from game_award_decision d join game_session g on g.id=d.game_session_id where d.game_session_id=?")){q.setLong(1,game);try(var r=q.executeQuery()){if(!r.next()||!r.getString(1).equals("PENDING")||!r.getString(2).equals("FINISHED"))throw new IllegalStateException("Award already committed; do not fake pending");}}
            Files.writeString(held,"Genuine FINISHED + PENDING; User lock holds finalizer before award commit");System.out.println("GENUINE_PENDING_READY game="+game);System.out.flush();
            while(!Files.exists(release)){if(System.nanoTime()>deadline)throw new IllegalStateException("Owned hold timeout");Thread.sleep(100);}
            db.rollback();System.out.println("Released owned User lock without changing any DB data.");
        }
    }
}
