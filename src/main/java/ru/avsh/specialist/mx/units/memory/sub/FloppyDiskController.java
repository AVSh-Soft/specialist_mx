package ru.avsh.specialist.mx.units.memory.sub;

import org.jetbrains.annotations.NotNull;
import ru.avsh.specialist.mx.units.CPUi8080;
import ru.avsh.specialist.mx.units.ClockSpeedGenerator;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Адресуемое устройство "Контроллер НГМД КР1818ВГ93 (FD1793-02)".
 *
 * @author -=AVSh=-
 */
public final class FloppyDiskController implements MemoryUnit {
    private static final String THREAD_NAME  = "FloppyDiskController"; // Имя потока
    private static final int    STORAGE_SIZE = 4;

    // Константа для корректного взаимодействия с CPU
    private static final long CPU_WAIT_TIME = Math.round(ClockSpeedGenerator.TIME_OF_PULSE / 1_000_000.0);

    // Константы для формирования индексного импульса
    private static final long REVOLUTION_TIME    = 400_000L; // Время одного оборота диска = 200мс (400_000 тактов) или 300об/мин
    private static final long TIME_WITHOUT_INDEX = 399_000L; // Время одного оборота диска без времени индексного импульса

    // Константы под ODI-формат
    private static final int ODI_TRACKS        =   80;
    private static final int ODI_SECTORS       =    5;
    private static final int ODI_SIDES         =    2;
    private static final int ODI_SECTOR_LENGTH = 1024;

    // Флаги регистра статуса fRegStatus
    private static final int F_NOT_READY                               = 0b1000_0000;
    private static final int F_WRITE_PROTECT                           = 0b0100_0000;
    private static final int F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT = 0b0010_0000;
    private static final int F_SEEK_ERROR_OR_RECORD_NOT_FOUND          = 0b0001_0000;
    private static final int F_CRC_ERROR                               = 0b0000_1000;
    private static final int F_TRACK_00_OR_LOST_DATA                   = 0b0000_0100;
    private static final int F_INDEX_OR_DATA_REQUEST                   = 0b0000_0010;
    private static final int F_BUSY                                    = 0b0000_0001;

    // Регистры контроллера НГМД КР1818ВГ93 (FD1793-02)
    private final AtomicInteger fRegStatus ;
    private final AtomicInteger fRegCommand;
    private final AtomicInteger fRegTrack  ;
    private final AtomicInteger fRegSector ;
    private final AtomicInteger fRegData   ;

    // Дополнительные флаги
    private volatile    boolean fPause;
    private final AtomicBoolean fInterrupt;
    private final AtomicBoolean fWasDataRequest;

    // Переменные
    private final Object fMutex;
    private final CPUi8080 fCPU;
    private final FloppyDiskDrive fDriveA ;
    private final FloppyDiskDrive fDriveB ;
    private final ClockSpeedGenerator fGen;

    private volatile boolean         fCurSide ;
    private volatile FloppyDiskDrive fCurDrive;

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Внутренний класс "Накопитель на гибком магнитном диске".
     */
    private class FloppyDiskDrive {
        private volatile RandomAccessFile fDisk;
        private volatile boolean          fReadOnly;
        private final    AtomicInteger    fCurTrack = new AtomicInteger();

        /**
         * Извлекает диск из дисковода (закрывает файл с образом диска).
         */
        void ejectDisk() {
            if (isReady()) {
                try {
                    fDisk.close();
                } catch (IOException e) {
                    //
                } finally {
                    fDisk     = null ;
                    fReadOnly = false;
                }
            }
        }

        /**
         * Вставляет диск в дисковод (открывает файл с образом диска).
         *
         * @param file файл с образом диска
         * @throws IOException исключение, возникающее при вставке диска
         */
        void insertDisk(File file) throws IOException {
            ejectDisk();
            // Проверка соответствия ODI формату на 800Кб
            if (file.length() == ODI_TRACKS * ODI_SECTORS * ODI_SIDES * ODI_SECTOR_LENGTH) {
                if (file.canWrite()) {
                    fReadOnly = false;
                    fDisk     = new RandomAccessFile(file, "rw");
                } else {
                    fReadOnly = true;
                    fDisk     = new RandomAccessFile(file, "r");
                }
            } else {
                throw new IOException(String.format("Образ диска: \"%s\" имеет некорректный размер (не равен 800Кб)!", file.getName()));
            }
        }

        /**
         * Возвращает признак готовности дисковода.
         *
         * @return true - дисковод готов.
         */
        boolean isReady() {
            return fDisk != null;
        }

        /**
         * Возвращает признак диска "только чтение".
         *
         * @return true - диск только для чтения.
         */
        boolean isReadOnly() {
            return fReadOnly;
        }

        /**
         * Возвращает ссылку на диск.
         *
         * @return ссылка на диск.
         */
        RandomAccessFile getDisk() {
            return fDisk;
        }

        /**
         * Возвращает номер текщей дорожки.
         *
         * @return номер текущей дорожки.
         */
        int getCurTrack() {
            return fCurTrack.get();
        }

        /**
         * Увеличивает номер дорожки на 1.
         */
        void incCurTrack() {
            if (fCurTrack.get() < ODI_TRACKS - 1) {
                fCurTrack.getAndIncrement();
            }
        }

        /**
         * Уменьшает номер дорожки на 1.
         */
        void decCurTrack() {
            if (fCurTrack.get() > 0) {
                fCurTrack.getAndDecrement();
            }
        }
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Внутренний класс "Ядро контроллера НГМД".
     */
    private class CoreFDC implements Runnable {
        private boolean fDirection;
        private final byte[] fBuf = new byte[ODI_SECTORS * ODI_SECTOR_LENGTH];

        /**
         * Возобновляет работу CPU (только если HOLD для CPU был инициирован в методе waitDataRequest()).
         */
        private void continueCPU() {
            if (fWasDataRequest.getAndSet(false)) {
                fCPU.hold(false);
            }
        }

        /**
         * Усыпляет на заданное количество миллисекунд с проверкой на прерывание.
         *
         * @param millis миллисекунды
         */
        private void sleep(long millis) {
            synchronized (fMutex) {
                try {
                    do {
                        fMutex.wait(millis);
                    } while (!fInterrupt.get() && fPause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Ожидает данные заданное количество миллисекунд с проверкой на прерывание/готовность данных.
         *
         * @param millis миллисекунды
         */
        private void waitData(long millis) {
            // Ожидаем реакцию CPU
            synchronized (fMutex) {
                try {
                    for (; !fInterrupt.get() && getStatusFlag(F_INDEX_OR_DATA_REQUEST) && (millis > 0); millis--) {
                        // Возобновляем работу CPU
                        continueCPU();
                        // Ожидаем 1мс (если установлен режим "Пауза", то ожидаем, пока "Пауза" не будет снята)
                        do {
                            fMutex.wait(1L);
                        } while (!fInterrupt.get() && fPause);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * Выполняет команды "TYPE I" контроллера НГМД.
         *
         * @param steps заданное количество шагов головки дисковода
         */
        private void emuCmdType1(int steps) {
            // Очищаем флаги "Ошибка в контрольной сумме" и "Массив не найден"
            setStatusFlag(F_CRC_ERROR | F_SEEK_ERROR_OR_RECORD_NOT_FOUND, false);
            // Устанавливаем флаг "Загрузка головки"
            setStatusFlag(F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT, (fRegCommand.get() & 0b1000) != 0);
            // Если заданное количество шагов не равно 0, начинаем позиционирование головки
            if (steps != 0) {
                int curTrack = fRegTrack.get();

                // Определяем скорость позиционирования головки
                long millis;
                switch (fRegCommand.get() & 0b11) {
                    case 0:
                        millis =  6L;
                        break;
                    case 1:
                        millis = 12L;
                        break;
                    case 2:
                        millis = 20L;
                        break;
                    case 3:
                        millis = 30L;
                        break;
                    default:
                        millis =  6L;
                        break;
                }
                // Определяем направление позиционирования
                fDirection = steps >= 0;
                // Эмулируем шаги позиционирования головки
                steps = Math.abs(steps);
                for (; !fInterrupt.get() && (steps > 0); steps--) {
                    // Если дисковод подает сигнал TR00, то прекращаем позиционирование головки
                    if (!fDirection && (fCurDrive.getCurTrack() == 0)) {
                        // Обнуляем регистр дорожки
                        fRegTrack.getAndSet(0);
                        break;
                    } else {
                        // Позиционируем головку
                        if (fDirection) {
                            fCurDrive.incCurTrack();
                            curTrack = (curTrack + 1) & 0xFF;
                        } else {
                            fCurDrive.decCurTrack();
                            if (curTrack > 0) {
                                curTrack = (curTrack - 1) & 0xFF;
                            }
                        }
                        // При необходимости изменяем номер дорожки в регистре дорожки
                        if ((fRegCommand.get() & 0b10000) != 0) {
                            fRegTrack.getAndSet(curTrack);
                        }
                        // Эмулируем задержку на перемещение головки
                        sleep(millis);
                    }
                }
                // Проверяем позиционирование головки с эмуляцией задержки
                if ((fRegCommand.get() & 0b100) != 0) {
                    sleep(30L);
                    // Устанавливаем флаги "Ошибка в контрольной сумме" и "Массив не найден"
                    setStatusFlag(F_CRC_ERROR | F_SEEK_ERROR_OR_RECORD_NOT_FOUND, !fCurDrive.isReady() || (fRegTrack.get() >= ODI_TRACKS));
                }
            }
        }

        /**
         * Проверяет корректность дорожки, сектора и стороны.
         *
         * @return возвращает true, если обнаружены проблемы.
         */
        private boolean checkTrackSectorSide() {
                      // Проверка дорожки
            return    ( fRegTrack.get() != fCurDrive.getCurTrack())
                      // Проверка сектора
                   || (fRegSector.get() == 0)
                   || (fRegSector.get() > ODI_SECTORS)
                      // Проверка строны (если это предусмотрено в команде)
                   || (   ( (fRegCommand.get() & 0b10  ) != 0)
                       && (((fRegCommand.get() & 0b1000) == 0) == fCurSide));
        }

        /**
         * Получает позицию в файле-образе диска.
         *
         * @return позиция.
         */
        private long getPos() {
            return (long) ODI_SECTOR_LENGTH * (ODI_SECTORS * ((ODI_SIDES * fRegTrack.get()) + (fCurSide ? 1 : 0)) + (fRegSector.get() - 1));
        }

        /**
         * Выполняет команды "TYPE II" контроллера НГМД.
         */
        private void emuCmdType2() {
            int length;
            // Очищаем флаги "Запрос данных", "Потеря данных", "Ошибка в контрольной сумме", "Массив не найден", "Тип адресной метки/Ошибка записи" и "Защита записи"
            setStatusFlag(F_INDEX_OR_DATA_REQUEST | F_TRACK_00_OR_LOST_DATA | F_CRC_ERROR | F_SEEK_ERROR_OR_RECORD_NOT_FOUND | F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT | F_WRITE_PROTECT, false);
            // Если дисковод не готов - выходим
            if (!fCurDrive.isReady()) {
                return;
            }
            // Эмулируем задержку загрузки головки
            if ((fRegCommand.get() & 0b100) != 0) {
                sleep(30L);
            }
            // Блок "Запись сектор(а/ов)"
            if ((fRegCommand.get() & 0b1110_0000) == 0b1010_0000) {
                // Если диск (файл-образ) только на чтение - устанавливаем флаг "Защита записи" и выходим
                if (fCurDrive.isReadOnly()) {
                    setStatusFlag(F_WRITE_PROTECT, true);
                    return;
                }
                // Проверяем дорожку, сектор и сторону диска
                if (checkTrackSectorSide()) {
                    // В случае ошибки устанавливаем флаг "Массив не найден"
                    setStatusFlag(F_SEEK_ERROR_OR_RECORD_NOT_FOUND, true);
                    return;
                }
                // Вычисляем длину данных
                length = ODI_SECTOR_LENGTH * ((fRegCommand.get() & 0b1_0000) != 0 ? ODI_SECTORS - fRegSector.get() + 1 : 1);
                // Заранее вычисляем позицию данных
                long pos = getPos();
                // Читаем данные из регистра данных в буфер
                for (int i = 0; i < length; i++) {
                    // Устанавливаем флаг "Запрос данных"
                    setStatusFlag(F_INDEX_OR_DATA_REQUEST, true);
                    // Ожидаем, когда CPU заполнит регистр данных (время ожидания с учетом особенностей реализации CPU в классе CPUi8080)
                    waitData(CPU_WAIT_TIME);
                    // Если было принудительное прерывание
                    if (fInterrupt.get()) {
                        // Записываем последний байт из регистра данных в буфер
                        fBuf[i] = (byte) fRegData.get();
                        // Вычисляем длину удачно переданных в буфер данных
                        length = ((i + 1) / ODI_SECTOR_LENGTH) * ODI_SECTOR_LENGTH;
                        // Записываем удачные данные
                        if (length > 0) {
                            break;
                        }
                        // Иначе выходим
                        return;
                    }
                    // Если CPU не заполнил регистр данных вовремя
                    if (getStatusFlag(F_INDEX_OR_DATA_REQUEST)) {
                        // Устанавливаем флаг "Потеря данных"
                        setStatusFlag(F_TRACK_00_OR_LOST_DATA, true);
                        // Вычисляем длину удачно переданных в буфер данных
                        length = (i / ODI_SECTOR_LENGTH) * ODI_SECTOR_LENGTH;
                        // Записываем удачные данные
                        if (length > 0) {
                            break;
                        }
                        // Иначе выходим
                        return;
                    }
                    // Записываем байт из регистра данных в буфер
                    fBuf[i] = (byte) fRegData.get();
                }
                // Записываем данные из буфера на диск
                try {
                    fCurDrive.getDisk().seek (pos);
                    fCurDrive.getDisk().write(fBuf, 0, length);
                } catch (IOException e) {
                    // В случае ошибки записи устанавливаем флаги "Ошибка в контрольной сумме" и "Ошибка записи"
                    setStatusFlag(F_CRC_ERROR | F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT, true);
                }
            }
            // Блок "Чтение сектор(а/ов)"
            else if ((fRegCommand.get() & 0b1110_0000) == 0b1000_0000) {
                // Проверяем дорожку, сектор и сторону диска
                if (checkTrackSectorSide()) {
                    // В случае ошибки устанавливаем флаг "Массив не найден"
                    setStatusFlag(F_SEEK_ERROR_OR_RECORD_NOT_FOUND, true);
                    return;
                }
                // Устанавливаем флаг "Тип адресной метки"
                setStatusFlag(F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT, (fRegCommand.get() & 0b1) != 0);
                // Вычисляем длину данных
                length = ODI_SECTOR_LENGTH * ((fRegCommand.get() & 0b1_0000) != 0 ? ODI_SECTORS - fRegSector.get() + 1 : 1);
                // Читаем данные с диска в буфер
                try {
                    fCurDrive.getDisk().seek(getPos());
                    fCurDrive.getDisk().read(fBuf, 0, length);
                } catch (IOException e) {
                    // В случае ошибки чтения устанавливаем флаг "Ошибка в контрольной сумме"
                    setStatusFlag(F_CRC_ERROR, true);
                    return;
                }
                // Выдаем данные из буфера в регистр данных
                for (int i = 0; !fInterrupt.get() && (i < length); i++) {
                    fRegData.getAndSet(fBuf[i] & 0xFF);
                    // Устанавливаем флаг "Запрос данных"
                    setStatusFlag(F_INDEX_OR_DATA_REQUEST, true);
                    // Ожидаем, когда CPU прочитает данные из регистра данных (время ожидания с учетом особенностей реализации CPU в классе CPUi8080)
                    waitData(CPU_WAIT_TIME);
                    // Если CPU не прочитал вовремя данные из регистра данных
                    if (getStatusFlag(F_INDEX_OR_DATA_REQUEST)) {
                        // Устанавливаем флаг "Потеря данных" и выходим
                        setStatusFlag(F_TRACK_00_OR_LOST_DATA, true);
                        return;
                    }
                }
            }
        }

        /**
         * Выполняет команды "TYPE III" контроллера НГМД (в рамках формата ODI реальная реализация невозможна).
         */
        private void emuCmdType3() {
            // Очищаем флаги "Запрос данных", "Потеря данных", "Ошибка в контрольной сумме", "Массив не найден", "Тип адресной метки/Ошибка записи" и "Защита записи"
            setStatusFlag(F_INDEX_OR_DATA_REQUEST | F_TRACK_00_OR_LOST_DATA | F_CRC_ERROR | F_SEEK_ERROR_OR_RECORD_NOT_FOUND | F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT | F_WRITE_PROTECT, false);
            // Если дисковод не готов - выходим
            if (!fCurDrive.isReady()) {
                return;
            }
            // Эмулируем задержку загрузки головки
            if ((fRegCommand.get() & 0b100) != 0) {
                sleep(30L);
            }
            // Блок "Чтение адреса"
            if ((fRegCommand.get() & 0b1111_0000) == 0b1100_0000) {
                // Проверяем дорожку, сектор и сторону диска
                if (checkTrackSectorSide()) {
                    // В случае ошибки устанавливаем флаг "Массив не найден"
                    setStatusFlag(F_SEEK_ERROR_OR_RECORD_NOT_FOUND, true);
                    return;
                }
                // Устанавливаем флаги ошибок - эмулируем сбой при выполнении команды
                setStatusFlag(F_INDEX_OR_DATA_REQUEST | F_TRACK_00_OR_LOST_DATA | F_CRC_ERROR, true);
            } // Блок "Чтение дорожки"
            else if ((fRegCommand.get() & 0b1111_0000) == 0b1110_0000) {
                // Устанавливаем флаги ошибок - эмулируем сбой при выполнении команды
                setStatusFlag(F_INDEX_OR_DATA_REQUEST | F_TRACK_00_OR_LOST_DATA, true);
            } // Блок "Запись дорожки"
            else if ((fRegCommand.get() & 0b1111_0000) == 0b1111_0000) {
                // Если диск (файл-образ) только на чтение - устанавливаем флаг "Защита записи" и выходим
                if (fCurDrive.isReadOnly()) {
                    setStatusFlag(F_WRITE_PROTECT, true);
                    return;
                }
                // Устанавливаем флаги ошибок - эмулируем сбой при выполнении команды
                setStatusFlag(F_INDEX_OR_DATA_REQUEST | F_TRACK_00_OR_LOST_DATA | F_HEAD_LOAD_OR_RECORD_TYPE_OR_WRITE_FAULT, true);
            }
        }

        @Override
        public void run() {
            // Присваиваем имя потоку
            Thread.currentThread().setName(THREAD_NAME);
            try {
                for (; ; ) {
                    // Ожидаем новую команду
                    synchronized (fMutex) {
                        // Сбрасываем флаг "Прерывание"
                        fInterrupt.getAndSet(false);
                        // Сбрасываем флаг "Занято"
                        setStatusFlag(F_BUSY, false);
                        // Возобнавляем работу CPU для исключения зависания
                        continueCPU();
                        // Ожидаем новую команду
                        while (!getStatusFlag(F_BUSY)) {
                            fMutex.wait();
                        }
                    }
                    // Обрабатываем полученную команду
                    switch (fRegCommand.get() >> 4) {
                        case  0: // "Восстановление"
                            fRegData.getAndSet(0);
                            emuCmdType1(-255);
                            break;
                        case  1: // "Поиск"
                            emuCmdType1(fRegData.get() - fRegTrack.get());
                            break;
                        case  2: // "Шаг в предыдущем направлении"
                        case  3:
                            emuCmdType1(fDirection ? 1 : -1);
                            break;
                        case  4: // "Шаг вперед"
                        case  5:
                            emuCmdType1(1);
                            break;
                        case  6: // "Шаг назад"
                        case  7:
                            emuCmdType1(-1);
                            break;
                        case  8: // "Чтение сектора"
                        case  9:
                        case 10: // "Запись сектора"
                        case 11:
                            emuCmdType2();
                            break;
                        case 12: // "Чтение адреса"
                            emuCmdType3();
                            break;
                        case 13: // "Принудительное прерывание" (выполнение сюда не доходит, т.к. поток не пробуждается по этой команде)
                            fInterrupt.getAndSet(true);
                            break;
                        case 14: // "Чтение дорожки"
                        case 15: // "Запись дорожки"
                            emuCmdType3();
                            break;
                        default:
                            break;
                    }
                    // Если было прерывание потока - выходим из цикла
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    sleep(1L);
                }
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }
        }
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Конструктор.
     *
     * @param gen ссылка на объект класса ClockSpeedGenerator - "Тактовый генератор"
     * @param cpu ссылка на объект класса CPUi8080 - "Процессор Intel C8080A (К580ВМ80А)"
     */
    public FloppyDiskController(@NotNull ClockSpeedGenerator gen, CPUi8080 cpu) {
        // Устанавливаем ссылку на тактовый генератор
        fGen = gen;
        // Устанавливаем ссылку на CPU
        fCPU = cpu;

        // Инициализируем объекты
        fRegStatus  = new AtomicInteger();
        fRegCommand = new AtomicInteger();
        fRegTrack   = new AtomicInteger();
        fRegSector  = new AtomicInteger();
        fRegData    = new AtomicInteger();

        fInterrupt      = new AtomicBoolean();
        fWasDataRequest = new AtomicBoolean();

        fMutex  = new Object();
        fDriveA = new FloppyDiskDrive();
        fDriveB = new FloppyDiskDrive();

        fCurDrive = fDriveA;

        // Запускаем ядро контроллера
        new Thread(new CoreFDC()).start();
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public int readByte(int address) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            switch (address) {
                case 0: // -> Статус
                    setStatusFlag(F_NOT_READY, !fCurDrive.isReady());
                    // Если были команды "TYPE I" или команда "TYPE IV" "Принудительное прерывание", поданная без устаноленного флага F_BUSY
                    if (((fRegCommand.get() & 0b1000_0000) == 0) || ((fRegCommand.get() & 0b1111_0000) == 0b1101_0000)) {
                        // Формируем индексный импульс
                        if (!fCurDrive.isReady()) {
                            setStatusFlag(F_INDEX_OR_DATA_REQUEST, true);
                        } else if (fCurSide || (((fRegCommand.get() & 0b1000) != 0) && getStatusFlag(F_BUSY))) {
                            if (fGen.getCyclesCounter() % REVOLUTION_TIME > TIME_WITHOUT_INDEX) {
                                setStatusFlag(F_INDEX_OR_DATA_REQUEST, true );
                            } else {
                                setStatusFlag(F_INDEX_OR_DATA_REQUEST, false);
                            }
                        }
                        setStatusFlag(F_TRACK_00_OR_LOST_DATA, fCurDrive.getCurTrack() == 0);
                        setStatusFlag(F_WRITE_PROTECT, fCurDrive.isReadOnly());
                    }
                    return fRegStatus.get();
                case 1: // -> Дорожка
                    return  fRegTrack.get();
                case 2: // -> Сектор
                    return fRegSector.get();
                case 3: // -> Данные
                    int data = fRegData.get();
                    // Если выполняется команда "Чтение сектора"
                    if (((fRegCommand.get() & 0b1110_0000) == 0b1000_0000) && getStatusFlag(F_BUSY | F_INDEX_OR_DATA_REQUEST)) {
                        // Сбрасываем флаг "Запрос данных"
                        setStatusFlag(F_INDEX_OR_DATA_REQUEST, false);
                        // Заставляем конроллер НГМД выдать новую порцию данных
                        synchronized (fMutex) {
                            if (!fPause) {
                                fMutex.notifyAll();
                            }
                        }
                    }
                    return data;
                default:
                    return -1;
            }
        }
        return -1;
    }

    @Override
    public int debugReadByte(int address) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            switch (address) {
                case 0: // -> Статус
                    return fRegStatus.get();
                case 1: // -> Дорожка
                    return  fRegTrack.get();
                case 2: // -> Сектор
                    return fRegSector.get();
                case 3: // -> Данные
                    return   fRegData.get();
                default:
                    return -1;
            }
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            switch (address) {
                case 0: // <- Команда
                    // Если контроллер занят, то обрабатываем только команду "Принудительное прерывание" (прерывание с битами I1 и I0 игнорируем)
                    if (getStatusFlag(F_BUSY)) {
                        if ((value & 0b1111_0011) == 0b1101_0000) {
                            interrupt(false);
                        }
                    } else {
                        // Записываем команду в регистр команд
                        fRegCommand.getAndSet(value);
                        // Команда "Принудительное прерывание" не переводит контроллер в состояние занят
                        if ((value & 0b1111_0000) != 0b1101_0000) {
                            // Устанавливаем флаг "Занято" сразу после подачи команды
                            setStatusFlag(F_BUSY, true);
                            // На всякий случай сбрасываем флаг "Запрос данных/Индексный импульс"
                            setStatusFlag(F_INDEX_OR_DATA_REQUEST, false);
                            // Пробуждаем ядро конроллера НГМД
                            synchronized (fMutex) {
                                fMutex.notifyAll();
                            }
                        }
                    }
                    break;
                case 1: // <- Дорожка
                    fRegTrack.getAndSet(value);
                    break;
                case 2: // <- Сектор
                    fRegSector.getAndSet(value);
                    break;
                case 3: // <- Данные
                    fRegData.getAndSet(value);
                    // Если выполняется команда "Запись сектора"
                    if (((fRegCommand.get() & 0b1110_0000) == 0b1010_0000) && getStatusFlag(F_BUSY | F_INDEX_OR_DATA_REQUEST)) {
                        // Сбрасываем флаг "Запрос данных"
                        setStatusFlag(F_INDEX_OR_DATA_REQUEST, false);
                        // Заставляем конроллер НГМД принять данные
                        synchronized (fMutex) {
                            if (!fPause) {
                                fMutex.notifyAll();
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void reset(boolean clear) {
        // Прерываем текущую команду
        interrupt(true);
        // Устанавливаем строну 0 диска
        setSide(false);
        // Выбираем дисковод "A"
        switchFDD(false);
        // Подаем команду "Восстановление"
        writeByte(0, 0);
    }

    @Override
    public void pause(boolean mode) {
        if (getStatusFlag(F_BUSY) && (fPause != mode)) {
            fPause = mode;
            if (!mode) {
                synchronized (fMutex) {
                    fMutex.notifyAll();
                }
            }
        }
    }

    @Override
    public void close() {
        ejectDisk(false);
        ejectDisk(true );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FloppyDiskController that = (FloppyDiskController) o;
        return Objects.equals(fCPU, that.fCPU) &&
               Objects.equals(fGen, that.fGen);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fCPU, fGen);
    }

    /**
     * Получает значение определенного флага из регистра статуса контроллера НГМД.
     *
     * @param flag флаг (маска)
     * @return значение флага
     */
    private boolean getStatusFlag(int flag) {
        return (fRegStatus.get() & flag) == flag;
    }

    /**
     * Устанавливает значение определенного флага регистра статуса контроллера НГМД.
     *
     * @param flag  флаг (маска)
     * @param value значение флага
     */
    private void setStatusFlag(int flag, boolean value) {
        int prev;
        int next;
        do {
            prev = fRegStatus.get();
            next = value ? prev | flag : prev & ~flag;
        } while (!fRegStatus.compareAndSet(prev, next));
    }

    /**
     * Прерывает выполнение команды контроллера НГМД.
     *
     * @param wait true = ожидать завершения прерывания в течении 1 секунды
     */
    private void interrupt(boolean wait) {
        if (getStatusFlag(F_BUSY)) {
            // Сбрасываем флаг режима "Пауза", т.к. режим "Пауза" не совместим с выполнением прерывания команды контроллера НГМД
            fPause = false;
            // Устанавливаем флаг "Прерывание" для выполнения прерывания команды контроллера НГМД
            fInterrupt.getAndSet(true);
            // Отменяем ожидание контроллера, если оно было
            synchronized (fMutex) {
                fMutex.notifyAll();
            }
            // Ожидаем прерывания максимум 1сек
            if (wait) {
                for (int millis = 1000; getStatusFlag(F_BUSY) && (millis > 0); millis--) {
                    try {
                        Thread.sleep(1L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /**
     * Возвращает номер (boolean) текущего дисковода.
     *
     * @return false = "A" / true = "B"
     */
    private boolean isCurFDD() {
        return fCurDrive == fDriveB;
    }

    /**
     * Извлекает диск из заданного дисковода.
     *
     * @param fdd false = "A" / true = "B"
     */
    public void ejectDisk(boolean fdd) {
        if (fdd == isCurFDD()) {
            interrupt(true);
        }
        if (fdd) {
            fDriveB.ejectDisk();
        } else {
            fDriveA.ejectDisk();
        }
    }

    /**
     * Вставляет диск в заданный дисковод.
     *
     * @param fdd  false = "A" / true = "B"
     * @param file файл с образом диска
     * @throws IOException исключение, возникающее при вставке диска
     */
    public void insertDisk(boolean fdd, File file) throws IOException {
        if (fdd == isCurFDD()) {
            interrupt(true);
        }
        if (fdd) {
            fDriveB.insertDisk(file);
        } else {
            fDriveA.insertDisk(file);
        }
    }

    /**
     * Переключает дисковод на заданный.
     *
     * @param fdd false = "A" / true = "B"
     */
    void switchFDD(boolean fdd) {
        if (fdd != isCurFDD()) {
            interrupt(true);
            if (fdd) {
                fCurDrive = fDriveB;
            } else {
                fCurDrive = fDriveA;
            }
        }
    }

    /**
     * Устанавливает строну диска.
     *
     * @param side false = сторона 0 / true = сторона 1
     */
    void setSide(boolean side) {
        fCurSide = side;
    }

    /**
     * Заставляет CPU ожидать запрос данных от контроллера НГМД.
     */
    void waitDataRequest() {
        // CPU переводим в состояние "Пауза" только для команд контроллера НГМД "Чтение сектора" / "Запись сектора"
        if (((fRegCommand.get() & 0b1100_0000) == 0b1000_0000) && getStatusFlag(F_BUSY) && !getStatusFlag(F_INDEX_OR_DATA_REQUEST)) {
            fCPU.hold(true);
            fWasDataRequest.getAndSet(true);
        }
    }
}