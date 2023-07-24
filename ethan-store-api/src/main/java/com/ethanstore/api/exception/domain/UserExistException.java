package com.ethanstore.api.exception.domain;

public class UserExistException extends Exception {
    public UserExistException(String message) {
        super(message);
    }
}
