package com.recallai.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Shared pagination bounds for list endpoints. */
final class Paging {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private Paging() {
    }

    static Pageable of(int page, int size) {
        return PageRequest.of(page, size);
    }
}
