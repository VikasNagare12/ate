package com.vidnyan.ate.agentic.core;

import java.util.Map;

/**
 * Context passed between agents.
 * Acts as a shared blackboard for agent communication.
 */
public record AgentContext(
    String sessionId,
    Map<String, Object> data
) {
    public static AgentContext empty() {
        return new AgentContext(java.util.UUID.randomUUID().toString(), new java.util.HashMap<>());
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }
    
    public void put(String key, Object value) {
        data.put(key, value);
    }
}
