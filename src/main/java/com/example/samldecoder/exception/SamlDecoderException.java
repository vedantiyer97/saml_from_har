package com.example.samldecoder.exception;

public class SamlDecoderException extends RuntimeException {
    public SamlDecoderException(String message) {
        super(message);
    }

    public SamlDecoderException(String message, Throwable cause) {
        super(message, cause);
    }
} 