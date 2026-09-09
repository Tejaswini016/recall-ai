package com.recallai.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param totalDue every card due today (may exceed the number returned when a limit applies)
 */
public record ReviewQueueResponse(LocalDate today, long totalDue, List<DueCardResponse> cards) {
}
