package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Устройство памяти "Порт для управления страницами памяти 'Специалист MX'".
 * @author -=AVSh=-
 */
final class MemDevMainMemoryPort implements IMemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 3;

    private final MemDevMainMemory fRAM;

    /**
     * Конструктор.
     * @param ram ссылка на объект класса MemDevMainMemory - "Оперативная память 'Специалист MX'"
     */
    MemDevMainMemoryPort(@NotNull MemDevMainMemory ram) {
        fRAM = ram;
    }

    @Override
    public int getMemoryDeviceLength() {
        return MEMORY_DEVICE_LENGTH;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            switch (address) {
                case 0:  // 0 - основная память
                    value = 0;
                    break;
                case 1:  // от 1 до 8 - RAM-диск
                    value = (value & 0b111) + 1;
                    break;
                case 2:  // 9 - ROM-диск
                    value = MemDevMainMemory.ROM_DISK;
                    break;
                default:
                    return;
            }
            // Переключает страницы памяти
            fRAM.setPage(value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemDevMainMemoryPort that = (MemDevMainMemoryPort) o;
        return Objects.equals(this.fRAM, that.fRAM);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fRAM);
    }
}