package ru.avsh.specialist.mx.units.memory.units;

import ru.avsh.specialist.mx.units.Speaker;
import ru.avsh.specialist.mx.units.types.ClockedUnit;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.Objects;

/**
 * Адресуемое устройство "Программируемый таймер КР580ВИ53 (i8253)".
 * Реализация с учетом особенностей подключения таймера на ПК "Специалист MX"
 * (на входы GATE всех таймеров подается логическая 1, Counter #1 соединен каскадно с Counter #2).
 *
 * @author -=AVSh=-
 */
public final class ProgrammableTimer implements MemoryUnit, ClockedUnit {
    private static final int STORAGE_SIZE = 4;

    // Режимы чтения/загрузки счетчика таймера
    private static final int RLM_LATCH   = 0; // Чтение фиксированного значения счетчика / Фиксация текущего значения счетчика
    private static final int RLM_LSB     = 1; // Чтение / Загрузка только младшего значащего байта (LSB)
    private static final int RLM_MSB     = 2; // Чтение / Загрузка только старшего значащего байта (MSB)
    private static final int RLM_LSB_MSB = 3; // Чтение / загрузка сначала LSB, а затем MSB (16-битный режим)

    private final Speaker fSpeaker ;
    private final Counter fCounter0;
    private final Counter fCounter1;
    private final Counter fCounter2;

    private boolean fPause;

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Внутренний класс "Счетчик" (реализует один счетчик таймера КР580ВИ53).
     */
    private static class Counter {
        private boolean fBCD   ; // Режим счета (True = двоично-десятичный счет, False = двоичный счет)
        private int     fMode  ; // Режим работы счетчика
        private int     fAccess; // Режим чтения/загрузки счетчика

        private int fPhase       ; // Фаза работы счетчика
        private int fValue       ; // Значение счетчика
        private int fInitValue   ; // Новое значение счетчика
        private int fLatch       ; // Фиксированное значение счетчика
        private int fLatchedCount; // Счетчик байтов для чтения фиксированного значения
        private int fLowByteValue; // Значение LSB в 16-битном режиме чтения/загрузки (RLM_LSB_MSB)

        private boolean fReadMSB; // Флаг чтения   MSB (True = далее следует чтение   MSB)
        private boolean fLoadMSB; // Флаг загрузки MSB (True = далее следует загрузка MSB)

        private volatile boolean fOut; // Состояние выхода OUT счетчика
        private boolean   fOutChanged; // Индикатор изменения состояния выхода OUT в текущем цикле (True = состояние OUT изменилось)

        private int fMinInitValue =       1; // Минимально  возможное новое значение счетчика
        private int fMaxInitValue = 0x10000; // Максимально возможное новое значение счетчика

        /**
         * Конструктор.
         */
        Counter() {
            reset();
        }

        /**
         * Выполняет сброс счетчика.
         */
        void reset() {
            fPhase  = 0; // Установим начальную фазу счетчика = 0

            fBCD    =       false; // Установим режим двоичного счета
            fMode   =           0; // Установим режим счетчика "Mode 0"
            fAccess = RLM_LSB_MSB; // Установим режим чтения/загрузки 16-битного значения

            fValue  = fInitValue = fLatch = 0; // Установим основные регистры счетчика = 0
            fLatchedCount = fLowByteValue = 0; // Установим вспомогательные переменные = 0

            fReadMSB = fLoadMSB    = false; // Сбрасываем флаги чтения/загрузки
            fOut     = fOutChanged = false; // В режиме счетчика "Mode 0" на выходе OUT будет 0 (false)

            fMinInitValue =       1; // Устанавливаем минимально  возможное новое значение счетчика по умолчанию
            fMaxInitValue = 0x10000; // Устанавливаем максимально возможное новое значение счетчика по умолчанию
        }

        /**
         * Возвращает состояние выхода OUT счетчика.
         *
         * @return значение состояния выхода OUT.
         */
        boolean isOut() {
            return fOut;
        }

        /**
         * Показывает было ли изменение состояния на выходе OUT счетчика в текущем цикле.
         *
         * @return true - состояние выхода OUT изменилось.
         */
        boolean isChanged() {
            return fOutChanged;
        }

        /**
         * Устанавливает значение на выходе OUT счетчика.
         *
         * @param value значение
         */
        private void setOut(boolean value) {
            fOutChanged = fOut ^ value;
            fOut        = value;
        }

        /**
         * Переводит двоичное число в двоично-десятичное.
         *
         * @param value двоичное число
         * @return двоично-десятичное число
         */
        private int binToDec(int value) {
            int result = 0;
            for (int i = 0; value >  0; value /= 10, i += 4) {
                result |= ( value % 10) << i;
            }
            return result;
        }

        /**
         * Переводит двоично-десятичное число в двоичное.
         *
         * @param value двоично-десятичное число
         * @return двоичное число
         */
        private int decToBin(int value) {
            int result = 0;
            for (int i = 1; value > 0; value >>= 4, i *= 10) {
                result += ( value & 0b1111) * i;
            }
            return result;
        }

        /**
         * Устанавливает режимы работы счетчика.
         *
         * @param cmd команда установки режимов счетчика
         */
        void setup(int cmd) {
            int access = (cmd >> 4) & 0b11;
            if (access == RLM_LATCH) {
                if (!fReadMSB && (fLatchedCount == 0)) {
                    int value;
                    // Корректируем нечетные граничные значения в режиме 3
                    if ((fMode == 3) && fOutChanged) {
                        value = fInitValue;
                    } else {
                        value = fValue;
                    }
                    switch (fAccess) {
                        case RLM_LSB:
                            fLatch        = ((value << 8) & 0xFF00) | (value & 0xFF);
                            fLatchedCount = 1;
                            break;
                        case RLM_MSB:
                            fLatch        = (value & 0xFF00) | ((value >> 8) & 0xFF);
                            fLatchedCount = 1;
                            break;
                        case RLM_LSB_MSB:
                            fLatch        = value;
                            fLatchedCount = 2;
                            break;
                        default:
                            break;
                    }
                }
            } else {
                fPhase   =      0; // После установки режима всегда устанавливаем фазу 0
                fAccess  = access;
                fBCD     = (cmd  & 1) == 1;
                fMode    = (cmd >> 1) & ((cmd & 0b100) != 0 ? 0b11 : 0b111);
                fReadMSB = fLoadMSB = false;
                if ((fMode == 2) || (fMode == 3)) {
                    fMinInitValue = 2; // В режимах "Mode 2" и "Mode 3" можно установить начальное значение счетчика >=2
                } else {
                    fMinInitValue = 1;
                }
                fMaxInitValue = fBCD ? 10000 : 0x10000;
                setOut(fMode > 0);
            }
        }

        /**
         * Читает данные (байт) из счетчика.
         *
         * @return байт, прочитанный из счетчика
         */
        int read() {
            int data = 0;
            if (fLatchedCount != 0) {
                data     = ((fBCD ? binToDec(fLatch) : fLatch) >> (fReadMSB ? 8 : 0)) & 0xFF;
                fReadMSB = !fReadMSB;
                fLatchedCount--;
            } else {
                int value;
                // Корректируем нечетные граничные значения в режиме 3
                if ((fMode == 3) && fOutChanged) {
                    value = fBCD ? binToDec(fInitValue) : fInitValue;
                } else {
                    value = fBCD ? binToDec(fValue    ) : fValue;
                }
                switch (fAccess){
                    case RLM_LSB:
                        data = value & 0xFF;
                        break;
                    case RLM_MSB:
                        data = (value >> 8) & 0xFF;
                        break;
                    case RLM_LSB_MSB:
                        // Чтение счетчика, после записи только LSB 16-битной записи, возвращает XOR версию LSB
                        if (fLoadMSB) {
                            data = fBCD ? binToDec(fLowByteValue ^ 0xFF) & 0xFF : fLowByteValue ^ 0xFF;
                        } else {
                            data = (value >> (fReadMSB ? 8 : 0)) & 0xFF;
                        }
                        fReadMSB = !fReadMSB;
                        break;
                    default:
                        break;
                }
            }
            return data;
        }

        /**
         * Читает данные (байт) из счетчика.
         * (Метод для вызова из отладчика - не вносит изменения в счетчик при чтении)
         *
         * @return байт, прочитанный из счетчика
         */
        int debugRead() {
            int data = 0;
            if (fLatchedCount != 0) {
                data = ((fBCD ? binToDec(fLatch) : fLatch) >> (fReadMSB ? 8 : 0)) & 0xFF;
            } else {
                int value;
                // Корректируем нечетные граничные значения в режиме 3
                if ((fMode == 3) && fOutChanged) {
                    value = fBCD ? binToDec(fInitValue) : fInitValue;
                } else {
                    value = fBCD ? binToDec(fValue    ) : fValue;
                }
                switch (fAccess){
                    case RLM_LSB:
                        data = value & 0xFF;
                        break;
                    case RLM_MSB:
                        data = (value >> 8) & 0xFF;
                        break;
                    case RLM_LSB_MSB:
                        // Чтение счетчика, после записи только LSB 16-битной записи, возвращает XOR версию LSB
                        if (fLoadMSB) {
                            data = fBCD ? binToDec(fLowByteValue ^ 0xFF) & 0xFF : fLowByteValue ^ 0xFF;
                        } else {
                            data = (value >> (fReadMSB ? 8 : 0)) & 0xFF;
                        }
                        break;
                    default:
                        break;
                }
            }
            return data;
        }

        /**
         * Загружает новое значение счетчика.
         *
         * @param initValue новое значение
         */
        private void loadInitValue(int initValue) {
            if        (initValue == 0) {
                initValue = fMaxInitValue; // Нужно для корректной работы в двоично-десятичном режиме
            } else if (initValue == 1) {
                if (fMode == 2) {
                    initValue = fMinInitValue; // В "Mode 2" невозможна загрузка значения = 1 (установим = 2)
                }
                if (fMode == 3) {
                    initValue = fMaxInitValue; // В "Mode 3" невозможна загрузка значения = 1 (установим = 10000H/10000)
                }
            }
            fInitValue = initValue;

            if        ((fMode == 2) || (fMode == 3)) {
                if (fPhase == 0) {
                    fPhase = 1; // Для "Mode 2" и "Mode 3" переход в фазу 1 возможен только после фазы 0
                }
            } else if ((fMode == 0) || (fMode == 4)) {
                fPhase = 1;
            }
        }

        /**
         * Загружает данные (байт) в счетчик.
         *
         * @param data байт для загрузки в счетчик
         */
        void load(int data) {
            if (fBCD) {
                data = decToBin(data) & 0xFF;
            }
            switch (fAccess){
                case RLM_LSB:
                    loadInitValue(data);
                    break;
                case RLM_MSB:
                    loadInitValue(data << 8);
                    break;
                case RLM_LSB_MSB:
                    if (fLoadMSB) {
                        loadInitValue(fLowByteValue | (data << 8));
                    } else {
                        fLowByteValue = data;
                        if (fMode == 0) {
                            // Документы Intel говорят, что загрузка MSB в режиме "Mode 0" фаза 2 не остановит счет, но это было экспериментально опровергнуто.
                            fPhase = 0;
                            setOut(false);
                        }
                    }
                    fLoadMSB = !fLoadMSB;
                    break;
                default:
                    break;
            }
        }

        /**
         * Выполняет один цикл счетчика.
         */
        void step() {
            // Сбрасываем индикатор изменения состояния выхода OUT
            fOutChanged = false;
            // Выполняем один цикл счетчика
            switch (fMode) {
                case 0:
                    /* Mode 0: (Interrupt on Terminal Count)
                              +------------------
                              |
                    ----------+
                      <- n+1 ->
                      ^
                      +- counter load

                    phase|output|length  |value|next|comment
                    -----+------+--------+-----+----+----------------------------------
                        0|low   |infinity|     |1   |waiting for count
                        1|low   |1       |     |2   |internal delay when counter loaded
                        2|low   |n       |n..1 |3   |counting down
                        3|high  |infinity|0..1 |3   |counting down

                    Gate level sensitive only. Low disables counting, high enables it. */
                    if (fPhase > 0) {
                        switch (fPhase) {
                            case 1:
                                fPhase = 2;
                                fValue = fInitValue;
                                setOut(false);
                                break;
                            case 2:
                                if (fValue > 1) {
                                    fValue--;
                                } else {
                                    fPhase = 3;
                                    fValue = fMaxInitValue;
                                    setOut(true);
                                }
                                break;
                            case 3:
                                if (fValue > 1) {
                                    fValue--;
                                } else {
                                    fValue = fMaxInitValue;
                                }
                                break;
                            default:
                                break;
                        }
                    }
                    break;
                case 1: // -= Для Gate = "H" счет в этом режиме не выполняется =-
                    /* Mode 1: (Hardware Retriggerable One-Shot a.k.a. Programmable One-Shot)
                    -----+       +------------------
                         |       |
                         +-------+
                         <-  n  ->
                      ^
                      +- trigger

                    phase|output|length  |value|next|comment
                    -----+------+--------+-----+----+----------------------------------
                        0|high  |infinity|     |1   |counting down
                        1|high  |1       |     |2   |internal delay to load counter
                        2|low   |n       |n..1 |3   |counting down
                        3|high  |infinity|0..1 |3   |counting down

                    Gate rising-edge sensitive only.
                    Rising edge initiates counting and resets output after next clock. */
                    break;
                case 2:
                    /* Mode 2: (Rate Generator)
                    --------------+ +---------+ +----
                                  | |         | |
                                  +-+         +-+
                       <-    n    -X-    n    ->
                                  <1>
                    ^
                    +- counter load or trigger

                    phase|output|length  |value|next|comment
                    -----+------+--------+-----+----+----------------------------------
                        0|high  |infinity|     |1   |waiting for count
                        1|high  |1       |     |2   |internal delay to load counter
                        2|high  |n       |n..2 |3   |counting down
                        3|low   |1       |1    |2   |reload counter

                    Counter rewrite has no effect until repeated

                    Gate rising-edge and level sensitive.
                    Gate low disables counting and sets output immediately high.
                    Rising-edge reloads count and initiates counting
                    Gate high enables counting. */

                    break;
                case 3:
                    /* Mode 3: (Square Wave Generator)
                    ----------------+           +-----------+           +----
                                    |           |           |           |
                                    +-----------+           +-----------+
                        <- (n+1)/2 -X-   n/2   ->
                     ^
                     +- counter load or trigger

                    phase|output|length  |value|next|comment
                    -----+------+--------+-----+----+----------------------------------
                        0|high  |infinity|     |1   |waiting for count
                        1|high  |1       |     |2   |internal delay to load counter
                        2|high  |n/2(+1) |n..0 |3   |counting down double speed, reload counter
                        3|low   |n/2     |n..0 |2   |counting down double speed, reload counter

                    Counter rewrite has no effect until repeated (output falling or rising)

                    Gate rising-edge and level sensitive.
                    Gate low disables counting and sets output immediately high.
                    Rising-edge reloads count and initiates counting
                    Gate high enables counting. */
                    if (fPhase > 0) {
                        switch (fPhase) {
                            case 1:
                                fPhase      = 2;
                                fValue      = (fInitValue + 1) & 0x1FFFE; // Нечетные граничные значения заменяем на +1 четные
                                fOutChanged = true;                       // Для коррекции граничного значения при чтении начального значения счетчика
                                break;
                            case 2:
                                if (fValue > fMinInitValue) {
                                    fValue -= 2;
                                } else {
                                    fPhase = 3;
                                    fValue = fInitValue & 0x1FFFE; // Нечетные граничные значения заменяем на четные
                                    setOut(false);
                                }
                                break;
                            case 3:
                                if (fValue > fMinInitValue) {
                                    fValue -= 2;
                                } else {
                                    fPhase = 2;
                                    fValue = (fInitValue + 1) & 0x1FFFE; // Нечетные граничные значения заменяем на +1 четные
                                    setOut(true);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                    break;
                case 4:
                    /* Mode 4: (Software Trigger Strobe)
                       Mode 5: (Hardware Trigger Strobe)
                    --------------+ +--------------------
                                  | |
                                  +-+
                        <-  n+1  ->
                        ^         <1>
                        +- counter load (mode 4) or trigger (mode 5)

                    phase|output|length  |value|next|comment
                    -----+------+--------+-----+----+----------------------------------
                        0|high  |infinity|0..1 |0   |waiting for count/counting down
                        1|high  |1       |     |2   |internal delay when counter loaded
                        2|high  |n       |n..1 |3   |counting down
                        3|low   |1       |0    |0   |strobe

                    Mode 4 only: counter rewrite loads new counter
                    Mode 5 only: count not reloaded immediately.
                    Mode control write doesn't stop count but sets output high

                    Mode 4 only: Gate level sensitive only. Low disables counting, high enables it.
                    Mode 5 only: Gate rising-edge sensitive only. Rising edge initiates counting */
                    break;
                case 5: // -= Для Gate = "H" счет в этом режиме не выполняется =-
                    break;
                default:
                    break;
            }
        }
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Конструктор.
     *
     * @param speaker ссылка на объект класса Speaker - "Speaker (динамик)"
     */
    public ProgrammableTimer(Speaker speaker) {
        fSpeaker  = speaker;
        fCounter0 = new Counter();
        fCounter1 = new Counter();
        fCounter2 = new Counter();
    }

    /**
     * Возвращает состояние выхода OUT счетчика 2.
     *
     * @return значение состояния выхода OUT счетчика 2.
     */
    boolean getCounter2Out() {
        return fCounter2.isOut();
    }

    @Override
    public boolean cycle() {
        if (!fPause) {
            // Тактируем счетчики
            fCounter0.step();
            fCounter1.step();
            if (fCounter1.isChanged() && !fCounter1.isOut()) {
                fCounter2.step();
            }
            // Выводим звук
            if ((fSpeaker != null) && (((fCounter0.isChanged() ^ fCounter0.isOut()) || (fCounter2.isChanged() ^ fCounter2.isOut())) ^ (fCounter0.isOut() || fCounter2.isOut()))) {
                 fSpeaker.play8253(!(fCounter0.isOut() || fCounter2.isOut()));
            }
        }
        return true;
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public int readByte(int address) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
            switch (address) {
                case 0: // -> Counter #0
                    return fCounter0.read();
                case 1: // -> Counter #1
                    return fCounter1.read();
                case 2: // -> Counter #1
                    return fCounter2.read();
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
                case 0: // -> Counter #0
                    return fCounter0.debugRead();
                case 1: // -> Counter #1
                    return fCounter1.debugRead();
                case 2: // -> Counter #1
                    return fCounter2.debugRead();
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
                case 0: // <- Counter #0
                    fCounter0.load(value);
                    break;
                case 1: // <- Counter #1
                    fCounter1.load(value);
                    break;
                case 2: // <- Counter #2
                    fCounter2.load(value);
                    break;
                case 3: // <- Control Word
                    switch (value >> 6) {
                        case 0: // <- Counter #0
                            fCounter0.setup(value);
                            break;
                        case 1: // <- Counter #1
                            fCounter1.setup(value);
                            break;
                        case 2: // <- Counter #2
                            fCounter2.setup(value);
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void reset(boolean clear) {
        fCounter0.reset();
        fCounter1.reset();
        fCounter2.reset();
        if (fSpeaker != null) {
            fSpeaker.play8253(false); // Чтобы не был заперт вывод звука через ВВ55
        }
    }

    @Override
    public void pause(boolean mode) {
        fPause = mode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgrammableTimer that = (ProgrammableTimer) o;
        return Objects.equals(this.fSpeaker, that.fSpeaker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fSpeaker);
    }
}