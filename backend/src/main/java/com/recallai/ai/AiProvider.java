package com.recallai.ai;

/**
 * Hosted model providers the backend can generate with. Selected by {@code AI_PROVIDER};
 * {@code AI_DEMO_MODE=true} overrides both with the local heuristic generator.
 */
public enum AiProvider {
    /** Anthropic Claude through the official Java SDK (default, used in production). */
    ANTHROPIC,
    /** Google Gemini through the official Java SDK; its Flash models have a free developer tier. */
    GEMINI,
    /** Groq's OpenAI-compatible API (free developer tier); GROQ_BASE_URL also fits any OpenAI-compatible server such as Ollama. */
    GROQ
}
