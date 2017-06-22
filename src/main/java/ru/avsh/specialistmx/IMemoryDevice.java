package ru.avsh.specialistmx;

/**
 * Интерфейс "Устройство памяти".
 * @author -=AVSh=-
 */
interface IMemoryDevice {
    /**
     * Возвращает длину устройства памяти в байтах.
     * @return длина устройства памяти
     */
    int getMemoryDeviceLength();

    /**
     * Читает байт по заданному адресу из устройства памяти (адрес всегда относительный).
     * @param address относительный адрес
     * @return считанный байт (байт представлен как int)
     */
    default int readByte(int address) {
        return -1;
    }

    /**
     * Читает байт по заданному адресу из устройства памяти (адрес всегда относительный).
     * (Метод для вызова из отладчика - минимизирует влияние отладчика на работу устройства памяти)
     * @param address относительный адрес
     * @return считанный байт (байт представлен как int)
     */
    default int debugReadByte(int address) {
        return readByte(address);
    }

    /**
     * Пишет байт по заданному адресу в устройство памяти (адрес всегда относительный).
     * @param address относительный адрес
     * @param value   записываемый байт (байт представлен как int)
     */
    void writeByte(int address, int value);

    /**
     * Сбрасывает устройство памяти в начальное состояние.
     * @param clear true - очистка буферов устройства памяти (если необходимо)
     */
    default void reset(boolean clear) {
    }

    /**
     * Переводит устройство памяти в режим "Пауза".
     * @param mode - true = установить режим "Пауза" / false = снять режим "Пауза"
     */
    default void pause(boolean mode) {
    }

    /**
     * Закрывает ресурсы устройства памяти.
     */
    default void close() {
    }
}