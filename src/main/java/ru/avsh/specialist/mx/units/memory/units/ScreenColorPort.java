package ru.avsh.specialist.mx.units.memory.units;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.Objects;

/**
 * Адресуемое устройство "Порт для установки цвета фона/изображения на экране 'Специалиста MX'".
 *
 * @author -=AVSh=-
 */
public final class ScreenColorPort implements MemoryUnit {
    private static final int STORAGE_SIZE = 1;

    private final Screen fScreen;

    private int fCurrentColor;

    /**
     * Конструктор.
     *
     * @param screen ссылка на объект класса Screen - "Экран 'Специалиста MX'"
     */
    public ScreenColorPort(@NotNull Screen screen) {
        fScreen       = screen;
        fCurrentColor = Screen.DEFAULT_COLOR;
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public int readByte(int address) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            return fCurrentColor;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            fCurrentColor =  value ;
            fScreen.setColor(value); // Устанавливает цвет фона/изображения экрана
        }
    }

    @Override
    public void reset(boolean clear) {
        fCurrentColor = Screen.DEFAULT_COLOR;
    }

    @Override
    public boolean equals(Object o) {
        if (this ==  o) return true;
        if ((o == null) || (getClass() != o.getClass())) return false;
        ScreenColorPort that = (ScreenColorPort) o;
        return Objects.equals(this.fScreen, that.fScreen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fScreen);
    }
}