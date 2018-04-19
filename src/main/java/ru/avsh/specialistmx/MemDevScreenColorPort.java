package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Устройство памяти "Порт для установки цвета фона/изображения на экране 'Специалиста MX'".
 *
 * @author -=AVSh=-
 */
final class MemDevScreenColorPort implements IMemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 1;

    private int fCurrentColor;
    private final MemDevScreen fScreen;

    /**
     * Конструктор.
     *
     * @param screen ссылка на объект класса MemDevScreen - "Экран 'Специалиста MX'"
     */
    MemDevScreenColorPort(@NotNull MemDevScreen screen) {
        fScreen       = screen;
        fCurrentColor = MemDevScreen.DEFAULT_COLOR;
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
        fCurrentColor = MemDevScreen.DEFAULT_COLOR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemDevScreenColorPort that = (MemDevScreenColorPort) o;
        return Objects.equals(this.fScreen, that.fScreen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fScreen);
    }
}