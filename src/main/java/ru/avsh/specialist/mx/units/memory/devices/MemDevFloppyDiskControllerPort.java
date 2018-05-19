package ru.avsh.specialist.mx.units.memory.devices;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Устройство памяти "Дополнительные порты контроллера НГМД"
 *
 * @author -=AVSh=-
 */
public final class MemDevFloppyDiskControllerPort implements IMemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 4;

    private final MemDevFloppyDiskController fFDC;

    /**
     * Конструктор.
     *
     * @param fdc ссылка на объект класса MemDevFloppyDiskController - "Контроллер НГМД КР1818ВГ93 (FD1793-02)"
     */
    public MemDevFloppyDiskControllerPort(@NotNull MemDevFloppyDiskController fdc) {
        fFDC = fdc;
    }

    @Override
    public int getMemoryDeviceLength() {
        return MEMORY_DEVICE_LENGTH;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
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
        MemDevFloppyDiskControllerPort that = (MemDevFloppyDiskControllerPort) o;
        return Objects.equals(fFDC, that.fFDC);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fFDC);
    }
}