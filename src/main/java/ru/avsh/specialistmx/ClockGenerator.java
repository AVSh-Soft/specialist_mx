package ru.avsh.specialistmx;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Класс "Тактовый генератор".
 */
final class ClockGenerator implements Runnable {
    // Основные константы тактового генератора
            static final String THREAD_NAME   = "ClockGenerator"; // Имя потока
            static final int    CLOCK_SPEED   =       2_000_000 ; // Тактовая частота
            static final long   TIME_OF_PULSE =      33_000_000L; // Время импульса в наносекундах
    private static final int    MAX_DEVICES   =              50 ; // Максимальное количество тактируемых устройств

    private final Object           fMutex;
    private final AtomicLong       fCyclesCounter ; // Это может быть излишним и достаточно volatile
    private final IClockedDevice[] fClockedDevices;

    private int fSize; // Тут не нужен volatile, т.к. используется синхронизация

    private volatile ProcessorI8080 fCPU;
    private volatile int     fIndexCPU;
    private volatile boolean fWaitFlag;
    private volatile boolean fPauseFlag;
    private volatile int     fClockSpeed;
    private volatile int     fIterationCycles;
    private volatile boolean fPrevCpuHoldMode;

    /**
     * Конструктор.
     */
    ClockGenerator() {
        fMutex          = new Object();
        fCyclesCounter  = new AtomicLong();
        fClockedDevices = new IClockedDevice[MAX_DEVICES];
        fIndexCPU       =   -1;
        fPauseFlag      = true;
        setClockSpeed(CLOCK_SPEED);
    }

    @Override
    public String toString() {
        return String.format("Частота процессора: %.1fМГц", getClockSpeed() / 1_000_000F);
    }

    @Override
    public void run() {
        int  index;
        long nanos;
        long endTime;
        long startTime = System.nanoTime();

        // Присваиваем имя потоку
        Thread.currentThread().setName(THREAD_NAME);

        try {
            // Основной цикл работы тактового генератора
            for (; ; ) {
                // Если установлена пауза -
                if (fPauseFlag) {
                    // завершаем команду CPU
                    execOneCmdCPU();
                    // переводим поток генератора в состояние ожидания
                    synchronized (fMutex) {
                        // Здесь выполняем присвоение fWaitFlag = fPauseFlag с одновременной проверкой
                        while (fWaitFlag = fPauseFlag) {
                            fMutex.wait();
                        }
                    }
                    // после завершения ожидания, замеряем начальное время
                    startTime = System.nanoTime();
                }

                // Рассчитываем конечное время выполнения импульса генератора (подгонка из-за погрешности System.nanoTime())
                endTime = startTime + TIME_OF_PULSE - 100_000L;
                // Выполняем заданное количество тактов генератора без задержек
                synchronized (this) {
                    for (long end = fCyclesCounter.get() + fIterationCycles; !fPauseFlag && (fCyclesCounter.get() < end); ) {
                        // Выполняем один такт у тактируемых устройств
                        for (index = 0; index < fSize; index++) {
                            fClockedDevices[index].cycle();
                        }
                        // Увеличиваем счетчик циклов (тактов) генератора
                        fCyclesCounter.getAndIncrement();
                    }
                }

                if (!fPauseFlag) {
                    // Если было прерывание потока - выходим из цикла
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    // Усыпляем поток на оставшееся время импульса
                    if ((nanos = endTime - System.nanoTime()) >= 1_000_000L) {
                        Thread.sleep(nanos / 1_000_000L);
                    }
                    // Делаем точную подгонку под оставшееся время импульса
                    // noinspection StatementWithEmptyBody
                    while (endTime > (startTime = System.nanoTime())) {
                        //
                    }
                }
            }
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Добавляет тактируемое устройство в массив.
     * Метод должен вызываться до старта тактового генератора.
     *
     * @param clockedDevice тактируемое устройство
     */
    synchronized void addClockedDevice(IClockedDevice clockedDevice) {
        if (clockedDevice != null) {
            int index = 0;
            for (; index < fSize; index++) {
                if (fClockedDevices[index].equals(clockedDevice)) {
                    break;
                }
            }
            if ((fSize == index) && (fSize < MAX_DEVICES)) {
                // Запоминаем ссылку на CPU и индекс CPU в массиве устройств
                if (clockedDevice instanceof ProcessorI8080) {
                         fCPU = (ProcessorI8080) clockedDevice;
                    fIndexCPU = fSize;
                }
                fClockedDevices[fSize++] = clockedDevice; // fClockedDevices и fSize изменяются только тут
            }
        }
    }

    /**
     * Возвращает значение счетчика тактов.
     *
     * @return значение счетчика тактов
     */
    long getCyclesCounter() {
        return fCyclesCounter.get();
    }

    /**
     * Возвращает значение тактовой частоты.
     *
     * @return тактовая частота в Гц
     */
    int getClockSpeed() {
        return fClockSpeed;
    }

    /**
     * Задает тактовую частоту.
     *
     * @param clockSpeed тактовая частота в Гц
     */
    void setClockSpeed(int clockSpeed) {
        fClockSpeed      = clockSpeed;
        fIterationCycles = (int) Math.round(TIME_OF_PULSE * clockSpeed / 1_000_000_000.0);
    }

    /**
     * Показывает приостановлен тактовый генератор или нет.
     *
     * @return - true = CPU приостановлен
     */
    boolean isPaused() {
        return fPauseFlag;
    }

    /**
     * Переводит тактовый генератор в режим "Пауза".
     *
     * @param mode true/false = установить/снять режим "Пауза"
     * @param dev  true = устанавливать/снимать режим "Пауза" и для устройств памяти
     */
    void pause(boolean mode, boolean dev) {
        if (fPauseFlag != mode) {
            if (mode) {
                // Останавливаем CPU и устройства памяти
                if (dev && (fCPU != null)) {
                    // CPU
                    fPrevCpuHoldMode = fCPU.isHoldAcknowledge();
                    if (!fPrevCpuHoldMode) {
                        fCPU.hold(true);
                    }
                    // Устройства
                    fCPU.pauseMemoryDevices(true);
                }

                // Останавливаем тактовый генератор
                fPauseFlag = true;
                for (int i = 1000; !fWaitFlag && (i > 0); i--) {
                    try {
                        Thread.sleep(1L); // спим 1мс
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                // Пробуждаем тактовый генератор
                fPauseFlag = false;
                synchronized (fMutex) {
                    fMutex.notifyAll();
                }

                // Пробуждаем CPU и устройства памяти
                if (dev && (fCPU != null)) {
                    // Устройства
                    fCPU.pauseMemoryDevices(false);
                    // CPU
                    if (!fPrevCpuHoldMode && fCPU.isHoldAcknowledge()) {
                        fCPU.hold(false);
                    }
                }
            }
        }
    }

    /**
     * Выполняет одну команду CPU в режиме "Пауза" тактового генератора.
     * (Метод для вызова из отладчика)
     *
     * @return true = команда выполнена успешно
     */
    synchronized boolean execOneCmdCPU() {
        if (fPauseFlag && (fIndexCPU >= 0) && !fCPU.isHoldAcknowledge()) {
            boolean flag = true;
            do {
                // Выполняем один такт у тактируемых устройств
                for (int index = 0; index < fSize; index++) {
                    if ( index == fIndexCPU) {
                          flag = fClockedDevices[index].cycle();
                    } else {
                        fClockedDevices[index].cycle();
                    }
                }
            } while (!flag);
            return true;
        }
        return false;
    }
}