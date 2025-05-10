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

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private ItemService itemService;
    private EmailValidatorRegex emailValidatorReg;

    public ItemController(ItemService itemService, EmailValidatorRegex emailValidator) {
        this.itemService = itemService;
        this.emailValidatorReg = emailValidator;
    }

    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        return new ResponseEntity<>(itemService.findAll(), HttpStatus.OK);
    }


    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody Item item, BindingResult result) {
        if (result.hasErrors()) {
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


    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Long id) {
        Item item =  itemService.findById(id)
                .orElseThrow(() -> new ItemNotFoundException(id));
        return ResponseEntity.ok(item);
    }


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

        BeanUtils.copyProperties(item, existingItem, "id");
        Item updated = itemService.save(existingItem);
        return ResponseEntity.ok(updated);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        itemService.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/process")
    public CompletableFuture<ResponseEntity<List<Item>>> processItems() {
        return itemService.processItemsAsync()
                .thenApply(ResponseEntity::ok);
    }
}
