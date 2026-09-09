package com.recallai.service;

import java.util.ArrayList;
import java.util.List;

/** Shares a requested item count across text chunks in proportion to their length. */
final class GenerationPlanner {

    private GenerationPlanner() {
    }

    /**
     * @return one share per chunk, each at least 1 and at most {@code maxPerCall}
     */
    static List<Integer> shares(List<String> chunks, int count, int maxPerCall) {
        long total = chunks.stream().mapToLong(String::length).sum();
        List<Integer> shares = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            int proportional = total == 0 ? count : (int) Math.ceil(count * (double) chunk.length() / total);
            shares.add(Math.max(1, Math.min(proportional, maxPerCall)));
        }
        return shares;
    }
}
