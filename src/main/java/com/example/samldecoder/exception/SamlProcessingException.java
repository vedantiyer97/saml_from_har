package com.example.samldecoder.exception;

public class SamlProcessingException extends RuntimeException {
    public SamlProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
} 