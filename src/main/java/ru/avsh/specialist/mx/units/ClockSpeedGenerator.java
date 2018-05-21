package ru.avsh.specialist.mx.units;

import ru.avsh.specialist.mx.units.types.ClockedUnit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Класс "Тактовый генератор".
 */
public final class ClockSpeedGenerator implements Runnable {
    // Основные константы тактового генератора
            static final String THREAD_NAME   = "ClockSpeedGenerator"; // Имя потока
    public  static final int    CLOCK_SPEED   =            2_000_000 ; // Тактовая частота
    public  static final long   TIME_OF_PULSE =           33_000_000L; // Время импульса в наносекундах
    private static final int    MAX_UNITS     =                   50 ; // Максимальное количество тактируемых устройств

    private final Object        fMutex;
    private final AtomicLong    fCyclesCounter; // Это может быть излишним и достаточно volatile
    private final ClockedUnit[] fClockedUnits ;

    private int fSize; // Тут не нужен volatile, т.к. используется синхронизация

    private volatile CPUi8080 fCPU;
    private volatile int            fIndexCPU  ;
    private volatile boolean        fWaitFlag  ;
    private volatile boolean        fPauseFlag ;
    private volatile int            fClockSpeed;
    private volatile int            fIterationCycles;
    private volatile boolean        fPrevCpuHoldMode;

    /**
     * Конструктор.
     */
    public ClockSpeedGenerator() {
        fMutex          = new Object();
        fCyclesCounter  = new AtomicLong();
        fClockedUnits   = new ClockedUnit[MAX_UNITS];
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
                            fClockedUnits[index].cycle();
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
     * (Метод должен вызываться до старта тактового генератора.)
     *
     * @param clockedUnit тактируемое устройство
     */
    public synchronized void addClockedUnit(final ClockedUnit clockedUnit) {
        if (clockedUnit != null) {
            int index = 0;
            for (; index < fSize; index++) {
                if (fClockedUnits[index].equals(clockedUnit)) {
                    break;
                }
            }
            if ((fSize == index) && (fSize < MAX_UNITS)) {
                // Запоминаем ссылку на CPU и индекс CPU в массиве устройств
                if (clockedUnit instanceof CPUi8080) {
                         fCPU = (CPUi8080) clockedUnit;
                    fIndexCPU = fSize;
                }
                fClockedUnits[fSize++] = clockedUnit; // fClockedUnits и fSize изменяются только тут
            }
        }
    }

    /**
     * Возвращает значение счетчика тактов.
     *
     * @return значение счетчика тактов
     */
    public long getCyclesCounter() {
        return fCyclesCounter.get();
    }

    /**
     * Возвращает значение тактовой частоты.
     *
     * @return тактовая частота в Гц
     */
    public int getClockSpeed() {
        return fClockSpeed;
    }

    /**
     * Задает тактовую частоту.
     *
     * @param clockSpeed тактовая частота в Гц
     */
    public void setClockSpeed(final int clockSpeed) {
        fClockSpeed      = clockSpeed;
        fIterationCycles = (int) Math.round(TIME_OF_PULSE * clockSpeed / 1_000_000_000.0);
    }

    /**
     * Показывает приостановлен тактовый генератор или нет.
     *
     * @return - true = тактовый генератор приостановлен
     */
    public boolean isPaused() {
        return fPauseFlag;
    }

    /**
     * Переводит тактовый генератор в режим "Пауза".
     *
     * @param mode true/false = установить/снять режим "Пауза"
     * @param mem  true = устанавливать/снимать режим "Пауза" и для запоминающих устройств
     */
    public void pause(final boolean mode, final boolean mem) {
        if (fPauseFlag != mode) {
            if (mode) {
                // Останавливаем CPU и устройства памяти
                if (mem && (fCPU != null)) {
                    // CPU
                    fPrevCpuHoldMode = fCPU.isHoldAcknowledge();
                    if (!fPrevCpuHoldMode) {
                        fCPU.hold(true);
                    }
                    // Устройства
                    fCPU.pauseMemoryUnits(true);
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
                if (mem && (fCPU != null)) {
                    // Устройства
                    fCPU.pauseMemoryUnits(false);
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
    public synchronized boolean execOneCmdCPU() {
        if (fPauseFlag && (fIndexCPU >= 0) && !fCPU.isHoldAcknowledge()) {
            boolean flag = true;
            do {
                // Выполняем один такт у тактируемых устройств
                for (int index = 0; index < fSize; index++) {
                    if ( index == fIndexCPU) {
                          flag = fClockedUnits[index].cycle();
                    } else {
                        fClockedUnits[index].cycle();
                    }
                }
            } while (!flag);
            return true;
        }
        return false;
    }
}