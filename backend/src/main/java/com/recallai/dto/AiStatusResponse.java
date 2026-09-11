package com.recallai.dto;

/**
 * @param available whether generation endpoints will work at all
 * @param demoMode  true when output comes from local heuristics instead of Claude
 * @param model     the model id behind generation ("demo" in demo mode)
 */
public record AiStatusResponse(boolean available, boolean demoMode, String model) {
}
