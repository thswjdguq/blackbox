package com.blackbox.agent.tool;

import com.blackbox.dto.MeetingResponse;
import com.blackbox.entity.User;
import com.blackbox.service.MeetingService;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 회의 목록(K-59 5장). 출석 기록, 체크인 코드, 외부 연동 정보는 넣지 않는다.
 * 회의에서 만든 액션 아이템도 넣지 않는다. 업무와 회의를 잇는 칸이 없어 업무는 list_tasks로 따로 읽는다.
 * 회의록은 팀원이 자유롭게 쓴 글이라 연락처가 섞일 수 있어, 이메일 주소와 전화번호를 가려서 보낸다(K-30 5장)
 */
@Component
@RequiredArgsConstructor
public class ListMeetingsTool implements ProjectTool {
    public static final String NAME = "list_meetings";
    static final int NOTES_LIMIT = 1000;
    static final String EMAIL_MASK = "[이메일]", PHONE_MASK = "[전화번호]";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // 국내 번호(010-1234-5678, 02 123 4567, 01012345678)와 +82로 시작하는 번호. 날짜처럼 0으로 시작하지 않는 숫자는 건드리지 않는다
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+82[-.\\s]?|0)\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)");

    private final MeetingService meetings;

    @Override public String name() { return NAME; }
    @Override public String label() { return "회의 조회"; }
    @Override public String description() {
        return "회의를 최근 날짜 순으로 돌려준다. 제목, 날짜, 목적, 결정 사항과 회의록 앞부분(" + NOTES_LIMIT + "자)이 들어 있다. "
                + "notesTruncated가 true면 회의록이 더 길다. 이메일 주소와 전화번호는 " + EMAIL_MASK + ", " + PHONE_MASK + "로 가려져 있다.";
    }

    @Override public Object read(UUID projectId, User user, JsonNode arguments) {
        return meetings.listMeetings(projectId, user).stream().map(Item::from).toList();
    }

    static String masked(String text) {
        return text == null ? null : PHONE.matcher(EMAIL.matcher(text).replaceAll(EMAIL_MASK)).replaceAll(PHONE_MASK);
    }

    record Item(UUID id, String title, OffsetDateTime meetingDate, String purpose, String decisions, String notes, boolean notesTruncated) {
        static Item from(MeetingResponse m) {
            // 가린 뒤에 자른다. 자른 끝에 연락처의 일부가 남지 않는다
            String notes = masked(m.notes());
            // 글자 수는 코드 포인트로 센다. 이모지 같은 문자를 반으로 자르지 않는다
            boolean cut = notes != null && notes.codePointCount(0, notes.length()) > NOTES_LIMIT;
            return new Item(m.id(), masked(m.title()), m.meetingDate(), masked(m.purpose()), masked(m.decisions()),
                    cut ? notes.substring(0, notes.offsetByCodePoints(0, NOTES_LIMIT)) : notes, cut);
        }
    }
}
