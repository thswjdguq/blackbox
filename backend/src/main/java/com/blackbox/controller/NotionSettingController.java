package com.blackbox.controller;

import com.blackbox.dto.NotionSettingRequest;
import com.blackbox.dto.NotionStatusResponse;
import com.blackbox.entity.User;
import com.blackbox.repository.UserRepository;
import com.blackbox.service.NotionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notion")
public class NotionSettingController {

    private final UserRepository userRepository;
    private final NotionService  notionService;

    public NotionSettingController(UserRepository userRepository, NotionService notionService) {
        this.userRepository = userRepository;
        this.notionService  = notionService;
    }

    /** GET /api/notion/status — 연결 여부 + 워크스페이스 이름 반환 */
    @GetMapping("/status")
    public ResponseEntity<NotionStatusResponse> getStatus(@AuthenticationPrincipal User user) {
        String key = user.getNotionApiKey();
        if (key == null || key.isBlank()) {
            return ResponseEntity.ok(new NotionStatusResponse(false, null));
        }
        String workspaceName = notionService.validateAndGetWorkspaceName(key);
        boolean connected = workspaceName != null;
        return ResponseEntity.ok(new NotionStatusResponse(connected, workspaceName));
    }

    /** POST /api/notion/settings — API Key + Page ID 유효성 검증 후 저장 */
    @PostMapping("/settings")
    public ResponseEntity<NotionStatusResponse> saveSettings(
            @RequestBody NotionSettingRequest req,
            @AuthenticationPrincipal User user) {

        if (req.notionApiKey() == null || req.notionApiKey().isBlank()
                || req.notionPageId() == null || req.notionPageId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String workspaceName = notionService.validateAndGetWorkspaceName(req.notionApiKey());
        if (workspaceName == null) {
            return ResponseEntity.status(422)
                    .body(new NotionStatusResponse(false, null));
        }

        user.setNotionApiKey(req.notionApiKey());
        user.setNotionPageId(req.notionPageId());
        userRepository.save(user);

        return ResponseEntity.ok(new NotionStatusResponse(true, workspaceName));
    }

    /** DELETE /api/notion/disconnect — 연결 해제 */
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
        user.setNotionApiKey(null);
        user.setNotionPageId(null);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
}
