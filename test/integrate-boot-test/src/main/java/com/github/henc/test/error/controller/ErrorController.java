package com.github.henc.test.error.controller;

import com.github.henc.integrateboot.exception.BusinessException;
import com.github.henc.integrateboot.exception.ConflictException;
import com.github.henc.integrateboot.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Error-demo controller exercising the global exception handler: one endpoint per failure
 * kind, thrown straight from the controller (the handler catches service-layer throws the
 * same way).
 */
@RestController
@RequestMapping("/errors")
public class ErrorController {

    /**
     * Request body for the validation demo.
     */
    public record CreateUserRequest(@NotBlank String name, @Max(150) int age) {
    }

    @GetMapping("/business")
    public String business() {
        throw new BusinessException("insufficient balance");
    }

    @GetMapping("/not-found")
    public String notFound() {
        throw new NotFoundException("order 1 not found");
    }

    @GetMapping("/conflict")
    public String conflict() {
        throw new ConflictException(10003, "duplicate order id");
    }

    @PostMapping("/validate")
    public String validate(@Valid @RequestBody CreateUserRequest request) {
        return request.name();
    }

    @GetMapping("/unexpected")
    public String unexpected() {
        throw new IllegalStateException("boom");
    }
}
