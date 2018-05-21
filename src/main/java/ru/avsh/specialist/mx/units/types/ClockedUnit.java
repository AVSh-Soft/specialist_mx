package ru.avsh.specialist.mx.units.types;

/**
 * Интерфейс "Тактируемое устройство".
 *
 * @author -=AVSh=-
 */
public interface ClockedUnit extends Unit {
    /**
     * Выполняет один такт.
     *
     * @return true/false - конечное/промежуточное состояние устройства
     */
    boolean cycle();
}
