package ru.avsh.specialist.mx.gui.swing.debugger.types;

/**
 * Типы страницы памяти в отладчике:
 * CPU  - страница, в которой работает CPU;
 * CODE - страница, в которой просматривается код;
 * DATA - страница, в которой просматриваются данные.
 */
public enum MemoryPageType {
    CPU, CODE, DATA
}
