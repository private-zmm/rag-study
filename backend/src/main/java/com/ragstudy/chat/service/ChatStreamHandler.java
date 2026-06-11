package com.ragstudy.chat.service;

public interface ChatStreamHandler {

    void onDelta(String delta);
}
