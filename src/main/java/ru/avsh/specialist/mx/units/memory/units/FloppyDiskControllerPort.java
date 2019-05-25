package ru.avsh.specialist.mx.units.memory.units;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.Objects;

/**
 * Адресуемое устройство "Дополнительные порты контроллера НГМД"
 *
 * @author -=AVSh=-
 */
public final class FloppyDiskControllerPort implements MemoryUnit {
    private static final int STORAGE_SIZE = 4;

    private final FloppyDiskController fFDC;

    /**
     * Конструктор.
     *
     * @param fdc ссылка на объект класса FloppyDiskController - "Контроллер НГМД КР1818ВГ93 (FD1793-02)"
     */
    public FloppyDiskControllerPort(@NotNull FloppyDiskController fdc) {
        fFDC = fdc;
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            switch (address) {
                case 0: // Порт синхронизации CPU с контроллером НГМД
                    fFDC.waitDataRequest();
                    break;
                case 1: // Порт переключения плотности дискет (игнорируем)
                    break;
                case 2: // Порт переключения стороны диска
                    fFDC.setSide((value & 1) != 0);
                    break;
                case 3: // Порт переключения дисководов
                    fFDC.switchFDD((value & 1) != 0);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FloppyDiskControllerPort that = (FloppyDiskControllerPort) o;
        return Objects.equals(fFDC, that.fFDC);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fFDC);
    }
}