package com.blackbox.agent.run;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 모델의 답에서 JSON 객체 하나를 읽는다. 앞뒤 말이나 코드 블록 표시는 첫 "{"와 마지막 "}" 밖이라 버린다.
 * 그대로 읽히지 않으면 괄호의 짝만 맞춰 한 번 더 읽는다. Gemini가 카드 객체를 닫는 "}"를 빼고 목록의 "]"를 먼저 쓴 일이 있었다(A-83).
 * 따옴표 안의 글은 건드리지 않는다. 짝을 맞춘 결과도 카드 종류의 검증을 그대로 지나므로, 모양이 틀린 내용이 저장되지는 않는다
 */
final class ModelJson {
    private ModelJson() {}

    /** 읽을 수 없으면 JacksonException(또는 "{"가 없으면 IndexOutOfBoundsException)을 던진다 */
    static JsonNode read(ObjectMapper json, String answer) {
        String body = answer.substring(answer.indexOf('{'), answer.lastIndexOf('}') + 1);
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            return json.readTree(balance(body));
        }
    }

    /**
     * 괄호의 짝을 맞춘다. 닫는 괄호가 지금 열린 것과 다르면, 그 괄호가 닫을 곳까지 빠진 닫는 괄호를 앞에 넣는다.
     * 열린 적 없는 닫는 괄호는 뺀다. 끝까지 열려 있는 괄호는 순서대로 닫는다
     */
    static String balance(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        Deque<Character> open = new ArrayDeque<>();
        boolean inString = false, escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"' -> { inString = true; out.append(c); }
                case '{' -> { open.push('}'); out.append(c); }
                case '[' -> { open.push(']'); out.append(c); }
                case '}', ']' -> {
                    if (!open.contains(c)) continue;               // 열린 적 없는 닫는 괄호
                    while (open.peek() != c) out.append(open.pop());  // 빠진 닫는 괄호를 채운다
                    out.append(open.pop());
                }
                default -> out.append(c);
            }
        }
        while (!open.isEmpty()) out.append(open.pop());
        return out.toString();
    }
}
