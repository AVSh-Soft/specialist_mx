package ru.avsh.specialistmx;

/**
 * Интерфейс "Тактируемое устройство".
 * @author -=AVSh=-
 */
interface iClockedDevice {
    /**
     * Выполняет один такт.
     * @return true/false - конечное/промежуточное состояние устройства
     */
    boolean cycle();
}
