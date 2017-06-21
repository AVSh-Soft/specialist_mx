package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Устройство памяти "Порт для управления страницами памяти Specialist_MX".
 * @author -=AVSh=-
 */
final class cMD_SpMX_RAM_Port implements MemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 3;

    private final cMD_SpMX_RAM fRAM;

    cMD_SpMX_RAM_Port(@NotNull cMD_SpMX_RAM ram) {
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
                    value = cMD_SpMX_RAM.ROM_DISK;
                    break;
            }
            // Переключает страницы памяти
            fRAM.setPage(value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        cMD_SpMX_RAM_Port that = (cMD_SpMX_RAM_Port) o;
        return Objects.equals(this.fRAM, that.fRAM);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fRAM);
    }
}