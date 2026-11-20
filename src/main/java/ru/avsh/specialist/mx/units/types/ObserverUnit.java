package ru.avsh.specialist.mx.units.types;

/**
 * Устройство-наблюдатель.
 *
 * @author -=AVSh=-
 */
public interface ObserverUnit extends Unit {
    /**
     * Обновить устройство.
     */
    void update();
}
