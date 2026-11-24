package com.blackbox.service;

import com.blackbox.config.MailNotificationProperties;
import com.blackbox.dto.ReviewDtos;
import com.blackbox.entity.*;
import com.blackbox.repository.EmailDeliveryRepository;
import com.blackbox.scheduler.ScoreScheduler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** 명시적으로 켜는 로컬 Mailpit 검사. 실제 Gmail/자격증명을 쓰지 않으며 기본 CI에서는 실행하지 않는다. */
@EnabledIfEnvironmentVariable(named="BB_MAILPIT_SMOKE", matches="true")
@SpringBootTest(properties={"spring.datasource.hikari.minimum-idle=0", "app.frontend-base-url=http://localhost:13010",
        "spring.mail.host=127.0.0.1", "spring.mail.port=28135", "spring.mail.username=sender@example.test",
        "spring.mail.password=", "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false", "spring.mail.properties.mail.smtp.starttls.required=false"})
@ActiveProfiles("test")
class EmailSmtpEndToEndTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MailNotificationProperties mail;
    @Autowired EmailNotificationSettingsService preferences;
    @Autowired EmailVerificationService verification;
    @Autowired TaskWorkflowService workflow;
    @Autowired ReviewRoundService rounds;
    @Autowired EmailDeliveryRepository deliveries;
    @Autowired TaskRequestEmailEligibility tasks;
    @Autowired ReviewRequestEmailEligibility reviews;
    @Autowired ObjectProvider<EmailDeliveryAuthorization> authorizations;
    @Autowired ObjectProvider<EmailNotificationTransport> transports;
    @MockitoBean EmailDeliveryWorker scheduledWorker;
    @MockitoBean ScoreScheduler scheduler;
    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    record Team(UUID project, User actor, User assignee, User recipient) {}

    @Test void actualSmtpVerifiesTheAddressAndSendsOnlyCurrentOptedInTaskAndReviewRequests() throws Exception {
        Team team = new TransactionTemplate(transactions).execute(tx -> {
            User actor=user(em,"합성 SMTP 요청자"), assignee=user(em,"합성 SMTP 담당자"), recipient=user(em,"합성 SMTP 확인자");
            recipient.setEmail("mailpit-"+UUID.randomUUID()+"@example.test");
            Project p=project(em,actor); member(em,p,actor,"LEADER"); member(em,p,assignee,"MEMBER"); member(em,p,recipient,"MEMBER"); em.flush();
            return new Team(p.getId(),actor,assignee,recipient);
        });
        String address=team.recipient().getEmail();
        mail.setEnabled(true); mail.setRecipientAllowlist(List.of(address));
        var worker=new EmailDeliveryWorker(mail,deliveries,tasks,reviews,authorizations,transports,"http://localhost:13010");
        try {
            preferences.update(team.recipient().getId(),true);
            UUID before=task(team); request(team,before);
            assertEquals(0,outbox(team,before)); assertEquals(1,app(team,before)); worker.poll();
            assertEquals(0,messages(address).size());

            assertEquals(EmailVerificationService.Outcome.REQUESTED,verification.request(team.recipient()));
            var confirmation=waitFor(address,1).getFirst();
            String text=messageText(confirmation);
            var match=Pattern.compile("http://localhost:13010/email/verify#token=([A-Za-z0-9_-]+)").matcher(text);
            assertTrue(match.find(),"인증 메일에 fragment 링크가 있어야 한다");
            verification.confirm(team.recipient(),match.group(1));
            assertTrue(preferences.get(team.recipient().getId()).emailVerified());
            assertEquals(0,outbox(team,before),"인증 뒤 과거 사건을 새로 예약하지 않는다");

            UUID current=task(team); request(team,current); worker.poll();
            assertEquals("SENT",state(team,current));
            assertTrue(waitFor(address,2).stream().anyMatch(m->messageText(m).contains("task="+current)));

            preferences.update(team.recipient().getId(),false);
            UUID off=task(team); request(team,off); worker.poll();
            assertEquals(0,outbox(team,off)); assertEquals(1,app(team,off)); assertEquals(2,messages(address).size());

            preferences.update(team.recipient().getId(),true);
            UUID old=task(team); request(team,old);
            preferences.update(team.recipient().getId(),false); preferences.update(team.recipient().getId(),true);
            worker.poll(); assertEquals("SKIPPED",state(team,old)); assertEquals(2,messages(address).size());

            UUID latest=task(team); request(team,latest); worker.poll();
            assertEquals("SENT",state(team,latest)); waitFor(address,3);
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                Project p=em.find(Project.class,team.project());
                var d=deliverable(em,p,"합성 SMTP 최종 제출물");
                var f=file(em,p,em.find(User.class,team.assignee().getId()),"synthetic.txt",1,'s'); em.flush();
                rounds.open(p.getId(),d.getId(),new ReviewDtos.OpenRequest(f.getId()),em.find(User.class,team.actor().getId()));
            });
            worker.poll();
            assertTrue(waitFor(address,4).stream().anyMatch(m->messageText(m).contains("/deliverables/")));
            assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM notification_email_outbox WHERE project_id=? AND status='SENT'",Integer.class,team.project()));
        } finally {
            mail.setEnabled(false);
            jdbc.update("UPDATE notification_email_outbox SET status='SKIPPED',sent_at=NULL WHERE project_id=? AND status IN ('PENDING','PROCESSING')",team.project());
        }
    }

    private UUID task(Team t) {
        return new TransactionTemplate(transactions).execute(tx->{
            Task task=new Task(); task.setProject(em.find(Project.class,t.project())); task.setCreatedBy(em.find(User.class,t.actor().getId()));
            task.setTitle("합성 SMTP 결과 확인"); task.setPriority("MEDIUM"); task.setStatus("IN_PROGRESS"); em.persist(task);
            TaskAssignee a=new TaskAssignee(); a.setTask(task); a.setUser(em.find(User.class,t.assignee().getId())); em.persist(a); em.flush(); return task.getId();
        });
    }
    private void request(Team t,UUID task) { new TransactionTemplate(transactions).executeWithoutResult(tx->workflow.request(t.project(),task,em.find(User.class,t.actor().getId()))); }
    private int outbox(Team t,UUID task) { return jdbc.queryForObject("SELECT count(*) FROM notification_email_outbox WHERE project_id=? AND task_id=?",Integer.class,t.project(),task); }
    private int app(Team t,UUID task) { return jdbc.queryForObject("SELECT count(*) FROM user_notifications WHERE project_id=? AND task_id=?",Integer.class,t.project(),task); }
    private String state(Team t,UUID task) { return jdbc.queryForObject("SELECT status FROM notification_email_outbox WHERE project_id=? AND task_id=?",String.class,t.project(),task); }
    private JsonNode read(String path) {
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:28035"+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw new IllegalStateException("테스트 메일함 응답 실패");
            return json.readTree(response.body());
        } catch(Exception e) { throw new IllegalStateException("로컬 테스트 메일함에 연결하지 못함",e); }
    }
    private List<JsonNode> messages(String address) {
        var result=new ArrayList<JsonNode>();
        for(var message:read("/api/v1/messages?limit=100").get("messages"))
            for(var to:message.get("To")) if(address.equals(to.get("Address").asString())) { result.add(message); break; }
        return result;
    }
    private List<JsonNode> waitFor(String address,int count) throws InterruptedException {
        for(int i=0;i<50;i++) { var values=messages(address); if(values.size()==count) return values; Thread.sleep(100); }
        fail("합성 계정의 예상 메일 수신 수에 도달하지 못함: "+count); return List.of();
    }
    private String messageText(JsonNode message) { return read("/api/v1/message/"+message.get("ID").asString()).get("Text").asString(); }
}
