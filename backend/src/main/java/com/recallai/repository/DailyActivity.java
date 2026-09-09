package com.recallai.repository;

import java.sql.Date;

/** Review counts and quality for one calendar day (UTC). */
public interface DailyActivity {

    Date getDay();

    long getReviews();

    long getSuccessful();

    Double getAverageQuality();
}
