package ru.kamoved.journal.application;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Заказ не найден");
    }
}
