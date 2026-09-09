package com.recallai.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Scripted stand-in for the Claude API. Tests enqueue replies (text or exceptions) and can
 * inspect every prompt that was sent. Never touches the network.
 */
public class FakeClaudeClient implements ClaudeClient {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<AiPrompt> prompts = new ArrayList<>();

    public FakeClaudeClient reply(String text) {
        script.add(AiCompletion.ofText(text));
        return this;
    }

    public FakeClaudeClient reply(AiCompletion completion) {
        script.add(completion);
        return this;
    }

    public FakeClaudeClient fail(AiUnavailableException exception) {
        script.add(exception);
        return this;
    }

    public List<AiPrompt> prompts() {
        return List.copyOf(prompts);
    }

    public int calls() {
        return prompts.size();
    }

    public void reset() {
        script.clear();
        prompts.clear();
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        prompts.add(prompt);
        Object next = script.poll();
        if (next == null) {
            throw new AssertionError("FakeClaudeClient has no scripted reply for call " + prompts.size());
        }
        if (next instanceof AiUnavailableException exception) {
            throw exception;
        }
        return (AiCompletion) next;
    }
}
