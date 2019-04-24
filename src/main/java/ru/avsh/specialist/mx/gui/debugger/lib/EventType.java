package ru.avsh.specialist.mx.gui.debugger.lib;

/**
 * Тип события:
 * REG_PAIR - изменилась регистровая пара;
 * TRAPS    - изменились ловушки;
 * MEMORY   - изменилась память;
 * PAGE     - изменилась страница памяти;
 * STEP     - выполнен шаг CPU (изменилось всё).
 */
public enum EventType {
    REG_PAIR, TRAPS, MEMORY, PAGE, STEP
}
