package org.apache.flink.agents.demo;

import org.apache.flink.agents.api.Agent;
import org.apache.flink.agents.api.annotation.Action;
import org.apache.flink.agents.api.annotation.ChatModelSetup;
import org.apache.flink.agents.api.annotation.Prompt;
import org.apache.flink.agents.api.annotation.Tool;
import org.apache.flink.agents.api.annotation.ToolParam;
import org.apache.flink.agents.api.chat.ChatMessage;
import org.apache.flink.agents.api.chat.Message;
import org.apache.flink.agents.api.event.ChatRequestEvent;
import org.apache.flink.agents.api.event.ChatResponseEvent;
import org.apache.flink.agents.api.event.InputEvent;
import org.apache.flink.agents.api.event.OutputEvent;
import org.apache.flink.agents.api.resource.Prompt;
import org.apache.flink.agents.api.resource.ResourceDescriptor;
import org.apache.flink.agents.api.runtime.RunnerContext;
import org.apache.flink.agents.integrations.chatmodel.ollama.OllamaChatModelSetup;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple sentiment analysis agent that processes text input
 * and returns sentiment with a score.
 */
public class SimpleSentimentAgent extends Agent {

    private static final Prompt SENTIMENT_PROMPT = new Prompt(
            "You are a sentiment analysis assistant. Analyze the given text and determine its sentiment.\n" +
            "Classify the sentiment as POSITIVE, NEGATIVE, or NEUTRAL.\n" +
            "Provide a confidence score between 0 and 1.\n" +
            "If you detect strong emotions, use the logEmotion tool to record them.\n\n" +
            "Always respond with your final answer in this exact format:\n" +
            "SENTIMENT: [POSITIVE/NEGATIVE/NEUTRAL]\n" +
            "SCORE: [0.0-1.0]\n" +
            "REASON: [brief explanation]"
    );

    @ChatModelSetup
    public static ResourceDescriptor sentimentModel() {
        return ResourceDescriptor.Builder
                .newBuilder(OllamaChatModelSetup.class.getName())
                .addInitialArgument("connection", "ollamaChatModelConnection")
                .addInitialArgument("model", "llama3.2:latest")
                .addInitialArgument("prompt", "sentimentPrompt")
                .build();
    }

    @Prompt
    public static Prompt sentimentPrompt() {
        return SENTIMENT_PROMPT;
    }

    @Tool(description = "Log strong emotions detected in the text. Use this when you detect anger, joy, sadness, or fear.")
    public static void logEmotion(
            @ToolParam(name = "emotion", description = "The type of emotion: ANGER, JOY, SADNESS, FEAR") String emotion,
            @ToolParam(name = "intensity", description = "Intensity from 1-10") int intensity,
            @ToolParam(name = "keywords", description = "Keywords that indicate this emotion") String keywords) {
        System.out.println(String.format(
                "[EMOTION DETECTED] Type: %s, Intensity: %d, Keywords: %s",
                emotion, intensity, keywords
        ));
    }

    @Action(listenEvents = {InputEvent.class})
    public static void processInput(InputEvent event, RunnerContext ctx) throws Exception {
        // Get the input text
        String inputText = (String) event.getData();

        System.out.println("[SimpleSentimentAgent] Processing text: " + inputText);

        // Create a chat request with the input text
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(Message.Role.USER, "Analyze this text: " + inputText));

        // Send the chat request to the LLM
        ctx.sendEvent(new ChatRequestEvent("sentimentModel", messages));
    }

    @Action(listenEvents = {ChatResponseEvent.class})
    public static void processChatResponse(ChatResponseEvent event, RunnerContext ctx) throws Exception {
        // Get the response from the LLM
        ChatMessage response = event.getMessage();
        String content = response.getContent();

        System.out.println("[SimpleSentimentAgent] LLM Response: " + content);

        // Parse the response and create a result object
        SentimentResult result = parseResponse(content);

        // Send the result as output
        ctx.sendEvent(new OutputEvent(result));
    }

    private static SentimentResult parseResponse(String content) {
        // Simple parsing logic
        String sentiment = "NEUTRAL";
        double score = 0.5;
        String reason = content;

        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("SENTIMENT:")) {
                    sentiment = line.substring("SENTIMENT:".length()).trim();
                } else if (line.startsWith("SCORE:")) {
                    score = Double.parseDouble(line.substring("SCORE:".length()).trim());
                } else if (line.startsWith("REASON:")) {
                    reason = line.substring("REASON:".length()).trim();
                }
            }
        } catch (Exception e) {
            System.err.println("[SimpleSentimentAgent] Error parsing response: " + e.getMessage());
        }

        return new SentimentResult(sentiment, score, reason);
    }

    /**
     * Result object for sentiment analysis.
     */
    public static class SentimentResult {
        public String sentiment;
        public double score;
        public String reason;

        public SentimentResult(String sentiment, double score, String reason) {
            this.sentiment = sentiment;
            this.score = score;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("SentimentResult{sentiment='%s', score=%.2f, reason='%s'}",
                    sentiment, score, reason);
        }
    }
}
