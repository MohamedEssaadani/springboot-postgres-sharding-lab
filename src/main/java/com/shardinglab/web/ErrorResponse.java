package com.shardinglab.web;

/** Error body. Never carries stack traces, SQL, JDBC URLs or credentials. */
public record ErrorResponse(int status, String error, String message) {
}
