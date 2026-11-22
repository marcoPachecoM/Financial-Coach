package com.financialcoach.copilot;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.financialcoach.auth.AuthenticatedUser;

import jakarta.validation.Valid;

@RestController 
public class CopilotController {

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService){
        this.copilotService = copilotService;
    }

    @PostMapping ("/api/v1/copilot/chat")
    public ChatResponseRecord chat(@AuthenticationPrincipal AuthenticatedUser user,
                            @Valid @RequestBody ChatRequestRecord request) {
        return copilotService.chat(user.id(), request);
    }
}