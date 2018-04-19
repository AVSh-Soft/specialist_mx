package ru.avsh.specialistmx;

import java.util.Arrays;
import java.util.Objects;

/**
 * Устройство памяти "Оперативная память 'Специалист MX'".
 *
 * @author -=AVSh=-
 */
final class MemDevMainMemory implements IMemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 0xFFC0;
    private static final int ROM_DISK_LENGTH      = 0xC000;
    private static final int MIN_NUMBER_PAGES     =      2; // Минимальное  число страниц памяти: 0 - основная, 1   - RAM-диск (не считая ROM-диск)
    private static final int MAX_NUMBER_PAGES     =      9; // Максимальное число страниц памяти: 0 - основная, 1-8 - RAM-диск (не считая ROM-диск)

    // Номер ROM-диска для использования в методах getPage()/setPage()
    static final int ROM_DISK = MAX_NUMBER_PAGES;

    private final byte[] fRAM;
    private final int fNumberPages;
    private volatile int fCurrentPage;
    private volatile int fCurrentOffset;
    private final MemDevScreen fScreen ;

    /**
     * Конструктор.
     *
     * @param numberPages количество страниц памяти (не считая ROM-диск)
     * @param screen      ссылка на объект класса MemDevScreen - "Экран 'Специалиста MX'"
     */
    MemDevMainMemory(int numberPages, MemDevScreen screen) {
         numberPages = Math.min(Math.max(numberPages, MIN_NUMBER_PAGES), MAX_NUMBER_PAGES);
        fNumberPages = numberPages;
             fScreen = screen;
                fRAM = new byte[MEMORY_DEVICE_LENGTH * numberPages + ROM_DISK_LENGTH];
    }

    @Override
    public int getMemoryDeviceLength() {
        return MEMORY_DEVICE_LENGTH;
    }

    @Override
    public int readByte(int address) {
        if ((fCurrentOffset >= 0) && (address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            if ((fCurrentPage < fNumberPages) || (address < ROM_DISK_LENGTH)) {
                address += fCurrentOffset;
            }
            return (int) fRAM[address] & 0xFF;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((fCurrentOffset >= 0) && (address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            if ((fCurrentPage < fNumberPages) || (address < ROM_DISK_LENGTH)) {
                address += fCurrentOffset;
            }
            fRAM[address] = (byte) value;
        }
    }

    @Override
    public void reset(boolean clear) {
        fCurrentPage   = 0;
        fCurrentOffset = 0;
        if (clear) {
            Arrays.fill(fRAM, (byte) 0);
        }
    }

    @Override
    public synchronized String toString() {
        return String.format("Информация о памяти:%n" +
                             "Размер  (RAM + RAM-диск + ROM-диск): %.4fКб;%n" +
                             "Страниц (RAM + RAM-диск + ROM-диск): %d;%n"     +
                             "Активная страница памяти: %d",
                fRAM.length / 1024F, fNumberPages + 1, fCurrentPage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemDevMainMemory that = (MemDevMainMemory) o;
        return Objects.equals(this.fNumberPages, that.fNumberPages) &&
               Objects.equals(this.fScreen     , that.fScreen     );
    }

    @Override
    public int hashCode() {
        return Objects.hash(fNumberPages, fScreen);
    }

    /**
     * Устанавливает заданную страницу памяти.
     *
     * @param pageNumber от 0-8 - страницы RAM, 9 или больше - страница ROM
     */
    void setPage(int pageNumber) {
        if (pageNumber < 0) {
            pageNumber = 0;
        }
        if (pageNumber < MAX_NUMBER_PAGES) {
            fCurrentPage   =  pageNumber;
            fCurrentOffset = (pageNumber < fNumberPages) ? MEMORY_DEVICE_LENGTH * pageNumber : -1;
        } else {
            fCurrentPage   = ROM_DISK; // ROM-диск
            fCurrentOffset = MEMORY_DEVICE_LENGTH * fNumberPages;
        }
        // Изображение выводится на экран только из страницы 0
        if (fScreen != null) {
            fScreen.setEnable(fCurrentPage == 0);
        }
    }

    /**
     * Возвращает номер текущей страницы памяти.
     *
     * @return номер страницы
     */
    int getPage() {
        return fCurrentPage;
    }
}