package com.goldlens.ai;

import com.goldlens.domain.GoldRiskSnapshot;
import com.goldlens.domain.Indicator;
import com.goldlens.domain.IndicatorValue;
import com.goldlens.domain.Signal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExplainService {

    private static final String FALLBACK_MESSAGE = "Unable to generate explanation at this time. Please try again later.";

    private final GeminiClient geminiClient;

    public ExplainService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    /**
     * Generates an explanation for what an indicator means and why it matters for gold.
     */
    public String explainIndicator(Indicator indicator, IndicatorValue latestValue) {
        String prompt = buildIndicatorPrompt(indicator, latestValue);
        return geminiClient.generateContent(prompt)
                .map(this::sanitizeResponse)
                .orElse(FALLBACK_MESSAGE);
    }

    /**
     * Generates an explanation for why a signal has its current status.
     */
    public String explainSignal(Signal signal) {
        String prompt = buildSignalPrompt(signal);
        return geminiClient.generateContent(prompt)
                .map(this::sanitizeResponse)
                .orElse(FALLBACK_MESSAGE);
    }

    private String buildIndicatorPrompt(Indicator indicator, IndicatorValue latestValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain the following economic indicator in simple terms.\n\n");
        sb.append("Indicator: ").append(indicator.getName()).append("\n");

        if (indicator.getDescription() != null && !indicator.getDescription().isBlank()) {
            sb.append("Description: ").append(indicator.getDescription()).append("\n");
        }

        sb.append("Current Value: ").append(latestValue.getValue()).append(" ").append(indicator.getUnit()).append("\n");
        sb.append("As of: ").append(latestValue.getDate()).append("\n\n");
        sb.append("Question: What does this indicator mean and why does it matter for gold?\n\n");
        sb.append("Keep your response under 150 words. Use plain text only, no markdown or emojis.");

        return sb.toString();
    }

    private String buildSignalPrompt(Signal signal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain the following market signal in simple terms.\n\n");
        sb.append("Indicator: ").append(signal.getIndicator().getName()).append("\n");
        sb.append("Signal: ").append(signal.getSignalType()).append("\n");
        sb.append("Reason: ").append(signal.getReason()).append("\n");
        sb.append("Confidence: ").append(signal.getConfidence()).append("\n");
        sb.append("As of: ").append(signal.getAsOfDate()).append("\n\n");
        sb.append("Question: Explain why this signal is ").append(signal.getSignalType());
        sb.append(" in simple terms. What does it mean for someone watching gold markets?\n\n");
        sb.append("Keep your response under 150 words. Use plain text only, no markdown or emojis.");

        return sb.toString();
    }

    /**
     * Generates an explanation for the aggregated gold risk level.
     */
    public String explainGoldRisk(GoldRiskSnapshot snapshot, List<Signal> signals) {
        String prompt = buildGoldRiskPrompt(snapshot, signals);
        return geminiClient.generateContent(prompt)
                .map(this::sanitizeResponse)
                .orElse(FALLBACK_MESSAGE);
    }

    private String buildGoldRiskPrompt(GoldRiskSnapshot snapshot, List<Signal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain the current gold risk level clearly and neutrally.\n\n");
        sb.append("Overall Gold Risk Level: ").append(snapshot.getRiskLevel()).append("\n");
        sb.append("Aggregation Reason: ").append(snapshot.getReason()).append("\n");
        sb.append("As of: ").append(snapshot.getAsOfDate()).append("\n\n");

        sb.append("Contributing Indicator Signals:\n");
        for (Signal signal : signals) {
            sb.append("- ").append(signal.getIndicator().getName()).append(": ");
            sb.append(signal.getSignalType()).append(" (");
            sb.append(signal.getReason()).append(")\n");
        }

        sb.append("\nQuestion: Explain why the gold risk level is ").append(snapshot.getRiskLevel());
        sb.append(" based on these macro indicators. What does this mean for gold markets?\n\n");
        sb.append("Keep your response under 200 words. Use plain text only, no markdown or emojis.");

        return sb.toString();
    }

    /**
     * Removes any markdown formatting or emojis from the response.
     */
    private String sanitizeResponse(String response) {
        if (response == null) {
            return FALLBACK_MESSAGE;
        }

        return response
                .replaceAll("[*_#`~]", "")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("[\\p{So}\\p{Cn}]", "")
                .trim();
    }
}
