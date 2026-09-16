package com.recallai.repository;

import java.sql.Date;

/** Cards due on one calendar day. */
public interface DueCount {

    Date getDay();

    long getCards();
}
