package ru.avsh.specialistmx;

/**
 * Интерфейс "Тактируемое устройство".
 *
 * @author -=AVSh=-
 */
interface IClockedDevice {
    /**
     * Выполняет один такт.
     *
     * @return true/false - конечное/промежуточное состояние устройства
     */
    boolean cycle();
}
