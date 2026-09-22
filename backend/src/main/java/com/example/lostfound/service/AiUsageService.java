package com.example.lostfound.service;

public interface AiUsageService {

    /** Returns false when the caller has used all requests for the current day. */
    boolean tryConsume(Long userId);
}
