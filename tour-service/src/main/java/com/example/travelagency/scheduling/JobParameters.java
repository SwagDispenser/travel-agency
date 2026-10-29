package com.example.travelagency.scheduling;

import org.quartz.JobDataMap;

public final class JobParameters {
    private JobParameters() {
    }

    public static String requiredText(JobDataMap data, String key) {
        Object value = data.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Job parameter '" + key + "' must be a nonblank string");
        }
        return text.trim();
    }

    public static int nonNegativeInt(JobDataMap data, String key) {
        String text = requiredText(data, key);
        try {
            int value = Integer.parseInt(text);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Job parameter '" + key + "' must be a nonnegative integer");
    }
}
