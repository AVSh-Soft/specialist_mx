package ru.avsh.specialist.mx.units.memory.sub;

import javafx.animation.AnimationTimer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Адресуемое устройство "Экран 'Специалиста MX'".
 *
 * @author -=AVSh=-
 */
public class ScreenFx extends WritableImage implements MemoryUnit {
    // Таблица цветов A_R_G_B
    private static final int[] COLORS = {
            0xFF_00_00_00, 0xFF_00_00_80, 0xFF_00_80_00, 0xFF_00_80_80,
            0xFF_80_00_00, 0xFF_80_00_80, 0xFF_80_80_00, 0xFF_C0_C0_C0,
            0xFF_80_80_80, 0xFF_00_00_FF, 0xFF_00_FF_00, 0xFF_00_FF_FF,
            0xFF_FF_00_00, 0xFF_FF_00_FF, 0xFF_FF_FF_00, 0xFF_FF_FF_FF
    };

    public static final int SCREEN_WIDTH  =  384;
    public static final int SCREEN_HEIGHT =  256;
    public static final int DEFAULT_COLOR = 0xF0; // CL_WHITE / CL_BLACK по умолчанию

    private static final int BUFFER_SIZE  = SCREEN_HEIGHT * SCREEN_WIDTH;
    private static final int STORAGE_SIZE = BUFFER_SIZE >> 3;

    private volatile boolean fEnable;
    private volatile byte    fFgColor;
    private volatile byte    fBgColor;

    private final byte[] fImageBuffer;
    private final AtomicBoolean fChanges;
    private final AnimationTimer fAnimationTimer;

    public ScreenFx() {
        super(SCREEN_WIDTH, SCREEN_HEIGHT);

        fImageBuffer = new byte[BUFFER_SIZE];
            fChanges = new AtomicBoolean(false);

        setColor(DEFAULT_COLOR);

        fEnable = true;

        final PixelWriter             pixelWriter = this.getPixelWriter();
        final PixelFormat<ByteBuffer> pixelFormat = PixelFormat.createByteIndexedInstance(COLORS);

        fAnimationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (fChanges.getAndSet(false)) {
                    pixelWriter.setPixels(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, pixelFormat, fImageBuffer, 0, SCREEN_WIDTH);
                }
            }
        };
        fAnimationTimer.start();
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public void writeByte(int address, int value) {
        if (fEnable && (address >= 0) && (address < STORAGE_SIZE)) {
            fChanges.getAndSet(true);

            for (int idx = (SCREEN_WIDTH * (address & 0xFF)) + ((address >> 5) & 0xFFFFFFF8), end = idx + 8, mask = 0x80; idx < end; idx++, mask >>= 1) {
                fImageBuffer[idx] = ((value & mask) != 0) ? fFgColor : fBgColor;
            }
        }
    }

    @Override
    public void reset(boolean clear) {
        setColor(DEFAULT_COLOR);

        fEnable = true;
        fChanges.getAndSet(false);

        if (clear) {
            Arrays.fill(fImageBuffer, (byte) 0);
        }
    }

    @Override
    public void close() {
        fAnimationTimer.stop();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        ScreenFx screenFx = (ScreenFx) o;
        return (fFgColor == screenFx.fFgColor) && (fBgColor == screenFx.fBgColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fFgColor, fBgColor);
    }

    /**
     * Устанавливает цвет изображения/фона.
     *
     * @param color старший полубайт - цвет изображения, младший полубайт - цвет фона
     */
    public void setColor(int color) {
        fFgColor = (byte) ((color & 0xF0) >> 4);
        fBgColor = (byte)  (color & 0x0F);
    }

    /**
     * Отключает/включает вывод на экран.
     *
     * @param enable true - включает, false - отключает
     */
    void setEnable(boolean enable) {
        fEnable = enable;
    }
}
