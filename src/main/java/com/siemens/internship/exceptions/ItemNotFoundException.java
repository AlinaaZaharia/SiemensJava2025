package com.siemens.internship.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception to signal a missing Item by ID
 * annotated with @ResponseStatus so Spring returns 404 not found automatically
 */

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ItemNotFoundException extends RuntimeException{
    public ItemNotFoundException(Long id){
        super("Item with id " + id + " was not found!");
    }
}
