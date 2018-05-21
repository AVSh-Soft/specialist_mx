package ru.avsh.specialist.mx.units.memory;

import ru.avsh.specialist.mx.units.types.MemoryUnit;

/**
 * Класс "Быстрый диспетчер запоминающих устройств".
 *
 * @author -=AVSh=-
 */
public final class MemoryUnitManager implements MemoryUnit {
    private static final int STORAGE_SIZE      = 0x1_0000;
    // Максимальное количество запоминающих устройств
    private static final int MAX_STORAGE_UNITS = 50;

    private final      int[][] fAddresses   = new     int[2][MAX_STORAGE_UNITS];
    private final MemoryUnit[] fMemoryUnits = new MemoryUnit[MAX_STORAGE_UNITS];

    private int fSize; // Тут не нужен volatile, т.к. используется синхронизация

    /**
     * Добавляет запоминающее устройство в массивы с сортировкой по startAddress.
     *
     * @param startAddress начальный адрес размещения устройства
     * @param endAddress   конечный адрес размещения устройства
     * @param memoryUnit  запоминающее устройство
     */
    private void add(int startAddress, int endAddress, final MemoryUnit memoryUnit) {
        if (memoryUnit != null) {
            int index = 0;
            for (; index < fSize; index++) {
                if (fMemoryUnits[index].equals(memoryUnit) && (fAddresses[0][index] == startAddress)) {
                    break;
                }
            }
            if ((fSize == index) && (fSize < MAX_STORAGE_UNITS)) {
                for (index = 0; index < fSize; index++)       {
                    if (fAddresses[0][index]  > startAddress) {
                        for (int i = fSize; i > index;   i--) {
                             fAddresses[0][i] = fAddresses[0][i - 1];
                             fAddresses[1][i] = fAddresses[1][i - 1];
                             fMemoryUnits [i] = fMemoryUnits [i - 1];
                        }
                        break;
                    }
                }
                fAddresses[0][index] = startAddress;
                fAddresses[1][index] =   endAddress;
                fMemoryUnits [index] =   memoryUnit;
                fSize++;
            }
        }
    }

    /**
     * Добавляет запоминающее устройство (устройства с нулевой длинной памяти и выходящие за границы
     * диапазона адресов 64K не добавляются).
     * Метод должен вызываться до старта главного потока выполнения.
     *
     * @param startAddress начальный адрес размещения устройства
     * @param memoryUnit  запоминающее устройство
     */
    public synchronized void addMemoryUnit(int startAddress, final MemoryUnit memoryUnit) {
        if ((startAddress >= 0) && (startAddress < STORAGE_SIZE) && (memoryUnit != null)) {
            int  endAddress  = startAddress + memoryUnit.storageSize() - 1;
            if ((endAddress >= startAddress) && (endAddress < STORAGE_SIZE)) {
                // Добавляем запоминающее устройство
                add(startAddress, endAddress, memoryUnit);
            }
        }
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    /**
     * Читает байт по заданному адресу из запоминающего устройства.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     *
     * @param address заданный адрес
     * @return считанный из запоминающего устройства байт (байт представлен как int)
     */
    @Override
    public synchronized int readByte(int address) {
        for (int i = 0, startAddress; (i < fSize) && ((startAddress = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                int value  = fMemoryUnits[i].readByte(address - startAddress);
                if (value != -1) {
                    return value & 0xFF;
                }
            }
        }
        return 0xFF;
    }

    /**
     * Читает байт по заданному адресу из запоминающего устройства.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     * (Метод для вызова из отладчика - минимизирует влияние отладчика на работу запоминающего устройства)
     *
     * @param address заданный адрес
     * @return считанный из запоминающего устройства байт (байт представлен как int)
     */
    @Override
    public synchronized int debugReadByte(int address) {
        for (int i = 0, startAddress; (i < fSize) && ((startAddress = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                int value  = fMemoryUnits[i].debugReadByte(address - startAddress);
                if (value != -1) {
                    return value & 0xFF;
                }
            }
        }
        return 0xFF;
    }

    /**
     * Читает слово по заданному адресу из запоминающего устройства.
     *
     * @param address заданный адрес
     * @return считанное из запоминающего устройства слово
     */
    public int readWord(int address) {
        return readByte(address) | (readByte(address + 1) << 8);
    }

    /**
     * Записывает байт по заданному адресу в запоминающее устройство/устройства.
     *
     * @param address заданный адрес
     * @param value   записываемый байт (байт представлен как int)
     */
    @Override
    public synchronized void writeByte(int address, int value) {
        for (int i = 0, startAddress; (i < fSize) && ((startAddress = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                fMemoryUnits[i].writeByte(address - startAddress, value & 0xFF);
            }
        }
    }

    /**
     * Пишет слово по заданному адресу в запоминающее устройство/устройства.
     *
     * @param address заданный адрес
     * @param word    записываемое слово
     */
    public void writeWord(int address, int word) {
        writeByte(address, word);
        writeByte(address + 1, word >> 8);
    }

    /**
     * Сбрасывает запоминающие устройства в начальное состояние.
     *
     * @param clear true - очистка буферов запоминающих устройств
     */
    @Override
    public synchronized void reset(boolean clear) {
        for (int i = 0; i < fSize; i++) {
            fMemoryUnits[i].reset(clear);
        }
    }

    /**
     * Переводит запоминающие устройства в режим "Пауза"
     *
     * @param mode true = установить режим "Пауза" / false = снять режим "Пауза"
     */
    @Override
    public synchronized void pause(boolean mode) {
        for (int i = 0; i < fSize; i++) {
            fMemoryUnits[i].pause(mode);
        }
    }

    /**
     * Закрывает ресурсы запоминающих устройств (используется при завершении работы программы).
     */
    @Override
    public synchronized void close() {
        for (int i = 0; i < fSize; i++) {
            fMemoryUnits[i].close();
        }
    }
}