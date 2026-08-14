package ru.kamoved.journal.application;

public class OrderVersionConflictException extends RuntimeException {

    public OrderVersionConflictException() {
        super("Заказ уже изменён другим пользователем. Обновите журнал и повторите действие");
    }
}
