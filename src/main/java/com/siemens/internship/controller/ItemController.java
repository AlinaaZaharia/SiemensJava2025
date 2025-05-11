package com.siemens.internship.controller;

import com.siemens.internship.exceptions.ItemNotFoundException;
import com.siemens.internship.model.Item;
import com.siemens.internship.service.ItemService;
import com.siemens.internship.validator.EmailValidatorRegex;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * I fixed swapped HTTP status codes in createItem (was returning 201 on error and 400 on success)
 * I added a field -> message map for validation errors so clients can get detailed feedback
 * I made the GET/{id}, PUT/{id}, and DELETE endpoints throw ItemNotFoundException instead of returning NO_CONTENT, to correctly send 404
 * I changed 'updateItem' method to use BeanUtils.copyProperties so I only overwrite mutable fields and keep the ID intact
 * I added proper Location header on POST by building a URI with ServletUriComponentsBuilder
 * I exposed processItems as CompletableFuture<ResponseEntity<…>> so Spring waits until all items finish processing.
 */


@RestController
@RequestMapping("/api/items")
public class ItemController {

    private ItemService itemService;
    private EmailValidatorRegex emailValidatorReg;

    public ItemController(ItemService itemService, EmailValidatorRegex emailValidator) {
        this.itemService = itemService;
        this.emailValidatorReg = emailValidator;
    }

    // 200 OK with full list
    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        return new ResponseEntity<>(itemService.findAll(), HttpStatus.OK);
    }

    // 201 created or 400 bad request with error details
    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody Item item, BindingResult result) {
        if (result.hasErrors()) {
            // I used a map to collect each field's error message so the client can see exactly which field failed
            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(
                    FieldError::getField,
                    FieldError::getDefaultMessage
            ));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
        }

        try{
            emailValidatorReg.validate(item);
        } catch(IllegalArgumentException ex){
            return ResponseEntity.badRequest()
                    .body(Map.of("email", ex.getMessage()));
        }

        Item savedItem = itemService.save(item);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(savedItem.getId()).toUri();
        return ResponseEntity.created(uri).body(savedItem);
    }


    // 200 OK or 404 not found
    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Long id) {
        Item item =  itemService.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
        return ResponseEntity.ok(item);
    }


    // 200 OK, 400 bad request or 404 not found
    @PutMapping("/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @Valid @RequestBody Item item, BindingResult result) {
        Item existingItem = itemService.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));

        if (result.hasErrors()) {
            Map<String, String> errors = result.getFieldErrors().stream().collect(Collectors.toMap(
                    FieldError::getField,
                    FieldError::getDefaultMessage
            ));
            return ResponseEntity.badRequest().body(errors);
        }

        try{
            emailValidatorReg.validate(item);
        } catch(IllegalArgumentException ex){
            return ResponseEntity.badRequest()
                    .body(Map.of("email", ex.getMessage()));
        }

        // I used BeanUtils to copy all updatable fields from incoming item onto the existing one, keeping the ID unchanged
        BeanUtils.copyProperties(item, existingItem, "id");

        Item updated = itemService.save(existingItem);
        return ResponseEntity.ok(updated);
    }


    // 204 no content or 404 not found
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        itemService.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // 200 OK when async process is completed
    @GetMapping("/process")
    public CompletableFuture<ResponseEntity<List<Item>>> processItems() {
        return itemService.processItemsAsync()
                .thenApply(ResponseEntity::ok);
    }
}