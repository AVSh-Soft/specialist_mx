package ru.avsh.specialistmx;

/**
 * Класс "Быстрый диспетчер устройств памяти".
 * @author -=AVSh=-
 */
final class cMemoryDevicesManager {
    // Максимальное количество устройств памяти
    private final static int MAX_DEVICES = 50;

    private final         int[][]     fAddresses = new        int[2][MAX_DEVICES];
    private final MemoryDevice[] fMemoryDevices = new MemoryDevice[MAX_DEVICES];

    private int fSize; // Тут не нужен volatile, т.к. используется синхронизация

    /**
     * Добавляет устройство памяти в массивы с сортировкой по start_address.
     * @param start_address начальный адрес размещения устройства
     * @param end_address конечный адрес размещения устройства
     * @param memoryDevice устройство памяти
     */
    private void add(int start_address, int end_address, MemoryDevice memoryDevice) {
        if (memoryDevice != null) {
            int index = 0;
            for (; index < fSize; index++) {
                if (fMemoryDevices[index].equals(memoryDevice) && (fAddresses[0][index] == start_address)) {
                    break;
                }
            }
            if ((fSize == index) && (fSize < MAX_DEVICES)) {
                for (index = 0; index < fSize; index++)    {
                    if (fAddresses[0][index] > start_address) {
                        for (int i = fSize; i > index; i--)   {
                             fAddresses[0][i] =  fAddresses[0][i - 1];
                             fAddresses[1][i] =  fAddresses[1][i - 1];
                            fMemoryDevices[i] = fMemoryDevices[i - 1];
                        }
                        break;
                    }
                }
                 fAddresses[0][index] = start_address;
                 fAddresses[1][index] =   end_address;
                fMemoryDevices[index] =  memoryDevice;
                fSize++;
            }
        }
    }

    /**
     * Добавляет устройство памяти (устройства с нулевой длинной памяти и выходящие за границы
     * диапазона адресов 64K не добавляются).
     * Метод должен вызываться до старта главного потока выполнения.
     * @param start_address начальный адрес размещения устройства
     * @param memoryDevice устройство памяти
     */
    synchronized void addMemoryDevice(int start_address, MemoryDevice memoryDevice) {
        if ((start_address >= 0) && (start_address <= 0xFFFF) && (memoryDevice != null)) {
            int  end_address  = start_address + memoryDevice.getMemoryDeviceLength() - 1;
            if ((end_address >= start_address) && (end_address <= 0xFFFF)) {
                // Добавляем устройство памяти
                add(start_address, end_address, memoryDevice);
            }
        }
    }

    /**
     * Читает байт по заданному адресу из устройства памяти.
     * Чтение осуществляется из первого встречного устройства типа R/W,
     * остальные устройства игнорируются.
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    synchronized int readByte(int address) {
        for (int i = 0, start_address; (i < fSize) && ((start_address = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                int value = fMemoryDevices[i].readByte(address - start_address);
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
     * @param address заданный адрес
     * @return считанный из устройства памяти байт (байт представлен как int)
     */
    synchronized int debugReadByte(int address) {
        for (int i = 0, start_address; (i < fSize) && ((start_address = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                int value = fMemoryDevices[i].debugReadByte(address - start_address);
                if (value != -1) {
                    return value & 0xFF;
                }
            }
        }
        return 0xFF;
    }

    /**
     * Читает слово по заданному адресу из устройства памяти.
     * @param address заданный адрес
     * @return считанное из устройства памяти слово
     */
    int readWord(int address) {
        return readByte(address) | (readByte(address + 1) << 8);
    }

    /**
     * Записывает байт по заданному адресу в устройство/устройства памяти.
     * @param address заданный адрес
     * @param value записываемый байт (байт представлен как int)
     */
    synchronized void writeByte(int address, int value) {
        for (int i = 0, start_address; (i < fSize) && ((start_address = fAddresses[0][i]) <= address); i++) {
            if (fAddresses[1][i] >= address) {
                fMemoryDevices[i].writeByte(address - start_address, value & 0xFF);
            }
        }
    }

    /**
     * Пишет слово по заданному адресу в устройство/устройства памяти.
     * @param address заданный адрес
     * @param word записываемое слово
     */
    void writeWord(int address, int word) {
        writeByte(address    , word);
        writeByte(address + 1, word >> 8);
    }

    /**
     * Сбрасывает устройства памяти в начальное состояние.
     * @param clear true - очистка буферов устройств памяти
     */
    synchronized void resetMemoryDevices(boolean clear) {
        for (int i = 0; i < fSize; i++) {
            fMemoryDevices[i].reset(clear);
        }
    }

    /**
     * Переводит устройства памяти в режим "Пауза"
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