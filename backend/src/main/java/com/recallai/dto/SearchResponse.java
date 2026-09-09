package com.recallai.dto;

import java.util.List;

public record SearchResponse(List<DeckResponse> decks, List<CardResponse> cards) {
}
