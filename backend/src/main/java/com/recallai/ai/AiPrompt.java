package com.recallai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything one model call needs. Immutable; a corrective follow-up is a new prompt that
 * extends the conversation rather than a mutation of this one.
 *
 * @param operation     what is being generated (cache key component, logging)
 * @param promptVersion version of the template that produced this prompt (cache key component)
 * @param system        the system prompt
 * @param messages      alternating user/assistant turns, always ending with a user turn
 * @param outputSchema  JSON Schema the response must satisfy; sent as a structured-output constraint
 */
public record AiPrompt(
        AiOperation operation,
        String promptVersion,
        String system,
        List<Message> messages,
        JsonNode outputSchema) {

    public enum Role { USER, ASSISTANT }

    public record Message(Role role, String content) {
    }

    public static AiPrompt of(AiOperation operation, String promptVersion, String system, String user,
                              JsonNode outputSchema) {
        return new AiPrompt(operation, promptVersion, system, List.of(new Message(Role.USER, user)), outputSchema);
    }

    /** Continues the conversation with the model's rejected reply and a correction request. */
    public AiPrompt withCorrection(String rejectedReply, String correction) {
        List<Message> extended = new ArrayList<>(messages);
        extended.add(new Message(Role.ASSISTANT, rejectedReply));
        extended.add(new Message(Role.USER, correction));
        return new AiPrompt(operation, promptVersion, system, List.copyOf(extended), outputSchema);
    }
}
