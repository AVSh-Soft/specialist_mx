package ru.avsh.specialist.mx.helpers;

import org.jetbrains.annotations.NotNull;

/**
 * Класс "Ловушка".
 */
public final class Trap implements Comparable<Trap> {
    private int fValue;

    /**
     * Конструктор.
     *
     * @param page    номер страницы памяти
     * @param address адрес
     */
    public Trap(int page, int address) {
        fValue = (page << 16) | address;
    }

    /**
     * Возвращает номер страницы памяти.
     *
     * @return номер страницы памяти
     */
    public int getPage() {
        return fValue >> 16;
    }

    /**
     * Возвращает адрес.
     *
     * @return адрес
     */
    public int getAddress() {
        return fValue & 0xFFFF;
    }

    /**
     * Изменяет ловушку.
     *
     * @param page    номер страницы памяти
     * @param address адрес
     */
    public void change(int page, int address) {
        fValue = (page << 16) | address;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Trap) && (fValue == ((Trap) obj).fValue);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(fValue);
    }

    @Override
    public int compareTo(@NotNull Trap anotherTrap) {
        return Integer.compare(this.fValue, anotherTrap.fValue);
    }
}
