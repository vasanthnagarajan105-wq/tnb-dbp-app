package com.tnb.dbp.common;

import java.time.Instant;

/** Standard error body for every failed API call. */
public record ApiError(Instant timestamp, int status, String code, String message, String path) {
}
