package com.njydsz.pmis.agent.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.agent.api.dto.AgentTraceListDTO;
import com.njydsz.pmis.agent.api.dto.AgentTraceDetailDTO;
import com.njydsz.pmis.agent.server.debug.AgentDebuggerService;

@RestController
@RequestMapping("/agent/debug")
public class DebugController {

    private final AgentDebuggerService agentDebuggerService;

    public DebugController(AgentDebuggerService agentDebuggerService) {
        this.agentDebuggerService = agentDebuggerService;
    }

    @GetMapping("/traces")
    public List<AgentTraceListDTO> listTraces(@RequestParam(defaultValue = "20") int limit) {
        throw new UnsupportedOperationException("listTraces not implemented");
    }

    @GetMapping("/trace/{traceId}")
    public AgentTraceDetailDTO getTrace(@PathVariable String traceId) {
        throw new UnsupportedOperationException("getTrace not implemented");
    }

    @PostMapping("/trace/{traceId}/replay")
    public String replayTrace(@PathVariable String traceId) {
        throw new UnsupportedOperationException("replayTrace not implemented");
    }
}
