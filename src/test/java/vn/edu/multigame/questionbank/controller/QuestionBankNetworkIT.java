package vn.edu.multigame.questionbank.controller;

import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.multigame.auth.security.AuthSessionRegistry;
import vn.edu.multigame.game.dto.GameplayRulesSnapshot;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP/session/CSRF + MySQL + filesystem. History rows are synthetic, not a running lifecycle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000",
    "quiz.images.directory=./target/task4-test-images"
})
@ActiveProfiles("mysql")
class QuestionBankNetworkIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionRegistry sessions;
    List<Client> clients = new ArrayList<>();
    List<String> usernames = new ArrayList<>();
    List<Long> quizzes = new ArrayList<>(), games = new ArrayList<>(), rooms = new ArrayList<>();
    class Client {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        String csrf;
        long userId;
        HttpResponse<String> request(String method, String path, Object body, boolean token) throws Exception {
            return http.send(builder(method, path, body, token), HttpResponse.BodyHandlers.ofString());
        }
        HttpRequest builder(String method, String path, Object body, boolean token) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10));
            if (token && csrf != null) request.header("X-CSRF-TOKEN", csrf);
            if (body != null) request.header("Content-Type", "application/json");
            return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        }
        void csrf() throws Exception { csrf = json.readTree(request("GET", "/api/auth/csrf", null, false).body()).get("token").asText(); }
        HttpResponse<String> upload(long id, byte[] image) throws Exception { return upload("/api/quizzes/"+id+"/images",image); }
        HttpResponse<String> upload(String path, byte[] image) throws Exception {
            String boundary = "quiz-test-boundary";
            var body = new ByteArrayOutputStream();
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../untrusted.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(image); body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN", csrf)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<byte[]> image(String path) throws Exception { return http.send(builder("GET",path,null,false), HttpResponse.BodyHandlers.ofByteArray()); }
    }
    URI uri(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    Client login() throws Exception {
        Client client = new Client(); clients.add(client); client.csrf();
        String name = "quiz_"+UUID.randomUUID().toString().replace("-", "").substring(0,15); usernames.add(name);
        var register = client.request("POST", "/api/auth/register", Map.of("username",name,"displayName","Quiz Test","password","Test-quiz-42"), true);
        assertThat(register.statusCode()).as(register.body()).isEqualTo(201); client.userId=json.readTree(register.body()).get("id").asLong();
        assertThat(client.request("POST", "/api/auth/login", Map.of("username",name,"password","Test-quiz-42"), true).statusCode()).isEqualTo(200);
        client.csrf(); return client;
    }
    Map<String,Object> question(String text) { return new LinkedHashMap<>(Map.of("content",text,"options",Map.of("A","First","B","Second","C","Third","D","Fourth"),"correctAnswer","B")); }
    Map<String,Object> body(String visibility, String content) {
        return new LinkedHashMap<>(Map.of("title","Test Quiz","visibility",visibility,"questions",java.util.stream.IntStream.range(0,10).mapToObj(i->question(content+" "+i)).toList()));
    }
    JsonNode create(Client owner, String visibility) throws Exception {
        var request=body(visibility,"Original"); request.put("ownerUserId",999999);
        var response=owner.request("POST","/api/quizzes",request,true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode quiz=json.readTree(response.body()); quizzes.add(quiz.get("id").asLong());
        assertThat(quiz.get("ownerUserId").asLong()).isEqualTo(owner.userId); return quiz;
    }
    @AfterEach void cleanup() {
        clients.forEach(client -> client.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID"))
                .forEach(c->sessions.revoke(c.getValue(),"LOGGED_OUT")));
        for(long game:games) {jdbc.update("DELETE FROM game_question WHERE game_session_id=?",game);jdbc.update("DELETE FROM game_member WHERE game_session_id=?",game);jdbc.update("DELETE FROM game_session WHERE id=?",game);}
        for(long room:rooms) {jdbc.update("DELETE FROM room_member WHERE room_id=?",room);jdbc.update("DELETE FROM room WHERE id=?",room);}
        for(long quiz:quizzes) {jdbc.update("DELETE FROM question WHERE quiz_id=?",quiz);jdbc.update("DELETE FROM quiz WHERE id=?",quiz);}
        usernames.forEach(name->jdbc.update("DELETE FROM app_user WHERE username=?",name)); clients.forEach(c->c.http.close());
    }
    @Test void publicMetadataPrivateScopeAndOwnerCrud() throws Exception {
        Client owner=login(), other=login(); JsonNode quiz=create(owner,"PUBLIC"); long id=quiz.get("id").asLong();
        assertThat(quiz.get("questions").size()).isEqualTo(10);
        assertThat(quiz.get("questions").get(0).get("correctAnswer").asText()).isEqualTo("B");
        var publicView=other.request("GET","/api/quizzes/"+id,null,false);
        assertThat(publicView.statusCode()).isEqualTo(200);
        assertThat(publicView.body()).doesNotContain("questions", "options", "correct", "Original", "imageRef");
        var list=other.request("GET","/api/quizzes",null,false);
        assertThat(list.statusCode()).isEqualTo(200); assertThat(list.body()).doesNotContain("correctAnswer","questions");
        var edit=body("PRIVATE","Changed");edit.put("revision",0);
        error(other.request("PUT","/api/quizzes/"+id,edit,true),403,"FORBIDDEN");
        error(other.request("DELETE","/api/quizzes/"+id+"?revision=0",null,true),403,"FORBIDDEN");
        assertThat(owner.request("PUT","/api/quizzes/"+id,edit,true).statusCode()).isEqualTo(200);
        error(other.request("GET","/api/quizzes/"+id,null,false),404,"QUIZ_NOT_FOUND");
        assertThat(json.readTree(other.request("GET","/api/quizzes",null,false).body()).get("items")).allSatisfy(item -> assertThat(item.path("id").asLong()).isNotEqualTo(id));
        assertThat(owner.request("GET","/api/quizzes/"+id,null,false).body()).contains("correctAnswer","Changed");
        assertThat(owner.request("DELETE","/api/quizzes/"+id+"?revision=1",null,true).statusCode()).isEqualTo(204);
        error(owner.request("GET","/api/quizzes/"+id,null,false),404,"QUIZ_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE quiz_id=?",Integer.class,id)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE quiz_id=? AND deleted_at_ms IS NULL",Integer.class,id)).isZero();
    }
    @Test void validationFourOptionsCorrectAnswerCsrfAndAuthentication() throws Exception {
        Client owner=login();
        for(Object options:List.of(Map.of("A","a","B","b","C","c"), Map.of("A","a","B","b","C","c","D","d","E","extra"), Map.of("A"," ","B","b","C","c","D","d"))) {
            var invalid=body("PUBLIC","Text"); var q=question("Text");q.put("options",options);invalid.put("questions",List.of(q));
            error(owner.request("POST","/api/quizzes",invalid,true),400,"INVALID_REQUEST");
        }
        for(String correct:List.of("E", "", "A,B")) {
            var invalid=body("PUBLIC","Text");var q=question("Text");q.put("correctAnswer",correct);invalid.put("questions",List.of(q));
            error(owner.request("POST","/api/quizzes",invalid,true),400,"INVALID_REQUEST");
        }
        for(Object field:List.of(" ", "x".repeat(201))) {
            var invalid=body("PUBLIC","Text");invalid.put("title",field);error(owner.request("POST","/api/quizzes",invalid,true),400,"INVALID_REQUEST");
        }
        var invalid=body("PUBLIC","Text"); invalid.put("questions",List.of());error(owner.request("POST","/api/quizzes",invalid,true),400,"INVALID_REQUEST");
        invalid.put("questions",Collections.nCopies(51,question("Text")));error(owner.request("POST","/api/quizzes",invalid,true),400,"INVALID_REQUEST");
        var q=question("Text");q.put("imageRef","https://untrusted/image.png");invalid.put("questions",List.of(q));error(owner.request("POST","/api/quizzes",invalid,true),400,"INVALID_REQUEST");
        error(owner.request("POST","/api/quizzes",body("PUBLIC","Text"),false),403,"CSRF_INVALID");
        error(owner.request("GET","/api/quizzes?page=-1",null,false),400,"INVALID_REQUEST");
        error(owner.request("GET","/api/quizzes?page=2147483647&size=100",null,false),400,"INVALID_REQUEST");
        error(owner.request("GET","/api/quizzes?size=101",null,false),400,"INVALID_REQUEST");
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/quizzes")).GET().build(),HttpResponse.BodyHandlers.ofString());
        error(response,401,"UNAUTHENTICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE owner_user_id=?",Integer.class,owner.userId)).isZero();
    }
    @Test void concurrentEditAndQuestionOnlyEditAdvanceRevisionWithoutLosingHistory() throws Exception {
        Client owner=login();JsonNode initial=create(owner,"PUBLIC");long id=initial.get("id").asLong();long originalId=initial.get("questions").get(0).get("id").asLong();
        var left=body("PUBLIC","Left"); var right=body("PUBLIC","Right"); left.put("revision",0); right.put("revision",0);
        var one=owner.http.sendAsync(owner.builder("PUT","/api/quizzes/"+id,left,true),HttpResponse.BodyHandlers.ofString());
        var two=owner.http.sendAsync(owner.builder("PUT","/api/quizzes/"+id,right,true),HttpResponse.BodyHandlers.ofString());
        assertThat(List.of(one.get(10,TimeUnit.SECONDS).statusCode(),two.get(10,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("SELECT revision FROM quiz WHERE id=?",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM question WHERE id=?",String.class,originalId)).isEqualTo("Original 0");
        assertThat(jdbc.queryForObject("SELECT deleted_at_ms FROM question WHERE id=?",Long.class,originalId)).isNotNull();
        error(owner.request("DELETE","/api/quizzes/"+id+"?revision=0",null,true),409,"REVISION_CONFLICT");
        error(owner.request("DELETE","/api/quizzes/"+id,null,true),400,"INVALID_REQUEST");
        var next=body("PUBLIC","Only question changed");next.put("revision",1);
        var updated=owner.request("PUT","/api/quizzes/"+id,next,true);assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(json.readTree(updated.body()).get("revision").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question WHERE quiz_id=? AND deleted_at_ms IS NULL",Integer.class,id)).isEqualTo(10);
    }
    @Test void imageValidationOwnerOnlyUploadAndImmutableStorage() throws Exception {
        Client owner=login(),other=login(); long id=create(owner,"PUBLIC").get("id").asLong();
        var uploaded=owner.upload(id,png(0xffff0000));assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(201);
        String reference=json.readTree(uploaded.body()).get("imageRef").asText(), url=json.readTree(uploaded.body()).get("url").asText();
        assertThat(reference).matches("sha256:[0-9a-f]{64}");
        assertThat(json.readTree(owner.upload(id,png(0xffff0000)).body()).get("imageRef").asText()).isEqualTo(reference);
        var original=owner.image(url);assertThat(original.statusCode()).isEqualTo(200);assertThat(original.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(other.image(url).statusCode()).isEqualTo(403);error(other.upload(id,png(0xffff0000)),403,"FORBIDDEN");
        error(owner.upload(id,"<svg/>".getBytes(StandardCharsets.UTF_8)),400,"INVALID_IMAGE");
        error(owner.upload(id,new byte[2*1024*1024+1]),413,"IMAGE_TOO_LARGE");
        var edit=body("PUBLIC","Images");var question=question("Image question");question.put("imageRef",reference);edit.put("questions",List.of(question));edit.put("revision",0);
        assertThat(owner.request("PUT","/api/quizzes/"+id,edit,true).statusCode()).isEqualTo(200);
        var invalid=body("PUBLIC","Foreign image");invalid.put("questions",List.of(question));
        error(owner.request("POST","/api/quizzes",invalid,true),400,"IMAGE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz WHERE owner_user_id=?",Integer.class,owner.userId)).isEqualTo(1);
        String nextRef=json.readTree(owner.upload(id,png(0xff0000ff)).body()).get("imageRef").asText();assertThat(nextRef).isNotEqualTo(reference);
        question.put("imageRef",nextRef);edit.put("revision",1);assertThat(owner.request("PUT","/api/quizzes/"+id,edit,true).statusCode()).isEqualTo(200);
        assertThat(owner.request("DELETE","/api/quizzes/"+id+"?revision=2",null,true).statusCode()).isEqualTo(204);
        assertThat(owner.image(url).body()).isEqualTo(original.body());
    }
    @Test void historicalSnapshotAndReleasedImageSurviveEditDeleteWithoutFutureLeak() throws Exception {
        Client author=login(),player=login(),outsider=login();long id=create(author,"PUBLIC").get("id").asLong();
        JsonNode oldImage=json.readTree(author.upload(id,png(0xffff0000)).body()), futureImage=json.readTree(author.upload(id,png(0xff00ff00)).body());
        String reference=oldImage.get("imageRef").asText(), futureRef=futureImage.get("imageRef").asText(), url=oldImage.get("url").asText();
        var q=question("Snapshot original");q.put("imageRef",reference);var edit=body("PUBLIC","Snapshot");edit.put("questions",Collections.nCopies(10,q));edit.put("revision",0);
        JsonNode saved=json.readTree(author.request("PUT","/api/quizzes/"+id,edit,true).body());long source=saved.get("questions").get(0).get("id").asLong();
        long room=insert("INSERT INTO room(host_user_id,quiz_id,room_code,name,status,max_players,question_duration_ms,created_at_ms) VALUES(?,?,?,'History','ACTIVE',10,30000,1000)",author.userId,id,UUID.randomUUID().toString().substring(0,8));rooms.add(room);
        insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'SPECTATOR','JOINED',1000)",room,author.userId);
        insert("INSERT INTO room_member(room_id,user_id,participation,status,joined_at_ms) VALUES(?,?,'PLAYER','JOINED',1000)",room,player.userId);
        long game=insert("INSERT INTO game_session(room_id,quiz_id,quiz_author_user_id,quiz_title_snapshot,status,phase,question_count,config_snapshot,started_at_ms) VALUES(?,?,?,'Original title','ACTIVE','QUESTION_OPEN',10,?,1000)",room,id,author.userId,json.writeValueAsString(GameplayRulesSnapshot.forGame(10,30000,5000)));games.add(game);
        insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'HOST','SPECTATOR','Author')",game,room,author.userId);
        insert("INSERT INTO game_member(game_session_id,room_id,user_id,role,participation,display_name_snapshot) VALUES(?,?,?,'MEMBER','PLAYER','Player')",game,room,player.userId);
        long snap=insert("INSERT INTO game_question(game_session_id,source_question_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,image_ref,question_duration_ms,phase,opened_at_ms,deadline_at_ms) VALUES(?,?,1,'Frozen','A','B','C','D','B',?,30000,'QUESTION_OPEN',1000,31000)",game,source,reference);
        insert("INSERT INTO game_question(game_session_id,source_question_id,order_index,content,option_a,option_b,option_c,option_d,correct_option,image_ref,question_duration_ms,phase) VALUES(?,?,2,'Future','A','B','C','D','A',?,30000,'DECISION')",game,source,futureRef);
        var opened=player.image(url+"?gameSessionId="+game);assertThat(opened.statusCode()).isEqualTo(200);
        assertThat(player.image(futureImage.get("url").asText()+"?gameSessionId="+game).statusCode()).isEqualTo(403);
        assertThat(outsider.image(url+"?gameSessionId="+game).statusCode()).isEqualTo(403);
        var changed=body("PRIVATE","Changed");changed.put("revision",1);assertThat(author.request("PUT","/api/quizzes/"+id,changed,true).statusCode()).isEqualTo(200);
        assertThat(author.request("DELETE","/api/quizzes/"+id+"?revision=2",null,true).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT content FROM game_question WHERE id=?",String.class,snap)).isEqualTo("Frozen");
        assertThat(jdbc.queryForObject("SELECT image_ref FROM game_question WHERE id=?",String.class,snap)).isEqualTo(reference);
        assertThat(jdbc.queryForObject("SELECT correct_option FROM game_question WHERE id=?",String.class,snap)).isEqualTo("B");
        assertThat(player.image(url+"?gameSessionId="+game).body()).isEqualTo(opened.body());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_session WHERE id=?",Integer.class,game)).isEqualTo(1);
    }
    @Test void riddleCrudScopesAndPrivacy() throws Exception {
        Client owner=login(),other=login();
        var request=new LinkedHashMap<String,Object>(Map.of("title","Đố mẹo","visibility","PUBLIC","mode","RIDDLE",
            "questions",List.of(Map.of("content","Cái gì có chân mà không đi?","acceptedAnswers",List.of(" CÁI   BÀN ","bàn")))));
        var made=owner.request("POST","/api/quizzes",request,true);assertThat(made.statusCode()).as(made.body()).isEqualTo(201);
        var created=json.readTree(made.body());long id=created.path("id").asLong();quizzes.add(id);
        assertThat(created.path("mode").asText()).isEqualTo("RIDDLE");
        assertThat(created.path("questions").get(0).path("options").isNull()).isTrue();
        assertThat(created.path("questions").get(0).path("acceptedAnswers").toString()).contains("cái bàn","bàn");
        assertThat(jdbc.queryForObject("select option_a from question where quiz_id=?",String.class,id)).isNull();
        assertThat(other.request("GET","/api/quizzes/"+id,null,false).body()).doesNotContain("acceptedAnswers","cái bàn","questions");
        for(String scope:List.of("MINE","SHARED")) {
            var list=json.readTree(owner.request("GET","/api/quizzes?scope="+scope+"&mode=RIDDLE",null,false).body());
            assertThat(list.path("items").toString()).contains("\"id\":"+id).doesNotContain("acceptedAnswers","matchingPolicy");
        }
        assertThat(json.readTree(other.request("GET","/api/quizzes?scope=MINE&mode=RIDDLE",null,false).body()).path("items")).isEmpty();
        request.put("visibility","PRIVATE");request.put("revision",created.path("revision").asLong());
        assertThat(owner.request("PUT","/api/quizzes/"+id,request,true).statusCode()).isEqualTo(200);
        error(other.request("GET","/api/quizzes/"+id,null,false),404,"QUIZ_NOT_FOUND");
        assertThat(owner.request("GET","/api/quizzes?scope=MINE&mode=RIDDLE",null,false).body()).contains("\"id\":"+id);
        assertThat(owner.request("GET","/api/quizzes?scope=SHARED&mode=RIDDLE",null,false).body()).doesNotContain("\"id\":"+id);
        error(other.request("PUT","/api/quizzes/"+id,request,true),403,"FORBIDDEN");
        error(owner.request("POST","/api/rooms",Map.of("requestId",UUID.randomUUID().toString(),"config",Map.of("quizId",id,"name","Text Room","maxPlayers",3,"questionDurationMs",20000,"hostParticipation","SPECTATOR")),true),409,"MODE_NOT_IMPLEMENTED");
        assertThat(owner.request("DELETE","/api/quizzes/"+id+"?revision=1",null,true).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from question where quiz_id=?",Integer.class,id)).isEqualTo(2);
    }
    @Test void rejectsWrongModesAndMixedAnswerShapesWithoutPartialRows() throws Exception {
        Client owner=login();
        for(Object answers:List.of(List.of(),List.of(" "),List.of("\u00a0"),List.of("x".repeat(301)),List.of("İ".repeat(300)),Collections.nCopies(21,"x"))) {
            error(owner.request("POST","/api/quizzes",Map.of("title","R","visibility","PUBLIC","mode","RIDDLE","questions",List.of(Map.of("content","R?","acceptedAnswers",answers))),true),400,"INVALID_REQUEST");
        }
        var q=question("R?");q.put("acceptedAnswers",List.of("bàn"));
        error(owner.request("POST","/api/quizzes",Map.of("title","R","visibility","PUBLIC","mode","RIDDLE","questions",List.of(q)),true),400,"INVALID_REQUEST");
        error(owner.request("POST","/api/quizzes",Map.of("title","Q","visibility","PUBLIC","questions",List.of(q)),true),400,"INVALID_REQUEST");
        error(owner.request("POST","/api/quizzes",Map.of("title","S","visibility","PUBLIC","mode","CLUES","questions",List.of(Map.of("content","S?","acceptedAnswers",List.of("x")))),true),400,"INVALID_REQUEST");
        error(owner.request("GET","/api/quizzes?scope=UNKNOWN",null,false),400,"INVALID_REQUEST");
        error(owner.request("GET","/api/quizzes?mode=UNKNOWN",null,false),400,"INVALID_REQUEST");
        assertThat(jdbc.queryForObject("select count(*) from quiz where owner_user_id=?",Integer.class,owner.userId)).isZero();
    }
    @Test void modeCannotChangeWhileOldQuizCrudAndBothScopesWork() throws Exception {
        Client owner=login();var quiz=create(owner,"PUBLIC");long id=quiz.path("id").asLong();
        assertThat(quiz.path("mode").asText()).isEqualTo("QUIZ");
        for(String scope:List.of("MINE","SHARED")) assertThat(owner.request("GET","/api/quizzes?scope="+scope+"&mode=QUIZ",null,false).body()).contains("\"id\":"+id).doesNotContain("acceptedAnswers");
        var edit=body("PUBLIC","Original");edit.put("revision",0);edit.put("mode","RIDDLE");
        error(owner.request("PUT","/api/quizzes/"+id,edit,true),400,"MODE_IMMUTABLE");
    }
    @Test void arrangementCrudPermissionsIdsAliasesAndValidation() throws Exception {
        Client owner=login(),other=login();
        for(String mode:List.of("VIETNAMESE_PUZZLE","ORDERING")) {
            var items=List.of(Map.of("id","a","text","ha"),Map.of("id","space","text"," "),Map.of("id","b","text","ha"));
            if(mode.equals("ORDERING"))items=List.of(Map.of("id","a","text","Một"),Map.of("id","space","text","Hai"),Map.of("id","b","text","Một"));
            var q=new LinkedHashMap<String,Object>();q.put("content","Sắp xếp");q.put(mode.equals("ORDERING")?"items":"pieces",items);q.put("correctOrder",List.of("a","space","b"));if(!mode.equals("ORDERING"))q.put("acceptedAnswers",List.of(" HA   HA "));
            var body=new LinkedHashMap<String,Object>(Map.of("title","Arrange "+mode,"visibility","PUBLIC","mode",mode,"questions",List.of(q)));
            var made=owner.request("POST","/api/quizzes",body,true);assertThat(made.statusCode()).as(made.body()).isEqualTo(201);var saved=json.readTree(made.body());long id=saved.path("id").asLong();quizzes.add(id);
            assertThat(saved.path("questions").get(0).path("correctOrder")).hasSize(3);assertThat(saved.path("questions").get(0).path("options").isNull()).isTrue();
            assertThat(other.request("GET","/api/quizzes/"+id,null,false).body()).doesNotContain("correctOrder","pieces","items","acceptedAnswers","questions");
            error(other.request("PUT","/api/quizzes/"+id,Map.of("revision",0,"title","Other","visibility","PUBLIC","mode",mode,"questions",List.of(q)),true),403,"FORBIDDEN");
            for(var bad:List.of(List.of("a","space"),List.of("a","a","b"),List.of("a","space","fake"))) {q.put("correctOrder",bad);error(owner.request("POST","/api/quizzes",body,true),400,"INVALID_REQUEST");}
            q.put("correctOrder",List.of("a","space","b"));
            if(mode.equals("VIETNAMESE_PUZZLE")){q.put("acceptedAnswers",List.of("different"));error(owner.request("POST","/api/quizzes",body,true),400,"INVALID_REQUEST");q.put("acceptedAnswers",List.of("ha ha"));}
            var duplicate=new ArrayList<>(items);duplicate.set(1,items.get(0));q.put(mode.equals("ORDERING")?"items":"pieces",duplicate);error(owner.request("POST","/api/quizzes",body,true),400,"INVALID_REQUEST");q.put(mode.equals("ORDERING")?"items":"pieces",items);
            body.put("revision",0);body.put("visibility","PRIVATE");assertThat(owner.request("PUT","/api/quizzes/"+id,body,true).statusCode()).isEqualTo(200);error(other.request("GET","/api/quizzes/"+id,null,false),404,"QUIZ_NOT_FOUND");
            assertThat(owner.request("GET","/api/quizzes?scope=MINE&mode="+mode,null,false).body()).contains("\"id\":"+id);assertThat(owner.request("GET","/api/quizzes?scope=SHARED&mode="+mode,null,false).body()).doesNotContain("\"id\":"+id);
            assertThat(owner.request("DELETE","/api/quizzes/"+id+"?revision=1",null,true).statusCode()).isEqualTo(204);assertThat(jdbc.queryForObject("select count(*) from question where quiz_id=?",Integer.class,id)).isEqualTo(2);
        }
    }
    @Test void imageWordDraftUploadCreatePrivacyValidationAndImmutableEdit() throws Exception {
        Client owner=login(),other=login();
        Client anonymous=new Client();anonymous.csrf();error(anonymous.upload("/api/quizzes/images/drafts",png(0xffab1200)),401,"UNAUTHENTICATED");
        String token=owner.csrf;owner.csrf="invalid-token";error(owner.upload("/api/quizzes/images/drafts",png(0xffab1200)),403,"CSRF_INVALID");owner.csrf=token;
        var staged=owner.upload("/api/quizzes/images/drafts",png(0xffab1200));assertThat(staged.statusCode()).as(staged.body()).isEqualTo(201);
        var image=json.readTree(staged.body());String ref=image.path("imageRef").asText(),draft=image.path("url").asText();
        assertThat(owner.image(draft).statusCode()).isEqualTo(200);assertThat(other.image(draft).statusCode()).isEqualTo(404);
        error(owner.upload("/api/quizzes/images/drafts","<svg/>".getBytes(StandardCharsets.UTF_8)),400,"INVALID_IMAGE");
        error(owner.upload("/api/quizzes/images/drafts",new byte[2*1024*1024+1]),413,"IMAGE_TOO_LARGE");
        var q=new LinkedHashMap<String,Object>(Map.of("content","Đoán cụm từ qua hình","acceptedAnswers",List.of(" BẮT   CÁ ","bắt cá"),"imageRef",ref));
        var body=new LinkedHashMap<String,Object>(Map.of("title","Đuổi hình","mode","IMAGE_WORD","visibility","PUBLIC","questions",List.of(q)));
        error(other.request("POST","/api/quizzes",body,true),400,"IMAGE_NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from quiz where owner_user_id=?",Integer.class,other.userId)).isZero();
        q.remove("imageRef");error(owner.request("POST","/api/quizzes",body,true),400,"INVALID_REQUEST");q.put("imageRef",ref);
        q.put("options",Map.of("A","a","B","b","C","c","D","d"));error(owner.request("POST","/api/quizzes",body,true),400,"INVALID_REQUEST");q.remove("options");
        q.put("acceptedAnswers",List.of(" "));error(owner.request("POST","/api/quizzes",body,true),400,"INVALID_REQUEST");q.put("acceptedAnswers",List.of(" BẮT   CÁ ","bắt cá"));
        var made=owner.request("POST","/api/quizzes",body,true);assertThat(made.statusCode()).as(made.body()).isEqualTo(201);var saved=json.readTree(made.body());long id=saved.path("id").asLong();quizzes.add(id);
        assertThat(saved.path("questions").get(0).path("imageRef").asText()).isEqualTo(ref);
        assertThat(saved.path("questions").get(0).path("acceptedAnswers")).hasSize(1);
        assertThat(jdbc.queryForObject("select JSON_UNQUOTE(JSON_EXTRACT(payload,'$.mediaRef')) from question where quiz_id=?",String.class,id)).isEqualTo(ref);
        assertThat(jdbc.queryForObject("select option_a from question where quiz_id=?",String.class,id)).isNull();
        String url="/api/quizzes/"+id+"/images/"+ref.substring(7);var original=owner.image(url);assertThat(original.statusCode()).isEqualTo(200);
        assertThat(other.image(url).statusCode()).isEqualTo(403);
        assertThat(other.request("GET","/api/quizzes/"+id,null,false).body()).doesNotContain("questions","imageRef","acceptedAnswers","bắt cá");
        assertThat(other.request("GET","/api/quizzes?scope=SHARED&mode=IMAGE_WORD",null,false).body()).doesNotContain("imageRef","acceptedAnswers");
        error(other.upload(id,png(0xff1200ab)),403,"FORBIDDEN");
        var next=json.readTree(owner.upload(id,png(0xff1200ab)).body());q.put("imageRef",next.path("imageRef").asText());body.put("visibility","PRIVATE");body.put("revision",0);
        assertThat(owner.request("PUT","/api/quizzes/"+id,body,true).statusCode()).isEqualTo(200);
        error(other.request("GET","/api/quizzes/"+id,null,false),404,"QUIZ_NOT_FOUND");
        assertThat(owner.image(url).body()).isEqualTo(original.body());
        assertThat(owner.request("DELETE","/api/quizzes/"+id+"?revision=1",null,true).statusCode()).isEqualTo(204);
        assertThat(owner.image(url).body()).isEqualTo(original.body());
    }
    byte[] png(int color) throws Exception {
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,color);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();
    }
    void error(HttpResponse<String> response,int status,String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("password_hash","jdbc:","stackTrace");
    }
    long insert(String sql,Object...args) {
        var keys=new GeneratedKeyHolder();jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);return keys.getKey().longValue();
    }
}
