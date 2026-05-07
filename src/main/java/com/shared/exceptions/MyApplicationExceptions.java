package com.shared.exceptions;

public class MyApplicationExceptions {
    private MyApplicationExceptions() {}

    public static abstract class AppException extends RuntimeException {
        AppException(String message) { super(message); }
    }

    public static class BadRequestException extends AppException {
        public BadRequestException(String message) { super(message); }
    }

    public static class UnauthorizedException extends AppException {
        public UnauthorizedException(String message) { super(message); }
    }

    public static class ForbiddenException extends AppException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class NotFoundException extends AppException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends AppException {
        public ConflictException(String message) { super(message); }
    }
}
