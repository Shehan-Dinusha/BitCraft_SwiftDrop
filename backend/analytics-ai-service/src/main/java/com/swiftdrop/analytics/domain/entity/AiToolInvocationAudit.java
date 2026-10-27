package com.swiftdrop.analytics.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_tool_invocation_audit")
public class AiToolInvocationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false, unique = true)
    private UUID auditId = UUID.randomUUID();

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "caller_persona", nullable = false, length = 64)
    private String callerPersona;

    @Column(name = "prompt_or_query", columnDefinition = "TEXT", nullable = false)
    private String promptOrQuery;

    @Column(name = "input_parameters", columnDefinition = "TEXT")
    private String inputParameters;

    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    @Column(name = "execution_time_ms", nullable = false)
    private long executionTimeMs;

    @Column(name = "success", nullable = false)
    private boolean success = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiToolInvocationAudit() {}

    public AiToolInvocationAudit(String toolName, String callerPersona, String promptOrQuery,
                                 String inputParameters, String outputResult, long executionTimeMs, boolean success) {
        this.toolName = toolName;
        this.callerPersona = callerPersona;
        this.promptOrQuery = promptOrQuery;
        this.inputParameters = inputParameters;
        this.outputResult = outputResult;
        this.executionTimeMs = executionTimeMs;
        this.success = success;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getAuditId() { return auditId; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getCallerPersona() { return callerPersona; }
    public void setCallerPersona(String callerPersona) { this.callerPersona = callerPersona; }

    public String getPromptOrQuery() { return promptOrQuery; }
    public void setPromptOrQuery(String promptOrQuery) { this.promptOrQuery = promptOrQuery; }

    public String getInputParameters() { return inputParameters; }
    public void setInputParameters(String inputParameters) { this.inputParameters = inputParameters; }

    public String getOutputResult() { return outputResult; }
    public void setOutputResult(String outputResult) { this.outputResult = outputResult; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
