package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Устройство памяти "Дополнительные порты контроллера НГМД"
 * @author -=AVSh=-
 */
final class cMD_SpMX_FDC_Port implements IMemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 4;

    private final cMD_SpMX_FDC fMainFDC;

    cMD_SpMX_FDC_Port(@NotNull cMD_SpMX_FDC mainFDC) {
        fMainFDC = mainFDC;
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
                    fMainFDC.waitDataRequest();
                    break;
                case 1: // Порт переключения плотности дискет (игнорируем)
                    break;
                case 2: // Порт переключения стороны диска
                    fMainFDC.setSide((value & 1) != 0);
                    break;
                case 3: // Порт переключения дисководов
                    fMainFDC.switchFDD((value & 1) != 0);
                    break;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        cMD_SpMX_FDC_Port that = (cMD_SpMX_FDC_Port) o;
        return Objects.equals(fMainFDC, that.fMainFDC);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fMainFDC);
    }
}