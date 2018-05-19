package ru.avsh.specialist.mx.units;

/**
 * Интерфейс "Тактируемое устройство".
 *
 * @author -=AVSh=-
 */
public interface IClockedDevice {
    /**
     * Выполняет один такт.
     *
     * @return true/false - конечное/промежуточное состояние устройства
     */
    boolean cycle();
}
