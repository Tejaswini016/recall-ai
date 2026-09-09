package com.recallai.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.NOT_FOUND, resource + " " + id + " was not found");
    }
}
