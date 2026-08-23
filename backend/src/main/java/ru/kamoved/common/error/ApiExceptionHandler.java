package ru.kamoved.common.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.kamoved.journal.application.InvalidOrderException;
import ru.kamoved.journal.application.InvalidPaymentException;
import ru.kamoved.journal.application.OrderNotFoundException;
import ru.kamoved.journal.application.OrderVersionConflictException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return new ApiError("VALIDATION_ERROR", "Проверьте заполненные поля", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleConstraintViolation(ConstraintViolationException exception) {
        return new ApiError("VALIDATION_ERROR", "Некорректные параметры запроса", Map.of());
    }

    @ExceptionHandler(InvalidOrderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleInvalidOrder(InvalidOrderException exception) {
        return new ApiError("INVALID_ORDER", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidPaymentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleInvalidPayment(InvalidPaymentException exception) {
        return new ApiError("INVALID_PAYMENT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleOrderNotFound(OrderNotFoundException exception) {
        return new ApiError("ORDER_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler({
        OrderVersionConflictException.class,
        ObjectOptimisticLockingFailureException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleOrderVersionConflict(RuntimeException exception) {
        String message = exception instanceof OrderVersionConflictException
            ? exception.getMessage()
            : "Заказ уже изменён другим пользователем. Обновите журнал и повторите действие";
        return new ApiError("ORDER_VERSION_CONFLICT", message, Map.of());
    }
}
