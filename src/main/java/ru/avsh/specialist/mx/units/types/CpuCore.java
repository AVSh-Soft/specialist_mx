package ru.avsh.specialist.mx.units.types;

/**
 * Интерфейс "Процессор" — общий контракт для всех реализаций CPU эмулятора.
 * Содержит только то, что нужно ядру, тактовому генератору и устройствам:
 * управление исполнением, пауза (HOLD) и минимальный набор методов для отладчика.
 *
 * @author -=AVSh=-
 */
public interface CpuCore extends ClockedUnit {
    /**
     * Возвращает название CPU.
     * @return название CPU
     */
    String getName();

    /**
     * Показывает, активен ли процессор.
     *
     * @return true = процессор активен
     */
    boolean isActive();

    /**
     * Включает или выключает процессор.
     * Неактивный процессор находится в массиве тактируемых устройств, но не исполняет команды.
     *
     * @param active true = процессор активен
     */
    void setActive(final boolean active);

    /**
     * Выполняет запуск с заданного адреса.
     *
     * @param address адрес запуска
     */
    void run(int address);

    /**
     * Выполняет сброс процессора.
     *
     * @param address          адрес запуска
     * @param resetMemoryUnits true - выполняет сброс запоминающих устройств
     */
    void reset(int address, boolean resetMemoryUnits);

    /**
     * Переводит запоминающие устройства и устройства ввода/вывода, подключенные к процессору, в режим "Пауза".
     *
     * @param mode true/false = установить/снять режим "Пауза"
     */
    void pauseMemoryUnits(boolean mode);

    /**
     * Переводит процессор в режим ожидания (выполняется захват "HOLD").
     *
     * @param mode true/false = установить/снять режим "HOLD"
     */
    void hold(boolean mode);

    /**
     * Показывает установлен ли режим "HOLD" для процессора.
     *
     * @return true = установлен режим "HOLD"
     */
    boolean isHoldAcknowledge();

    /**
     * Возвращает состояние регистровой пары, общей для всех процессоров эмулятора.
     * Раскладка пары в возвращаемом значении: старший байт - первый регистр пары, младший байт - второй
     * (BC, DE, HL; для AF - старший байт A, младший F; SP и PC - адрес целиком).
     *
     * @param commonPair индекс регистровой пары: 0 - BC, 2 - DE, 4 - HL, 6 - AF, 8 - SP, 9 - PC
     * @return значение регистровой пары
     */
    int getCommonRegPair(int commonPair);

    /**
     * Устанавливает состояние регистровой пары, общей для всех процессоров эмулятора.
     * Регистры, которых нет у I8080 (IX, IY и т.п.), не затрагиваются.
     *
     * @param commonPair индекс регистровой пары: 0 - BC, 2 - DE, 4 - HL, 6 - AF, 8 - SP, 9 - PC
     * @param value      значение регистровой пары
     */
    void setCommonRegPair(int commonPair, int value);
}
