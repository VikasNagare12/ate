package com.vidnyan.ate.advisor;

import com.vidnyan.ate.report.ReportModel;

/**
 * Interface for AI components that analyze the final report.
 * Adheres to DIP/OCP - implementation can be swapped (Mock vs OpenAI vs Gemini).
 */
public interface Advisor {
    
    /**
     * Analyze the report and generate advice.
     * 
     * @param report The analysis report
     * @return Advice containing suggestions
     */
    Advice analyze(ReportModel report);
}
