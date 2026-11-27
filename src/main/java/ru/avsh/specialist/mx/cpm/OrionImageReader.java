package ru.avsh.specialist.mx.cpm;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Разбирает образ диска, у которого формат объявлен в первом секторе, — так называемый
 * «Орион-стандарт»: параметры диска (DPH/DPB) прямо в начале файла.
 *
 * <p>Такие образы встречаются под расширением {@code .kdi} (каталог 8020: {@code cpm80.kdi},
 * {@code Ep.kdi}, {@code Games01..09.kdi} и д.), но контейнером KDI они не являются: у контейнера
 * 256-байтный заголовок с адресами загрузки и таблицей перемежения, а здесь первые байты — это
 * обычный первый сектор образа. Признак — код загрузчика {@code 0x80} в первом байте и связный
 * блок параметров диска CP/M 2.2 на смещении 16.
 *
 * <p><b>Заголовок не сдвигает тело.</b> Байты {@code 0..31} лежат на своих местах в образе:
 * это содержимое первого блока, поэтому каталог и все смещения считаются от начала файла,
 * как у любого линейного образа. Тело не отрезается — иначе адреса блоков в каталоге
 * разъехались бы с содержимым диска.
 *
 * <p>Раскладка первого сектора (порядок байт — младший первым, как у Z80):
 * <pre>
 *  0  DB      код загрузчика: 0x80 — загрузка с 0x80
 *  1  ..3     {@code C3 00 DA} — начало команды {@code JP 0xDA00}
 *  4  DW      адрес запуска: у всех образов 0x000A
 *  6  DB      код заполнения: 0x00 (ни у одного образа 8020 не 0xE5)
 *  7  DB      1 — признак формата
 *  8  DB      1 — второй признак формата (у microdos при OFS=3 тоже 1)
 *  9  DB      1
 * 10  DW      0x0103 — версия BPB
 * 12  DW      5 — число секторов на дорожке стороны
 * 14  DW      80 — число цилиндров
 * 16  DB 13   блок параметров диска CP/M 2.2, байт в байт как в документации:
 *             SPT(сл), SPT(ст), BSH, BLM, EXM, DSM(сл), DSM(ст), DRM(сл), DRM(ст),
 *             AL0, AL1, CKS(сл), CKS(ст)
 * 29  DW      количество системных дорожек одной стороны ({@code OFS})
 * 31  DB      последний занимаемый блок области данных, младший байт {@code DSM}
 * </pre>
 *
 * <p>Полей «стороны» в заголовке нет: слово 12 — это секторы на дорожке <em>одной</em> стороны,
 * слово 14 — цилиндры, а байт 6 — код заполнения. Сторонность поэтому определяется по тому,
 * где на самом деле лежит каталог: у образов 8020 {@code OFS = 2} и каталог стоит по смещению
 * 10 240 = 2 x 5 x 1024 (одна сторона) — то есть 819 200 байт образа это 160 дорожек
 * одностороннего диска, а не 80 цилиндров двустороннего. У {@code microdos.kdi} {@code OFS = 3}
 * и каталог на 15 360, где лежит первая запись {@code BASHE.BAS}.
 *
 * <p>Контрольной суммы в заголовке нет: ни сумма байтов, ни сумма со слагаемым KDI
 * ({@code 0x66}), ни XOR с байтом 31 не дают совпадения ни на одном образе. Проверка
 * «Орион-стандарта» по утилите Black Cat'а опирается, видимо, на другие правила, и пока
 * они не известны, узнавать заголовок приходится по связности DPB и по каталогу, который
 * действительно начинается с объявленного места.
 *
 * @author -=AVSh=-
 */
final class OrionImageReader {
    /** Смещение блока параметров диска: он лежит в первом секторе образа. */
    private static final int DPB_OFFSET = 16;
    /** Смещение поля {@code OFS} — числа системных дорожек одной стороны. */
    private static final int OFS_OFFSET = 29;
    /** Смещение байта с признаком формата. */
    private static final int MARK_OFFSET = 8;
    /** Смещение слова с числом секторов на дорожке одной стороны. */
    private static final int SECTORS_OFFSET = 12;
    /** Смещение слова с числом цилиндров. */
    private static final int CYLINDERS_OFFSET = 14;
    /** Размер логической записи CP/M. */
    private static final int RECORD_SIZE = 128;
    /** Размер записи каталога CP/M. */
    private static final int DIR_ENTRY_SIZE = 32;
    /** Байт «свободно» в каталоге: с него начинается пустой диск. */
    private static final int FREE_BYTE = 0xE5;
    /** Код пользователя CP/M: от 0 до 15, больше — уже не каталог. */
    private static final int MAX_USER_NUMBER = 15;
    /** Предел записей каталога: номер записи в CP/M 8-битный. */
    private static final int MAX_DIR_ENTRIES = 256;
    /** Предел номера блока: 16-битное поле в записи каталога. */
    private static final int MAX_BLOCKS = 0x10000;

    /**
     * Конструктор.
     */
    private OrionImageReader() {
        //
    }

    /**
     * Проверяет, что первый сектор файла описывает формат диска CP/M и файл является образом.
     *
     * <p>Признаки проверяются вместе, потому что по отдельности каждый встречается и в чужих
     * файлах: у контейнера KDI на смещении 16 лежит мусор, у записи B.01 — код загрузчика.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщения
     * @return true — образ объявляет свой формат в первом секторе
     */
    static boolean isOrionImage(final byte[] image, final String name) {
        if ((image.length <= DPB_OFFSET) || (image[0] != (byte) 0x80)) {
            return false;
        }
        try {
            buildProfile(image, name);
            return true;
        } catch (final IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Строит раскладку по первому сектору.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщения об ошибке
     * @return раскладка образа
     * @throws IOException если заголовок противоречив
     */
    static DiskProfile readProfile(final byte[] image, final String name) throws IOException {
        if ((image.length <= DPB_OFFSET) || (image[0] != (byte) 0x80)) {
            throw new IOException("Не «Орион-образ»: нет кода загрузчика 0x80 в первом байте (" + name + ")");
        }
        return buildProfile(image, name);
    }

    /**
     * Читает файлы образа, объявившего свой формат.
     *
     * <p>Тело берётся как есть, начиная с первого байта: заголовок — это содержимое первого
     * блока, а не надстройка над образом. Хвост длиннее объявленного DPB отрезается: у
     * {@code Games09.kdi} файл на 1 КБ длиннее диска, а каталог ссылается только на блоки
     * внутри него.
     *
     * @param image байты файла
     * @param name  имя файла для сообщения
     * @return файлы в порядке появления их первых записей в каталоге
     * @throws IOException если заголовок или каталог не разбираются
     */
    static List<CpmImageReader.CpmFile> readAllFiles(final byte[] image, final String name)
            throws IOException {
        final DiskProfile profile = readProfile(image, name);
        final int declared = (word(image, DPB_OFFSET + 5) + 1) * (RECORD_SIZE << byteAt(image, DPB_OFFSET + 2));
        if ((declared > 0) && (declared < image.length)) {
            // Объявленный диск короче файла: за его концом мусор, и читать оттуда нечего.
            return CpmImageReader.readAllFiles(Arrays.copyOf(image, declared), profile);
        }
        return CpmImageReader.readAllFiles(image, profile);
    }

    /**
     * Собирает профиль по полям первого сектора и сверяет их между собой.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщения об ошибке
     * @return раскладка образа
     * @throws IOException если поля заголовка противоречат друг другу
     */
    private static DiskProfile buildProfile(final byte[] image, final String name) throws IOException {
        final int spt = word  (image, DPB_OFFSET);
        final int bsh = byteAt(image, DPB_OFFSET + 2);
        final int blm = byteAt(image, DPB_OFFSET + 3);
        final int dsm = word  (image, DPB_OFFSET + 5);
        final int drm = word  (image, DPB_OFFSET + 7);
        final int ofs = word  (image, OFS_OFFSET);

        final int sectorsPerSide = word(image, SECTORS_OFFSET);
        final int cylinders      = word(image, CYLINDERS_OFFSET);

        if ((bsh < 3) || (bsh > 7)) {
            throw new IOException(String.format(
                    "«Орион-образ»: BSH=%d вне диапазона 3..7 (%s)", bsh, name));
        }
        final int blockSize = RECORD_SIZE << bsh;
        // BLM — маска младших разрядов номера записи в блоке: число записей всегда степень двойки.
        if (blm != ((blockSize / RECORD_SIZE) - 1)) {
            throw new IOException(String.format(
                    "«Орион-образ»: BLM=%02X не согласован с BSH=%d (блок %d байт) (%s)",
                    blm, bsh, blockSize, name));
        }
        // Байт 8 — признак формата, а не дубль OFS: у microdos OFS=3, а там всё равно 1.
        if ((spt <= 0) || (cylinders == 0) || (sectorsPerSide == 0)
                || ((spt % sectorsPerSide) != 0) || (byteAt(image, MARK_OFFSET) != 1)) {
            throw new IOException(String.format(
                    "«Орион-образ»: геометрия не согласована — SPT=%d, секторов на дорожке %d, "
                            + "цилиндров %d, байт 8 = %d (%s)",
                    spt, sectorsPerSide, cylinders, byteAt(image, MARK_OFFSET), name));
        }
        if (((drm + 1) > MAX_DIR_ENTRIES) || ((dsm + 1) > MAX_BLOCKS)) {
            throw new IOException(String.format(
                    "«Орион-образ»: параметры за пределами CP/M 2.2 — записей каталога %d, блоков %d (%s)",
                    drm + 1, dsm + 1, name));
        }

        // SPT в CP/M — число 128-байтных записей на дорожке: у 5 секторов по 1024 байта это 40.
        // Отсюда размер сектора, а не наоборот: 1024 у корветовских образов не записан нигде.
        final int sectorSize = (spt / sectorsPerSide) * RECORD_SIZE;
        final int sideSize   = sectorSize * sectorsPerSide;
        final int length     = image.length;

        // OFS в заголовке — дорожки одной стороны: у microdos OFS=3 даёт каталог на 15 360,
        // где и лежит первая запись BASHE.BAS.
        if ((ofs <= 0) || ((ofs * sideSize) >= length)) {
            throw new IOException(String.format(
                    "«Орион-образ»: системных дорожек %d при образе %d байт (%s)",
                    ofs, length, name));
        }
        final int dirOffset = ofs * sideSize;

        // Сторонность определяется каталогом, а не длиной: у образов 8020 он лежит через две
        // дорожки одной стороны, поэтому диск читается как 160 односторонних дорожек.
        final int sides;
        if (hasDirectoryAt(image, dirOffset, dsm, blockSize)) {
            sides = 1;
        } else if (hasDirectoryAt(image, dirOffset + sideSize, dsm, blockSize)) {
            sides = 2;
        } else {
            throw new IOException(String.format(
                    "«Орион-образ»: по смещениям %d и %d нет каталога CP/M (%s)",
                    dirOffset, dirOffset + sideSize, name));
        }

        if ((length % sideSize) != 0) {
            // Хвост короче дорожки не мешает: у Games01.kdi образ обрезан на 2048 байт,
            // а каталог и все блоки, на которые он ссылается, лежат целиком.
            if ((length - (length % sideSize)) <= sideSize) {
                throw new IOException(String.format(
                        "«Орион-образ»: образ %d байт не кратен дорожке %d байт (%s)",
                        length, sideSize, name));
            }
        }
        // Слово 14 «цилиндров» сверять с длиной нельзя: у образов 8020 там 80, а дорожек
        // в полтора-два раза больше. Годность геометрии решает каталог, найденный по OFS.
        final int tracks = (length + sideSize - 1) / sideSize;
        if (ofs >= tracks) {
            throw new IOException(String.format(
                    "«Орион-образ»: системных дорожек %d при %d дорожках (%s)",
                    ofs, tracks, name));
        }

        final String profileName = String.format(
                "«Орион-образ» %dx%dx%dx%d (%d KB, block %d, %d dir, %d sys) — %s",
                tracks, sides, sectorsPerSide, sectorSize, length / 1024,
                blockSize, drm + 1, ofs, name);

        return DiskProfile.of(profileName, tracks, sides, sectorsPerSide, sectorSize, ofs,
                blockSize, drm + 1, null, 0);
    }

    /**
     * Проверяет, что по смещению начинается каталог CP/M — с теми же послаблениями, с которыми
     * его читает {@link CpmImageReader}: счётчик записей в записи каталога не проверяется,
     * потому что образ {@code microdos.kdi} хранит там ноль.
     *
     * @param image     байты образа
     * @param offset    смещение начала каталога
     * @param dsm       номер последнего блока области данных
     * @param blockSize размер блока в байтах
     * @return true — по смещению лежит каталог
     */
    private static boolean hasDirectoryAt(final byte[] image ,
                                          final int    offset,
                                          final int    dsm   ,
                                          final int    blockSize) {

        if ((offset <= 0) || ((offset + DIR_ENTRY_SIZE) > image.length)) {
            return false;
        }
        final boolean is16Bit = ((dsm + 1) > 255);
        final int slots = is16Bit ? 8 : 16;

        for (int i = 0; i < MAX_DIR_ENTRIES; i++) {
            final int entry = offset + (i * DIR_ENTRY_SIZE);
            if ((entry + DIR_ENTRY_SIZE) > image.length) {
                return false;
            }
            final int status = image[entry] & 0xFF;
            if (status == FREE_BYTE) {
                return i > 0;
            }
            if (status > MAX_USER_NUMBER) {
                return false;
            }
            for (int c = 1; c <= 11; c++) {
                final int ch = image[entry + c] & 0x7F;
                if ((ch < 0x20) || (ch > 0x7E)) {
                    return false;
                }
            }
            for (int b = 0; b < slots; b++) {
                final int blk = is16Bit
                        ? word(image, entry + 16 + (b * 2))
                        : (image[entry + 16 + b] & 0xFF);
                if ((blk != 0) && (blk > dsm)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int byteAt(final byte[] image, final int offset) {
        return image[offset] & 0xFF;
    }

    private static int word(final byte[] image, final int offset) {
        return (image[offset] & 0xFF) | ((image[offset + 1] & 0xFF) << 8);
    }
}
