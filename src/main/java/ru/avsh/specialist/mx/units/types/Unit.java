package ru.avsh.specialist.mx.units.types;

/**
 * Интерфейс "Устройство".
 *
 * @author -=AVSh=-
 */
public interface Unit {
    /**
     * Сбрасывает устройство в начальное состояние.
     *
     * @param clear true - очистка буферов устройства (если необходимо)
     */
    default void reset(boolean clear) {
    }

    /**
     * Переводит устройство в режим "Пауза".
     *
     * @param mode - true = установить режим "Пауза" / false = снять режим "Пауза"
     */
    default void pause(boolean mode) {
    }

    /**
     * Закрывает ресурсы устройства.
     */
    default void close() {
    }
}
