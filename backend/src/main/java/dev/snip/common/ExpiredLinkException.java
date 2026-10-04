package dev.snip.common;

public class ExpiredLinkException extends RuntimeException {
    public ExpiredLinkException(String message) { super(message); }
}

