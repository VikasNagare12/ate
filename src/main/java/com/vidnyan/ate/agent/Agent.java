package com.vidnyan.ate.agent;

/**
 * Base interface for all AI Agents.
 * Each agent has a specific responsibility and uses LLM for reasoning.
 */
public interface Agent<INPUT, OUTPUT> {

    /**
     * Get the agent's name.
     */
    String getName();

    /**
     * Get the agent's description.
     */
    String getDescription();

    /**
     * Execute the agent's task.
     */
    OUTPUT execute(INPUT input);

    /**
     * Get the system prompt for this agent.
     */
    String getSystemPrompt();
}
