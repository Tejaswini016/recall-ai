package com.recallai.repository;

/** A topic with how many rows carry it. */
public interface TopicCount {

    String getTopic();

    long getCount();
}
