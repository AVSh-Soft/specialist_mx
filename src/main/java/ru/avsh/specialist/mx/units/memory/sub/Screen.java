package ru.avsh.specialist.mx.units.memory.sub;

import ru.avsh.specialist.mx.units.types.MemoryUnit;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Адресуемое устройство "Экран 'Специалиста MX'".
 *
 * @author -=AVSh=-
 */
public final class Screen extends JPanel implements MemoryUnit {
    private static final long serialVersionUID = 1612671161249875581L;

    public  static final int   SCREEN_WIDTH  = 384;
    public  static final int   SCREEN_HEIGHT = 256;
    private static final float ASPECT_RATIO  = (float) SCREEN_WIDTH / (float) SCREEN_HEIGHT;

    private static final Color CL_BLACK   = new Color(0x00,0x00,0x00);
    private static final Color CL_NAVY    = new Color(0x00,0x00,0x80);
    private static final Color CL_GREEN   = new Color(0x00,0x80,0x00);
    private static final Color CL_TEAL    = new Color(0x00,0x80,0x80);
    private static final Color CL_MAROON  = new Color(0x80,0x00,0x00);
    private static final Color CL_PURPLE  = new Color(0x80,0x00,0x80);
    private static final Color CL_OLIVE   = new Color(0x80,0x80,0x00);
    private static final Color CL_SILVER  = new Color(0xC0,0xC0,0xC0);
    private static final Color CL_GRAY    = new Color(0x80,0x80,0x80);
    private static final Color CL_BLUE    = new Color(0x00,0x00,0xFF);
    private static final Color CL_LIME    = new Color(0x00,0xFF,0x00);
    private static final Color CL_AQUA    = new Color(0x00,0xFF,0xFF);
    private static final Color CL_RED     = new Color(0xFF,0x00,0x00);
    private static final Color CL_FUCHSIA = new Color(0xFF,0x00,0xFF);
    private static final Color CL_YELLOW  = new Color(0xFF,0xFF,0x00);
    private static final Color CL_WHITE   = new Color(0xFF,0xFF,0xFF);

    private static final int[] COLOR_TABLE =
            {CL_BLACK.getRGB(),    CL_NAVY.getRGB(),  CL_GREEN.getRGB(),  CL_TEAL.getRGB(), CL_MAROON.getRGB(), CL_PURPLE.getRGB(),
             CL_OLIVE.getRGB(),  CL_SILVER.getRGB(),   CL_GRAY.getRGB(),  CL_BLUE.getRGB(),   CL_LIME.getRGB(),   CL_AQUA.getRGB(),
               CL_RED.getRGB(), CL_FUCHSIA.getRGB(), CL_YELLOW.getRGB(), CL_WHITE.getRGB()};

    public static final int DEFAULT_COLOR = 0xF0; // CL_WHITE / CL_BLACK по умолчанию

    private final int fStorageSize;
    private final AtomicBoolean fChanges;
    private final transient BufferedImage  fBufImg;
    private final transient WritableRaster fRaster;

    private volatile  boolean  fEnable;
    private transient volatile Object fColData  ;
    private transient volatile Object fColDataBg;

    /**
     * Конструктор.
     */
    public Screen() {
        fStorageSize = (SCREEN_HEIGHT * SCREEN_WIDTH) / 8; // Размер экранной области в байтах (каждый пиксел = 1 бит)

        fChanges = new AtomicBoolean(false);
        fBufImg  = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        fRaster  = fBufImg.getRaster();
        
        setColor(DEFAULT_COLOR);
        
        fEnable = true;

        setLayout(null);
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));

        // Экран обновляется по таймеру - значение 33 миллисекунд = обновлению экрана 30 раз в секунду
        new Timer(33, e -> repaintScreen()).start();
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth ();
        int h = getHeight();

        if ((float) w / (float) h > ASPECT_RATIO) {
            w = (int) (h * ASPECT_RATIO);
        } else {
            h = (int) (w / ASPECT_RATIO);
        }

        if ((w > 0) && (h > 0)) {
            g.drawImage(fBufImg.getScaledInstance(w, h, Image.SCALE_FAST), 0, 0, null);
            super.paintChildren(g);
            super.paintBorder  (g);
        }
    }

    @Override
    public int storageSize() {
        return fStorageSize;
    }

    @Override
    public void writeByte(int address, int value) {
        if (fEnable && (address >= 0) && (address < fStorageSize)) {
            fChanges.getAndSet(true);

            int x = (address / 256) * 8;
            int y =  address % 256;
            for (int j = 0; j < 8; j++) {
                if ((value & 0x80) != 0) {
                    fRaster.setDataElements(x + j, y, fColData  ); // Здесь без EventQueue.invokeLater для скорости!
                } else {
                    fRaster.setDataElements(x + j, y, fColDataBg); // Здесь без EventQueue.invokeLater для скорости!
                }
                value <<= 1;
            }
        }
    }

    @Override
    public void reset(boolean clear) {
        setColor(DEFAULT_COLOR);

        fEnable = true;
        fChanges.getAndSet(false);

        if (clear) {
            fBufImg.getGraphics().clearRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT); // Здесь без EventQueue.invokeLater для скорости!
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Screen that = (Screen) o;
        return Objects.equals(this.fStorageSize, that.fStorageSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fStorageSize);
    }

    /**
     * Перерисовывает экран.
     */
    private void repaintScreen() {
        if (fChanges.getAndSet(false)) {
            repaint();
        }
    }

    /**
     * Устанавливает цвет изображения/фона.
     *
     * @param color старший полубайт - цвет изображения, младший полубайт - цвет фона
     */
    public void setColor(int color) {
        fColData   = fBufImg.getColorModel().getDataElements(COLOR_TABLE[(color & 0xF0) >> 4], null);
        fColDataBg = fBufImg.getColorModel().getDataElements(COLOR_TABLE[ color & 0x0F      ], null);
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