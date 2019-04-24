package ru.avsh.specialist.mx.units.memory.sub;

import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Адресуемое запоминающее устройство "Оперативная память 'Специалист MX'".
 *
 * @author -=AVSh=-
 */
public final class MainMemory implements MemoryUnit {
    private static final int STORAGE_SIZE     = 0xFFC0;
    private static final int ROM_DISK_SIZE    = 0xC000;
    private static final int MIN_NUMBER_PAGES =      2; // Минимальное  число страниц памяти: 0 - основная, 1   - RAM-диск (не считая ROM-диск)
    private static final int MAX_NUMBER_PAGES =      9; // Максимальное число страниц памяти: 0 - основная, 1-8 - RAM-диск (не считая ROM-диск)

    // Номер ROM-диска для использования в методах getPage()/setPage()
    public static final int ROM_DISK = MAX_NUMBER_PAGES;

    private final byte[] fRAM;
    private final Screen fScreen;
    private final int    fNumberPages;

    private final AtomicInteger fCurrentPage;
    private final AtomicInteger fCurrentOffset;

    /**
     * Конструктор.
     *
     * @param numberPages количество страниц памяти (не считая ROM-диск)
     * @param screen      ссылка на объект класса Screen - "Экран 'Специалиста MX'"
     */
    public MainMemory(int numberPages, Screen screen) {
         numberPages = Math.min(Math.max(numberPages, MIN_NUMBER_PAGES), MAX_NUMBER_PAGES);
        fNumberPages = numberPages;
             fScreen = screen;
                fRAM = new byte[STORAGE_SIZE * numberPages + ROM_DISK_SIZE];

        fCurrentPage   = new AtomicInteger(0);
        fCurrentOffset = new AtomicInteger(0);
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public int readByte(int address) {
        if ((fCurrentOffset.get() >= 0) && (address >= 0) && (address < STORAGE_SIZE)) {
            if ((fCurrentPage.get() < fNumberPages) || (address < ROM_DISK_SIZE)) {
                address += fCurrentOffset.get();
            }
            return (int) fRAM[address] & 0xFF;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((fCurrentOffset.get() >= 0) && (address >= 0) && (address < STORAGE_SIZE)) {
            if ((fCurrentPage.get() < fNumberPages) || (address < ROM_DISK_SIZE)) {
                address += fCurrentOffset.get();
            }
            fRAM[address] = (byte) value;
        }
    }

    @Override
    public void reset(boolean clear) {
          fCurrentPage.getAndSet(0);
        fCurrentOffset.getAndSet(0);
        if (clear) {
            Arrays.fill(fRAM, (byte) 0);
        }
    }

    @Override
    public String toString() {
        return String.format("Информация о памяти:%n" +
                             "Размер  (RAM + RAM-диск + ROM-диск): %.4fКб;%n" +
                             "Страниц (RAM + RAM-диск + ROM-диск): %d;%n"     +
                             "Активная страница памяти: %d",
                fRAM.length / 1024F, fNumberPages + 1, fCurrentPage.get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MainMemory that = (MainMemory) o;
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
    public void setPage(int pageNumber) {
        if (pageNumber < 0) {
            pageNumber = 0;
        }
        if (pageNumber < MAX_NUMBER_PAGES) {
              fCurrentPage.getAndSet( pageNumber);
            fCurrentOffset.getAndSet((pageNumber < fNumberPages) ? STORAGE_SIZE * pageNumber : -1);
        } else {
              fCurrentPage.getAndSet(ROM_DISK); // ROM-диск
            fCurrentOffset.getAndSet(STORAGE_SIZE * fNumberPages);
        }
        // Изображение выводится на экран только из страницы 0
        if (fScreen != null) {
            fScreen.setEnable(fCurrentPage.get() == 0);
        }
    }

    /**
     * Возвращает номер текущей страницы памяти.
     *
     * @return номер страницы
     */
    public int getPage() {
        return fCurrentPage.get();
    }
}