package ru.avsh.specialist.mx.units.memory.units;

import javafx.scene.input.KeyCode;
import ru.avsh.specialist.mx.units.Speaker;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Адресуемое устройство "Порт клавиатуры Specialist_MX на базе КР580ВВ55А (i8255A)".
 *
 * @author -=AVSh=-
 */
public final class KeyboardPort implements MemoryUnit {
    private static final int STORAGE_SIZE = 4;

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

    // Битовые маски в формате B7-B0_C3-C0_A7-A0 - ПК "Специалист MX"
    private static final Map<KeyCode, Integer> BIT_MASKS_MX = new EnumMap<>(KeyCode.class);
    static {
        BIT_MASKS_MX.put(KeyCode.ENTER        , 0x04_001);
        BIT_MASKS_MX.put(KeyCode.END          , 0x04_002);
        BIT_MASKS_MX.put(KeyCode.RIGHT        , 0x04_004);
        BIT_MASKS_MX.put(KeyCode.TAB          , 0x04_008);
        BIT_MASKS_MX.put(KeyCode.LEFT         , 0x04_010);
        BIT_MASKS_MX.put(KeyCode.SPACE        , 0x04_020);
        BIT_MASKS_MX.put(KeyCode.F12          , 0x04_040);
        BIT_MASKS_MX.put(KeyCode.F11          , 0x04_080);
        BIT_MASKS_MX.put(KeyCode.DOWN         , 0x04_100);
        BIT_MASKS_MX.put(KeyCode.UP           , 0x04_200);
        BIT_MASKS_MX.put(KeyCode.HOME         , 0x04_400);
        BIT_MASKS_MX.put(KeyCode.ALT          , 0x04_800);
        BIT_MASKS_MX.put(KeyCode.ALT_GRAPH    , 0x04_800);
        BIT_MASKS_MX.put(KeyCode.BACK_SPACE   , 0x08_001);
        BIT_MASKS_MX.put(KeyCode.SLASH        , 0x08_002);
        BIT_MASKS_MX.put(KeyCode.DIVIDE       , 0x08_002);
        BIT_MASKS_MX.put(KeyCode.COMMA        , 0x08_004);
        BIT_MASKS_MX.put(KeyCode.SEMICOLON    , 0x08_008);
        BIT_MASKS_MX.put(KeyCode.B            , 0x08_010);
        BIT_MASKS_MX.put(KeyCode.X            , 0x08_020);
        BIT_MASKS_MX.put(KeyCode.T            , 0x08_040);
        BIT_MASKS_MX.put(KeyCode.I            , 0x08_080);
        BIT_MASKS_MX.put(KeyCode.M            , 0x08_100);
        BIT_MASKS_MX.put(KeyCode.S            , 0x08_200);
        BIT_MASKS_MX.put(KeyCode.QUOTE        , 0x08_400);
        BIT_MASKS_MX.put(KeyCode.Q            , 0x08_800);
        BIT_MASKS_MX.put(KeyCode.PERIOD       , 0x10_001);
        BIT_MASKS_MX.put(KeyCode.DECIMAL      , 0x10_001);
        BIT_MASKS_MX.put(KeyCode.BACK_SLASH   , 0x10_002);
        BIT_MASKS_MX.put(KeyCode.V            , 0x10_004);
        BIT_MASKS_MX.put(KeyCode.D            , 0x10_008);
        BIT_MASKS_MX.put(KeyCode.L            , 0x10_010);
        BIT_MASKS_MX.put(KeyCode.O            , 0x10_020);
        BIT_MASKS_MX.put(KeyCode.R            , 0x10_040);
        BIT_MASKS_MX.put(KeyCode.P            , 0x10_080);
        BIT_MASKS_MX.put(KeyCode.A            , 0x10_100);
        BIT_MASKS_MX.put(KeyCode.W            , 0x10_200);
        BIT_MASKS_MX.put(KeyCode.Y            , 0x10_400);
        BIT_MASKS_MX.put(KeyCode.F            , 0x10_800);
        BIT_MASKS_MX.put(KeyCode.EQUALS       , 0x20_001);
        BIT_MASKS_MX.put(KeyCode.MULTIPLY     , 0x20_001);
        BIT_MASKS_MX.put(KeyCode.H            , 0x20_002);
        BIT_MASKS_MX.put(KeyCode.Z            , 0x20_004);
        BIT_MASKS_MX.put(KeyCode.CLOSE_BRACKET, 0x20_008);
        BIT_MASKS_MX.put(KeyCode.OPEN_BRACKET , 0x20_010);
        BIT_MASKS_MX.put(KeyCode.G            , 0x20_020);
        BIT_MASKS_MX.put(KeyCode.N            , 0x20_040);
        BIT_MASKS_MX.put(KeyCode.E            , 0x20_080);
        BIT_MASKS_MX.put(KeyCode.K            , 0x20_100);
        BIT_MASKS_MX.put(KeyCode.U            , 0x20_200);
        BIT_MASKS_MX.put(KeyCode.C            , 0x20_400);
        BIT_MASKS_MX.put(KeyCode.J            , 0x20_800);
        BIT_MASKS_MX.put(KeyCode.MINUS        , 0x40_001);
        BIT_MASKS_MX.put(KeyCode.SUBTRACT     , 0x40_001);
        BIT_MASKS_MX.put(KeyCode.DIGIT0       , 0x40_002);
        BIT_MASKS_MX.put(KeyCode.NUMPAD0      , 0x40_002);
        BIT_MASKS_MX.put(KeyCode.DIGIT9       , 0x40_004);
        BIT_MASKS_MX.put(KeyCode.NUMPAD9      , 0x40_004);
        BIT_MASKS_MX.put(KeyCode.DIGIT8       , 0x40_008);
        BIT_MASKS_MX.put(KeyCode.NUMPAD8      , 0x40_008);
        BIT_MASKS_MX.put(KeyCode.DIGIT7       , 0x40_010);
        BIT_MASKS_MX.put(KeyCode.NUMPAD7      , 0x40_010);
        BIT_MASKS_MX.put(KeyCode.DIGIT6       , 0x40_020);
        BIT_MASKS_MX.put(KeyCode.NUMPAD6      , 0x40_020);
        BIT_MASKS_MX.put(KeyCode.DIGIT5       , 0x40_040);
        BIT_MASKS_MX.put(KeyCode.NUMPAD5      , 0x40_040);
        BIT_MASKS_MX.put(KeyCode.DIGIT4       , 0x40_080);
        BIT_MASKS_MX.put(KeyCode.NUMPAD4      , 0x40_080);
        BIT_MASKS_MX.put(KeyCode.DIGIT3       , 0x40_100);
        BIT_MASKS_MX.put(KeyCode.NUMPAD3      , 0x40_100);
        BIT_MASKS_MX.put(KeyCode.DIGIT2       , 0x40_200);
        BIT_MASKS_MX.put(KeyCode.NUMPAD2      , 0x40_200);
        BIT_MASKS_MX.put(KeyCode.DIGIT1       , 0x40_400);
        BIT_MASKS_MX.put(KeyCode.NUMPAD1      , 0x40_400);
        BIT_MASKS_MX.put(KeyCode.BACK_QUOTE   , 0x40_800);
        BIT_MASKS_MX.put(KeyCode.ADD          , 0x40_800);
        BIT_MASKS_MX.put(KeyCode.INSERT       , 0x80_001);
        BIT_MASKS_MX.put(KeyCode.F9           , 0x80_002);
        BIT_MASKS_MX.put(KeyCode.F8           , 0x80_004);
        BIT_MASKS_MX.put(KeyCode.F7           , 0x80_008);
        BIT_MASKS_MX.put(KeyCode.F6           , 0x80_010);
        BIT_MASKS_MX.put(KeyCode.F5           , 0x80_020);
        BIT_MASKS_MX.put(KeyCode.F4           , 0x80_040);
        BIT_MASKS_MX.put(KeyCode.F3           , 0x80_080);
        BIT_MASKS_MX.put(KeyCode.F2           , 0x80_100);
        BIT_MASKS_MX.put(KeyCode.F1           , 0x80_200);
        BIT_MASKS_MX.put(KeyCode.F10          , 0x80_400);
        BIT_MASKS_MX.put(KeyCode.ESCAPE       , 0x80_800);
    }

    // Битовые маски в формате B7-B0_C3-C0_A7-A0 - Стандартный ПК "Специалист"
    private static final Map<KeyCode, Integer> BIT_MASKS_ST = new EnumMap<>(KeyCode.class);
    static {
        BIT_MASKS_ST.put(KeyCode.ENTER        , 0x04_001);
        BIT_MASKS_ST.put(KeyCode.END          , 0x04_002);
        BIT_MASKS_ST.put(KeyCode.RIGHT        , 0x04_004);
        BIT_MASKS_ST.put(KeyCode.F12          , 0x04_008);
        BIT_MASKS_ST.put(KeyCode.LEFT         , 0x04_010);
        BIT_MASKS_ST.put(KeyCode.SPACE        , 0x04_020);
        BIT_MASKS_ST.put(KeyCode.ESCAPE       , 0x04_040);
        BIT_MASKS_ST.put(KeyCode.TAB          , 0x04_080);
        BIT_MASKS_ST.put(KeyCode.DOWN         , 0x04_100);
        BIT_MASKS_ST.put(KeyCode.UP           , 0x04_200);
        BIT_MASKS_ST.put(KeyCode.HOME         , 0x04_400);
        BIT_MASKS_ST.put(KeyCode.ALT          , 0x04_800);
        BIT_MASKS_ST.put(KeyCode.ALT_GRAPH    , 0x04_800);
        BIT_MASKS_ST.put(KeyCode.BACK_SPACE   , 0x08_001);
        BIT_MASKS_ST.put(KeyCode.SLASH        , 0x08_002);
        BIT_MASKS_ST.put(KeyCode.DIVIDE       , 0x08_002);
        BIT_MASKS_ST.put(KeyCode.COMMA        , 0x08_004);
        BIT_MASKS_ST.put(KeyCode.SEMICOLON    , 0x08_008);
        BIT_MASKS_ST.put(KeyCode.B            , 0x08_010);
        BIT_MASKS_ST.put(KeyCode.X            , 0x08_020);
        BIT_MASKS_ST.put(KeyCode.T            , 0x08_040);
        BIT_MASKS_ST.put(KeyCode.I            , 0x08_080);
        BIT_MASKS_ST.put(KeyCode.M            , 0x08_100);
        BIT_MASKS_ST.put(KeyCode.S            , 0x08_200);
        BIT_MASKS_ST.put(KeyCode.QUOTE        , 0x08_400);
        BIT_MASKS_ST.put(KeyCode.Q            , 0x08_800);
        BIT_MASKS_ST.put(KeyCode.PERIOD       , 0x10_001);
        BIT_MASKS_ST.put(KeyCode.DECIMAL      , 0x10_001);
        BIT_MASKS_ST.put(KeyCode.BACK_SLASH   , 0x10_002);
        BIT_MASKS_ST.put(KeyCode.V            , 0x10_004);
        BIT_MASKS_ST.put(KeyCode.D            , 0x10_008);
        BIT_MASKS_ST.put(KeyCode.L            , 0x10_010);
        BIT_MASKS_ST.put(KeyCode.O            , 0x10_020);
        BIT_MASKS_ST.put(KeyCode.R            , 0x10_040);
        BIT_MASKS_ST.put(KeyCode.P            , 0x10_080);
        BIT_MASKS_ST.put(KeyCode.A            , 0x10_100);
        BIT_MASKS_ST.put(KeyCode.W            , 0x10_200);
        BIT_MASKS_ST.put(KeyCode.Y            , 0x10_400);
        BIT_MASKS_ST.put(KeyCode.F            , 0x10_800);
        BIT_MASKS_ST.put(KeyCode.EQUALS       , 0x20_001);
        BIT_MASKS_ST.put(KeyCode.MULTIPLY     , 0x20_001);
        BIT_MASKS_ST.put(KeyCode.H            , 0x20_002);
        BIT_MASKS_ST.put(KeyCode.Z            , 0x20_004);
        BIT_MASKS_ST.put(KeyCode.CLOSE_BRACKET, 0x20_008);
        BIT_MASKS_ST.put(KeyCode.OPEN_BRACKET , 0x20_010);
        BIT_MASKS_ST.put(KeyCode.G            , 0x20_020);
        BIT_MASKS_ST.put(KeyCode.N            , 0x20_040);
        BIT_MASKS_ST.put(KeyCode.E            , 0x20_080);
        BIT_MASKS_ST.put(KeyCode.K            , 0x20_100);
        BIT_MASKS_ST.put(KeyCode.U            , 0x20_200);
        BIT_MASKS_ST.put(KeyCode.C            , 0x20_400);
        BIT_MASKS_ST.put(KeyCode.J            , 0x20_800);
        BIT_MASKS_ST.put(KeyCode.MINUS        , 0x40_001);
        BIT_MASKS_ST.put(KeyCode.SUBTRACT     , 0x40_001);
        BIT_MASKS_ST.put(KeyCode.DIGIT0       , 0x40_002);
        BIT_MASKS_ST.put(KeyCode.NUMPAD0      , 0x40_002);
        BIT_MASKS_ST.put(KeyCode.DIGIT9       , 0x40_004);
        BIT_MASKS_ST.put(KeyCode.NUMPAD9      , 0x40_004);
        BIT_MASKS_ST.put(KeyCode.DIGIT8       , 0x40_008);
        BIT_MASKS_ST.put(KeyCode.NUMPAD8      , 0x40_008);
        BIT_MASKS_ST.put(KeyCode.DIGIT7       , 0x40_010);
        BIT_MASKS_ST.put(KeyCode.NUMPAD7      , 0x40_010);
        BIT_MASKS_ST.put(KeyCode.DIGIT6       , 0x40_020);
        BIT_MASKS_ST.put(KeyCode.NUMPAD6      , 0x40_020);
        BIT_MASKS_ST.put(KeyCode.DIGIT5       , 0x40_040);
        BIT_MASKS_ST.put(KeyCode.NUMPAD5      , 0x40_040);
        BIT_MASKS_ST.put(KeyCode.DIGIT4       , 0x40_080);
        BIT_MASKS_ST.put(KeyCode.NUMPAD4      , 0x40_080);
        BIT_MASKS_ST.put(KeyCode.DIGIT3       , 0x40_100);
        BIT_MASKS_ST.put(KeyCode.NUMPAD3      , 0x40_100);
        BIT_MASKS_ST.put(KeyCode.DIGIT2       , 0x40_200);
        BIT_MASKS_ST.put(KeyCode.NUMPAD2      , 0x40_200);
        BIT_MASKS_ST.put(KeyCode.DIGIT1       , 0x40_400);
        BIT_MASKS_ST.put(KeyCode.NUMPAD1      , 0x40_400);
        BIT_MASKS_ST.put(KeyCode.BACK_QUOTE   , 0x40_800);
        BIT_MASKS_ST.put(KeyCode.ADD          , 0x40_800);
        BIT_MASKS_ST.put(KeyCode.INSERT       , 0x80_001);
        BIT_MASKS_ST.put(KeyCode.F11          , 0x80_002);
        BIT_MASKS_ST.put(KeyCode.F10          , 0x80_004);
        BIT_MASKS_ST.put(KeyCode.F9           , 0x80_008);
        BIT_MASKS_ST.put(KeyCode.F8           , 0x80_010);
        BIT_MASKS_ST.put(KeyCode.F7           , 0x80_020);
        BIT_MASKS_ST.put(KeyCode.F6           , 0x80_040);
        BIT_MASKS_ST.put(KeyCode.F5           , 0x80_080);
        BIT_MASKS_ST.put(KeyCode.F4           , 0x80_100);
        BIT_MASKS_ST.put(KeyCode.F3           , 0x80_200);
        BIT_MASKS_ST.put(KeyCode.F2           , 0x80_400);
        BIT_MASKS_ST.put(KeyCode.F1           , 0x80_800);
    }

    private int fPA;
    private int fPB;
    private int fPC;
    private int fPR;

    private final Speaker fSpeaker;
    private final List<Integer> fKeyBuffer;

    private final AtomicBoolean fShiftKey;
    private final AtomicBoolean fKeyboardMode;

    /**
     * Конструктор.
     *
     * @param speaker ссылка на объект класса Speaker - "Speaker (динамик)"
     */
    public KeyboardPort(Speaker speaker) {
        fPR        = 0b1001_1011; // начальная инициализация - режим 0, все порты на ввод
        fSpeaker   =     speaker;
        fKeyBuffer = new CopyOnWriteArrayList<>();

        fShiftKey     = new AtomicBoolean(false);
        fKeyboardMode = new AtomicBoolean(false);
    }

    @Override
    public int storageSize() {
        return STORAGE_SIZE;
    }

    @Override
    public int readByte(int address) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
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
                                if (fShiftKey.get()) {
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
                                if (fShiftKey.get()) {
                                    result &= 0b1111_1101;
                                }
                                break;
                            case 0b1001_0010: // режим группы А - 0 или 1, режим группы B - 0, порт А - ввод, порт B - ввод, порт С3-С0 - вывод
                                for (int curBitMask : fKeyBuffer) {
                                    if ((curBitMask != 0) && (((fPC << 8) & curBitMask & 0xF00) == 0)) {
                                        result &= ~(curBitMask >> 12);
                                    }
                                }
                                if (fShiftKey.get()) {
                                    result &= 0b1111_1101;
                                }
                                break;
                            default:
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
                //case 3:
                //    break;~
                default:
                    break;
            }
            return result;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if ((address >= 0) && (address < STORAGE_SIZE)) {
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
                default:
                    break;
            }
        }
    }

    @Override
    public void reset(boolean clear) {
        fPA = fPB = fPC = 0;
        fPR =   0b1001_1011; // режим 0, все порты на ввод
        fShiftKey.getAndSet(false);
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
        KeyboardPort that = (KeyboardPort) o;
        return Objects.equals(this.fSpeaker, that.fSpeaker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fSpeaker);
    }

    /**
     * Устанавливает режим работы порта по умолчанию.
     */
    public synchronized void setDefaultMode() {
        fPR = 0b1000_0010; // порт А - вывод, порт B - ввод, порт С3-С0 - вывод
    }

    /**
     * Возвращает режим работы клавиатуры.
     *
     * @return false = "Специалист MX" / true = стандартный "Специалист"
     */
    public boolean isKeyboardMode() {
        return fKeyboardMode.get();
    }

    /**
     * Устанавливает режим работы клавиатуры.
     *
     * @param keyboardMode false = "Специалист MX" / true = стандартный "Специалист"
     */
    public void setKeyboardMode(boolean keyboardMode) {
        fKeyboardMode.getAndSet(keyboardMode);
    }

    /**
     * Очищает клавиатурный буфер.
     */
    public void clearKeyBuffer() {
        if (!fKeyBuffer.isEmpty()) {
             fKeyBuffer.clear();
        }
    }

    /**
     * Принимает коды клавиш.
     *
     * @param flagKeyPressed true = клавиша нажата, false = клавиша отпущена
     * @param keyCode        код клавиши
     * @return true = событие клавиатуры обработано
     */
    public boolean keyCodeReceiver(final boolean flagKeyPressed, final KeyCode keyCode) {
        // Обработка клавиши Shift
        if (KeyCode.SHIFT.equals(keyCode)) {
            fShiftKey.getAndSet(flagKeyPressed);
            return true;
        }
        // Обработка остальных клавиш
        final Integer bitMask = fKeyboardMode.get() ? BIT_MASKS_ST.get(keyCode) : BIT_MASKS_MX.get(keyCode);
        if (bitMask == null) {
            return false;
        }
        if (flagKeyPressed) {
            if (fKeyBuffer.indexOf(bitMask) == -1) {
                fKeyBuffer.add    (bitMask);
            }
        } else {
                fKeyBuffer.remove (bitMask);
        }
        return true;
    }
}