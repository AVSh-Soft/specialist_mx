package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Устройство памяти "Порт для установки цвета фона/изображения на экране Specialist_MX".
 * @author -=AVSh=-
 */
final class cMD_SpMX_ColorPort implements IMemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 1;

    private int fCurrentColor;
    private final cMD_SpMX_Screen fScreen;

    cMD_SpMX_ColorPort(@NotNull cMD_SpMX_Screen screen) {
        fScreen     = screen;
        fCurrentColor = 0xF0; // CL_WHITE / CL_BLACK по умолчанию
    }

    @Override
    public int getMemoryDeviceLength() {
        return MEMORY_DEVICE_LENGTH;
    }

    @Override
    public int readByte(int address) {
        if ((address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            return fCurrentColor;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            fCurrentColor =  value ;
            fScreen.setColor(value); // Устанавливает цвет фона/изображения экрана
        }
    }

    @Override
    public void reset(boolean clear) {
        fCurrentColor = 0xF0; // CL_WHITE / CL_BLACK по умолчанию
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        cMD_SpMX_ColorPort that = (cMD_SpMX_ColorPort) o;
        return Objects.equals(this.fScreen, that.fScreen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fScreen);
    }
}