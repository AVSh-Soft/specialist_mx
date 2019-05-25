package ru.avsh.specialist.mx.units.memory.units;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.Objects;

/**
 * Адресуемое устройство "Порт для управления страницами памяти 'Специалист MX'".
 *
 * @author -=AVSh=-
 */
public final class MainMemoryPort implements MemoryUnit {
    private static final int STORAGE_SIZE = 3;

    private final MainMemory fRAM;

    /**
     * Конструктор.
     *
     * @param ram ссылка на объект класса MainMemory - "Оперативная память 'Специалист MX'"
     */
    public MainMemoryPort(@NotNull MainMemory ram) {
        fRAM = ram;
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            switch (address) {
                case 0:  // 0 - основная память
                    value = 0;
                    break;
                case 1:  // от 1 до 8 - RAM-диск
                    value = (value & 0b111) + 1;
                    break;
                case 2:  // 9 - ROM-диск
                    value = MainMemory.ROM_DISK;
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
        MainMemoryPort that = (MainMemoryPort) o;
        return Objects.equals(this.fRAM, that.fRAM);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fRAM);
    }
}