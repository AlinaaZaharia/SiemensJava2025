package com.siemens.internship.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * I added @NotBlank on name, status, and email in order to never get empty values
 * Any email format checks happen in EmailValidatorRegex ('validator' package)
*/

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotBlank(message="Name should not be blank.")
    private String name;

    private String description;

    @NotBlank(message="Status should not be blank.")
    private String status;

    @NotBlank(message="Email should not be blank.")
    private String email;
}