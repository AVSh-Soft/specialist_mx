package ru.avsh.specialistmx;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Устройство памяти "Порт клавиатуры Specialist_MX на базе КР580ВВ55А (i8255A)".
 * @author -=AVSh=-
 */
final class cMD_SpMX_KeyPort implements MemoryDevice {
    private static final int MEMORY_DEVICE_LENGTH = 4;

    /*
     Матрица клавиш 12х6 - ПК "Специалист MX"
     ---------------------------------------------------------------------------------
             |  C3 |  C2 |  C1 |  C0 |  A7 |  A6 |  A5 |  A4 |  A3 |  A2 |  A1 |  A0 |
     --------------------------------------------------------------------------------|
             | 800 | 400 | 200 | 100 | 080 | 040 | 020 | 010 | 008 | 004 | 002 | 001 |
     --------------------------------------------------------------------------------|
     B7 | 80 | ESC | КОИ |  F1 |  F2 |  F3 |  F4 |  F5 |  F6 |  F7 |  F8 |  F9 | СТР |
        |    |(Esc)|(F10)|     |     |     |     |     |     |     |     |     |(Ins)|
     B6 | 40 | ; + | 1 ! | 2 " | 3 # | 4 $ | 5 % | 6 & | 7 ' | 8 ( | 9 ) |  0  | - = |
        |    |( ` )|     |     |     |     |     |     |     |     |     |     |( - )|
     B5 | 20 | J Й | C Ц | U У | K K | E E | N Н | G Г | [ Ш | ] Щ | Z З | H Х | : * |
        |    |     |     |     |     |     |     |     |     |     |     |     |( = )|
     B4 | 10 | F Ф | Y Ы | W В | A A | P П | R Р | O O | L Л | D Д | V Ж | \ Э | . > |
     B3 | 08 | Q Я | ^ Ч | S С | M M | I И | T T | X Ь | B Б | @ Ю | , > | / ? |  ЗБ |
        |    |     |( ' )|     |     |     |     |     |     |( ; )|     |     |     |
     B2 | 04 | Р/Л |Home |  Up |Down |  ?  |  ?  | Spc |  <= |  ПВ |  => |  ПС |  ВК |
        |    |(Alt)|     |     |     |(F11)|(F12)|     |     |(Tab)|     |(End)|(Ent)|
     ---------------------------------------------------------------------------------

          Матрица клавиш 12х6 - Стандартный ПК "Специалист"
     ---------------------------------------------------------------------------------
             |  C3 |  C2 |  C1 |  C0 |  A7 |  A6 |  A5 |  A4 |  A3 |  A2 |  A1 |  A0 |
     --------------------------------------------------------------------------------|
             | 800 | 400 | 200 | 100 | 080 | 040 | 020 | 010 | 008 | 004 | 002 | 001 |
     --------------------------------------------------------------------------------|
     B7 | 80 |  F1 |  F2 |  F3 |  F4 |  F5 |  F6 |  F7 |  F8 |  F9 | Ч.Ф | Б.Ф | СТР |
        |    |     |     |     |     |     |     |     |     |     |(F10)|(F11)|(Ins)|
     B6 | 40 | ; + | 1 ! | 2 " | 3 # | 4 $ | 5 % | 6 & | 7 ' | 8 ( | 9 ) |  0  | - = |
        |    |( ` )|     |     |     |     |     |     |     |     |     |     |( - )|
     B5 | 20 | J Й | C Ц | U У | K K | E E | N Н | G Г | [ Ш | ] Щ | Z З | H Х | : * |
        |    |     |     |     |     |     |     |     |     |     |     |     |( = )|
     B4 | 10 | F Ф | Y Ы | W В | A A | P П | R Р | O O | L Л | D Д | V Ж | \ Э | . > |
     B3 | 08 | Q Я | ^ Ч | S С | M M | I И | T T | X Ь | B Б | @ Ю | , > | / ? |  ЗБ |
        |    |     |( ' )|     |     |     |     |     |     |( ; )|     |     |     |
     B2 | 04 | Р/Л |Home |  Up |Down | TAB | АР2 | Spc |  <= |  ПВ |  => |  ПС |  ВК |
        |    |(Alt)|     |     |     |(Tab)|(Esc)|     |     |(F12)|     |(End)|(Ent)|
     ---------------------------------------------------------------------------------
    */

    // Битовые маски в формате B7-B0_C3-C0_A7-A0
    private static final int[] BIT_MASKS_MX = {
    //             0         1         2         3         4         5         6         7         8         9         A         B         C         D         E         F
    /* 0 */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x08_001, 0x04_008, 0x04_001, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 1 */ 0x00_000, 0x00_000, 0x04_800, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x80_800, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 2 */ 0x04_020, 0x00_000, 0x00_000, 0x04_002, 0x04_400, 0x04_010, 0x04_200, 0x04_004, 0x04_100, 0x00_000, 0x00_000, 0x00_000, 0x08_004, 0x40_001, 0x10_001, 0x08_002,
    /* 3 */ 0x40_002, 0x40_400, 0x40_200, 0x40_100, 0x40_080, 0x40_040, 0x40_020, 0x40_010, 0x40_008, 0x40_004, 0x00_000, 0x08_008, 0x00_000, 0x20_001, 0x00_000, 0x00_000,
    /* 4 */ 0x00_000, 0x10_100, 0x08_010, 0x20_400, 0x10_008, 0x20_080, 0x10_800, 0x20_020, 0x20_002, 0x08_080, 0x20_800, 0x20_100, 0x10_010, 0x08_100, 0x20_040, 0x10_020,
    /* 5 */ 0x10_080, 0x08_800, 0x10_040, 0x08_200, 0x08_040, 0x20_200, 0x10_004, 0x10_200, 0x08_020, 0x10_400, 0x20_004, 0x20_010, 0x10_002, 0x20_008, 0x00_000, 0x00_000,
    /* 6 */ 0x40_002, 0x40_400, 0x40_200, 0x40_100, 0x40_080, 0x40_040, 0x40_020, 0x40_010, 0x40_008, 0x40_004, 0x20_001, 0x40_800, 0x00_000, 0x40_001, 0x10_001, 0x08_002,
    /* 7 */ 0x80_200, 0x80_100, 0x80_080, 0x80_040, 0x80_020, 0x80_010, 0x80_008, 0x80_004, 0x80_002, 0x80_400, 0x04_080, 0x04_040, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 8 */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 9 */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x80_001, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* A */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* B */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* C */ 0x40_800, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* D */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x08_400, 0x00_000,
    /* E */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* F */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000
    };

    // Битовые маски в формате B7-B0_C3-C0_A7-A0
    private static final int[] BIT_MASKS_ST = {
    //             0         1         2         3         4         5         6         7         8         9         A         B         C         D         E         F
    /* 0 */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x08_001, 0x04_080, 0x04_001, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 1 */ 0x00_000, 0x00_000, 0x04_800, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x04_040, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 2 */ 0x04_020, 0x00_000, 0x00_000, 0x04_002, 0x04_400, 0x04_010, 0x04_200, 0x04_004, 0x04_100, 0x00_000, 0x00_000, 0x00_000, 0x08_004, 0x40_001, 0x10_001, 0x08_002,
    /* 3 */ 0x40_002, 0x40_400, 0x40_200, 0x40_100, 0x40_080, 0x40_040, 0x40_020, 0x40_010, 0x40_008, 0x40_004, 0x00_000, 0x08_008, 0x00_000, 0x20_001, 0x00_000, 0x00_000,
    /* 4 */ 0x00_000, 0x10_100, 0x08_010, 0x20_400, 0x10_008, 0x20_080, 0x10_800, 0x20_020, 0x20_002, 0x08_080, 0x20_800, 0x20_100, 0x10_010, 0x08_100, 0x20_040, 0x10_020,
    /* 5 */ 0x10_080, 0x08_800, 0x10_040, 0x08_200, 0x08_040, 0x20_200, 0x10_004, 0x10_200, 0x08_020, 0x10_400, 0x20_004, 0x20_010, 0x10_002, 0x20_008, 0x00_000, 0x00_000,
    /* 6 */ 0x40_002, 0x40_400, 0x40_200, 0x40_100, 0x40_080, 0x40_040, 0x40_020, 0x40_010, 0x40_008, 0x40_004, 0x20_001, 0x40_800, 0x00_000, 0x40_001, 0x10_001, 0x08_002,
    /* 7 */ 0x80_800, 0x80_400, 0x80_200, 0x80_100, 0x80_080, 0x80_040, 0x80_020, 0x80_010, 0x80_008, 0x80_004, 0x80_002, 0x04_008, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 8 */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* 9 */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x80_001, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* A */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* B */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* C */ 0x40_800, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* D */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x08_400, 0x00_000,
    /* E */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000,
    /* F */ 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000, 0x00_000
    };

    private int fPA, fPB, fPC, fPR;
    private final cSpeaker fSpeaker;
    private final List<Integer> fKeyBuffer;
    private volatile boolean fShiftKey, fKeyboardMode;

    cMD_SpMX_KeyPort(cSpeaker speaker) {
        fPR = 0b1001_1011; // начальная инициализация - режим 0, все порты на ввод
        fSpeaker = speaker;
        fKeyBuffer = new CopyOnWriteArrayList<>();
    }

    @Override
    public int getMemoryDeviceLength() {
        return MEMORY_DEVICE_LENGTH;
    }

    @Override
    public int readByte(int address) {
        if ((address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            int result = 0xFF;
            switch (address) {
                case 0:
                    if         ((fPR & 0b1101_0000) == 0b1000_0000) { // режим - 0 или 1, порт А - вывод
                        result = fPA;
                    } else  if ((fPR & 0b1101_0010) == 0b1001_0000) { // режим - 0 или 1, порт А - ввод, порт B - вывод
                        for (int curBitMask : fKeyBuffer) {
                            if ((curBitMask != 0) && ((fPB & (curBitMask >> 12)) == 0)) {
                                result &= ~curBitMask;
                            }
                        }
                    }
                    break;
                case 1:
                    if ((fPR & 0b1000_0010) == 0b1000_0000) { // режим - 0 или 1, порт B - вывод
                        result = fPB;
                    } else {
                        switch (fPR & 0b1101_0111) {
                            case 0b1000_0010: // режим группы А - 0 или 1, режим группы B - 0, порт А - вывод, порт B - ввод, порт С3-С0 - вывод
                                for (int curBitMask : fKeyBuffer) {
                                    if ((curBitMask != 0) && ((((fPC << 8) | fPA) & curBitMask & 0xFFF) == 0)) {
                                        result &= ~(curBitMask >> 12);
                                    }
                                }
                                if (fShiftKey) {
                                    result &= 0b1111_1101;
                                }
                                break;
                            case 0b1000_0011: // режим группы А - 0 или 1, режим группы B - 0 или 1, порт А - вывод, порт B - ввод, порт С3-С0 - ввод
                            case 0b1000_0110: // -//-, но порт С3-С0 - в режиме 1 (хз что будет)
                            case 0b1000_0111: // -//-, но порт С3-С0 - в режиме 1 (хз что будет)
                                for (int curBitMask : fKeyBuffer) {
                                    if ((curBitMask != 0) && ((fPA & curBitMask & 0xFF) == 0)) {
                                        result &= ~(curBitMask >> 12);
                                    }
                                }
                                if (fShiftKey) {
                                    result &= 0b1111_1101;
                                }
                                break;
                            case 0b1001_0010: // режим группы А - 0 или 1, режим группы B - 0, порт А - ввод, порт B - ввод, порт С3-С0 - вывод
                                for (int curBitMask : fKeyBuffer) {
                                    if ((curBitMask != 0) && (((fPC << 8) & curBitMask & 0xF00) == 0)) {
                                        result &= ~(curBitMask >> 12);
                                    }
                                }
                                if (fShiftKey) {
                                    result &= 0b1111_1101;
                                }
                                break;
                        }
                    }
                    break;
                case 2:
                    if ((fPR & 0b1100_0111) == 0b1000_0001) { // режим группы A - 0 или 1, режим группы B - 0, порт B - вывод, порт C3-C0 - ввод
                        for (int curBitMask : fKeyBuffer) {
                            if ((curBitMask != 0) && ((fPB & (curBitMask >> 12)) == 0)) {
                                result &= ~((curBitMask >> 8) & 0xF);
                            }
                        }
                        if ((fPR & 0b10_1000) == 0) { // режим группы A - 0, порт C7-C4 - вывод
                            result &= fPC | 0xF;
                        } else {
                            result &= 0xF; // хз
                        }
                    } else {
                        if ((fPR & 0b1000_0101) == 0b1000_0000) { // режим - 0, порт C3-C0 - вывод
                            result &= fPC | 0xF0;
                        }
                        if ((fPR & 0b1000_1100) == 0b1000_0000) { // режим - 0, порт C7-C4 - вывод
                            result &= fPC | 0xF;
                        } else {
                            result &= 0xF; // хз
                        }
                    }
                    break;
                case 3:
                    break;
            }
            return result;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < MEMORY_DEVICE_LENGTH)) {
            switch (address) {
                case 0: // режим - 0 или 1, порт А - вывод
                    if ((fPR & 0b1101_0000) == 0b1000_0000) {
                        fPA = value;
                    }
                    break;
                case 1: // режим - 0 или 1, порт B - вывод
                    if ((fPR & 0b1000_0010) == 0b1000_0000) {
                        fPB = value;
                    }
                    break;
                case 2: // режим - 0, порт C3-C0 - вывод
                    if ((fPR & 0b1000_0101) == 0b1000_0000) {
                        fPC = (fPC & 0xF0) | (value & 0xF);
                    }
                    // режим - 0, порт C7-C4 - вывод (здесь порты вывода звука и т.д.)
                    if ((fPR & 0b1000_1100) == 0b1000_0000) {
                        fPC = (fPC & 0xF) | (value & 0xF0);
                        if (fSpeaker != null) {
                            fSpeaker.play8255((value & 0b10_0000) != 0);
                        }
                    }
                    break;
                case 3:
                    if ((value & 0b1000_0000) == 0) {
                        if ((value & 1) != 0) {
                            fPC |=  1 << ((value >> 1) & 0b111);
                        } else {
                            fPC &= (1 << ((value >> 1) & 0b111)) ^ 0xFF;
                        }
                        if ((fSpeaker != null) && ((value & 0b1110) == 0b1010)) {
                            fSpeaker.play8255((fPC & 0b10_0000) != 0);
                        }
                    } else {
                        fPR = value;
                        fPA = fPB = fPC = 0;
                    }
                    break;
            }
        }
    }

    @Override
    public void reset(boolean clear) {
        fPA = fPB = fPC = 0;
        fPR =   0b1001_1011; // режим 0, все порты на ввод
        fShiftKey =   false;
        if (clear) {
            clearKeyBuffer();
        }
        if (fSpeaker != null) {
            fSpeaker.play8255(false); // Чтобы не был заперт вывод звука через ВИ53
        }
    }

    @Override
    public void close() {
        if (fSpeaker != null) {
            fSpeaker.close();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        cMD_SpMX_KeyPort that = (cMD_SpMX_KeyPort) o;
        return Objects.equals(this.fSpeaker, that.fSpeaker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fSpeaker);
    }

    /**
     * Устанавливает режим работы порта по умолчанию.
     */
    synchronized void setDefaultMode() {
        fPR = 0b1000_0010; // порт А - вывод, порт B - ввод, порт С3-С0 - вывод
    }

    /**
     * Возвращает режим работы клавиатуры.
     * @return false = "Специалист MX" / true = стандартный "Специалист"
     */
    boolean isKeyboardMode() {
        return fKeyboardMode;
    }

    /**
     * Устанавливает режим работы клавиатуры.
     * @param keyboardMode false = "Специалист MX" / true = стандартный "Специалист"
     */
    void setKeyboardMode(boolean keyboardMode) {
        fKeyboardMode = keyboardMode;
    }

    /**
     * Очищает клавиатурный буфер.
     */
    void clearKeyBuffer() {
        if (!fKeyBuffer.isEmpty()) {
             fKeyBuffer.clear();
        }
    }

    /**
     * Принимает коды клавиш.
     * @param flagKeyPressed true = клавиша нажата, false = клавиша отпущена
     * @param keyCode код клавиши
     */
    void keyCodeReceiver(boolean flagKeyPressed, int keyCode) {
        if (keyCode <= 0xFF) {
            if (keyCode == KeyEvent.VK_SHIFT) {
                fShiftKey = flagKeyPressed;
            } else {
                int bitMask = fKeyboardMode ? BIT_MASKS_ST[keyCode] : BIT_MASKS_MX[keyCode];
                if (flagKeyPressed) {
                    if (fKeyBuffer.indexOf(bitMask) == -1) {
                        fKeyBuffer.add(bitMask);
                    }
                } else {
                    fKeyBuffer.remove((Integer) bitMask);
                }
            }
        }
    }
}