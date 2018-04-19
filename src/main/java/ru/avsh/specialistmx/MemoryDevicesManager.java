package ru.avsh.specialistmx;

/**
 * Класс "Быстрый диспетчер устройств памяти".
 *
 * @author -=AVSh=-
 */
final class MemoryDevicesManager {
    // Максимальное количество устройств памяти
    private static final  int MAX_DEVICES = 50;

    private final         int[][]     fAddresses = new        int[2][MAX_DEVICES];
    private final IMemoryDevice[] fMemoryDevices = new IMemoryDevice[MAX_DEVICES];

    private int fSize; // Тут не нужен volatile, т.к. используется синхронизация

    /**
     * Добавляет устройство памяти в массивы с сортировкой по startAddress.
     *
     * @param startAddress начальный адрес размещения устройства
     * @param endAddress   конечный адрес размещения устройства
     * @param memoryDevice устройство памяти
     */
    private void add(int startAddress, int endAddress, final IMemoryDevice memoryDevice) {
        if (memoryDevice != null) {
            int index = 0;
            for (; index < fSize; index++) {
                if (fMemoryDevices[index].equals(memoryDevice) && (fAddresses[0][index] == startAddress)) {
                    break;
                }
            }
            if ((fSize == index) && (fSize < MAX_DEVICES)) {
                for (index = 0; index < fSize; index++)    {
                    if (fAddresses[0][index]  > startAddress) {
                        for (int i = fSize; i > index;   i--) {
                             fAddresses[0][i] =  fAddresses[0][i - 1];
                             fAddresses[1][i] =  fAddresses[1][i - 1];
                            fMemoryDevices[i] = fMemoryDevices[i - 1];
                        }
                        break;
                    }
                }
                 fAddresses[0][index] = startAddress;
                 fAddresses[1][index] =   endAddress;
                fMemoryDevices[index] = memoryDevice;
                fSize++;
            }
        }
    }

    /**
     * Добавляет устройство памяти (устройства с нулевой длинной памяти и выходящие за границы
     * диапазона адресов 64K не добавляются).
     * Метод должен вызываться до старта главного потока выполнения.
     *
     * @param startAddress начальный адрес размещения устройства
     * @param memoryDevice устройство памяти
     */
    synchronized void addMemoryDevice(int startAddress, final IMemoryDevice memoryDevice) {
        if ((startAddress >= 0) && (startAddress <= 0xFFFF) && (memoryDevice != null)) {
            int  endAddress  = startAddress + memoryDevice.getMemoryDeviceLength() - 1;
            if ((endAddress >= startAddress) && (endAddress <= 0xFFFF)) {
                // Добавляем устройство памяти
                add(startAddress, endAddress, memoryDevice);
            }
        }
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     *
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    synchronized int readByte(int address) {
        for (int i = 0, startAddress; (i < fSize) && ((startAddress = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                int value  = fMemoryDevices[i].readByte(address - startAddress);
                if (value != -1) {
                    return value & 0xFF;
                }
            }
        }
        return 0xFF;
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     * (Метод для вызова из отладчика - минимизирует влияние отладчика на работу устройств памяти)
     *
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    synchronized int debugReadByte(int address) {
        for (int i = 0, startAddress; (i < fSize) && ((startAddress = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                int value  = fMemoryDevices[i].debugReadByte(address - startAddress);
                if (value != -1) {
                    return value & 0xFF;
                }
            }
        }
        return 0xFF;
    }

    /**
     * Читает слово по заданному адресу из устройства памяти.
     *
     * @param address заданный адрес
     * @return считанное из устройства памяти слово
     */
    int readWord(int address) {
        return readByte(address) | (readByte(address + 1) << 8);
    }

    /**
     * Записывает байт по заданному адресу в устройство/устройства памяти.
     *
     * @param address заданный адрес
     * @param value   записываемый байт (байт представлен как int)
     */
    synchronized void writeByte(int address, int value) {
        for (int i = 0, startAddress; (i < fSize) && ((startAddress = fAddresses[0][i]) <= address); i++) {
            if ( fAddresses[1][i] >= address) {
                fMemoryDevices[i].writeByte(address - startAddress, value & 0xFF);
            }
        }
    }

    /**
     * Пишет слово по заданному адресу в устройство/устройства памяти.
     *
     * @param address заданный адрес
     * @param word    записываемое слово
     */
    void writeWord(int address, int word) {
        writeByte(address, word);
        writeByte(address + 1, word >> 8);
    }

    /**
     * Сбрасывает устройства памяти в начальное состояние.
     *
     * @param clear true - очистка буферов устройств памяти
     */
    synchronized void resetMemoryDevices(boolean clear) {
        for (int i = 0; i < fSize; i++) {
            fMemoryDevices[i].reset(clear);
        }
    }

    /**
     * Переводит устройства памяти в режим "Пауза"
     *
     * @param mode true = установить режим "Пауза" / false = снять режим "Пауза"
     */
    synchronized void pauseMemoryDevices(boolean mode) {
        for (int i = 0; i < fSize; i++) {
            fMemoryDevices[i].pause(mode);
        }
    }

    /**
     * Закрывает ресурсы устройств памяти (используется при завершении работы программы).
     */
    synchronized void closeMemoryDevices() {
        for (int i = 0; i < fSize; i++) {
            fMemoryDevices[i].close();
        }
    }
}