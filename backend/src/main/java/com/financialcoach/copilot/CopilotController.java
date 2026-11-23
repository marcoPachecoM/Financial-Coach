package com.financialcoach.copilot;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.financialcoach.auth.AuthenticatedUser;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@RestController
public class CopilotController {

    static final String CHAT_PATH = "/api/v1/copilot/chat";
    static final String STREAM_PATH = "/api/v1/copilot/chat/stream";

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService){
        this.copilotService = copilotService;
    }

    /** The whole answer at once. */
    @PostMapping(CHAT_PATH)
    public ChatResponseRecord chat(@AuthenticationPrincipal AuthenticatedUser user,
                            @Valid @RequestBody ChatRequestRecord request) {
        return copilotService.chat(user.id(), request);
    }

    /** The answer as it is written, as Server-Sent Events (see ChatStreamEvents). */
    @PostMapping(path = STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@AuthenticationPrincipal AuthenticatedUser user,
                                                @Valid @RequestBody ChatRequestRecord request) {
        return copilotService.stream(user.id(), request);
    }
}
