package com.vidnyan.ate.agentic.core;

/**
 * Base interface for all agents in the Agentic Static Analysis system.
 * Agents are specialized components that handle specific domains of the analysis.
 */
public interface Agent<I, O> {
    
    /**
     * Get the unique name of this agent.
     */
    String getName();
    
    /**
     * Execute the agent's primary function.
     * @param input The input context/command
     * @return The output/result of processing
     */
    O execute(I input);
    
    /**
     * Check if the agent is ready to operate.
     */
    default boolean isReady() {
        return true;
    }
}
