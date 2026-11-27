package ru.avsh.specialist.mx.cpm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Читает образ дискеты в контейнере KDI (Korvet Disk Image) и раскладывает его в линейный образ.
 *
 * <p>Под этим расширением встречаются две разные вещи. Первая — контейнер с 256-байтным
 * заголовком: в нём лежат геометрия, полная таблица параметров диска CP/M (DPB) —
 * {@code SPT}, {@code BSH}, {@code BLM}, {@code EXM}, {@code DSM}, {@code DRM}, {@code AL0},
 * {@code AL1}, {@code CKS}, {@code OFS}, — и таблица перемежения, а тело файла уже линейный
 * образ. Вторая — запись образов «Специалиста MX» в формате утилиты B.01 (архив
 * zxbyte.ru, диски «Byte»/«Корвета»): 32 байта загрузчика в начале файла, дальше весь образ
 * целиком, без заголовка с параметрами. Различает их начало файла: запись B.01 открывается
 * строкой BIOS «{@code (C) cerikopik}», а у контейнера в первых словах адреса загрузки
 * и запуска, и контрольная сумма при этом не сходится.
 *
 * <p>Тело записи B.01 — обычный образ «Специалиста MX» на 800 КБ, но не развёрнутый по
 * дорожкам, а записанный как есть: каталог CP/M стоит там, откуда его читает загрузчик этого
 * образа, — через две дорожки, а не через четыре, как у {@code *.odi} этого проекта. Поэтому
 * раскладку для записи даёт готовый {@link DiskProfile#SPECIALIST_MX_800K}, переименованный
 * по имени файла, и отдаёт её {@link DiskProfileFactory}.
 *
 * <p>Порядок байт — младший первым (Z80). Раскладка заголовка контейнера:
 * <pre>
 *  0  DW адрес загрузки        16 DW SPT — секторов на дорожке
 *  2  DW адрес запуска         18 DB BSH
 *  4  DW счётчик блоков        19 DB BLM
 *  6  DB диаметр (8" / 5.25")  20 DB EXM
 *  7  DB плотность (FM / MFM)  21 DW DSM — всего блоков
 *  8  DB дорожек на дюйм       23 DW DRM — записей каталога минус одна
 *  9  DB коэффициент перемеж.  25 DB AL0
 * 10  DW секторов на дорожке  26 DB AL1
 * 12  DW дорожек на диске     27 DW CKS
 * 14  DB число сторон (0, 1)  29 DW OFS — системных дорожек
 * 32  DB контрольная сумма байт 1..31 плюс 0x66
 * 33  .. 128 — таблица перемежения (есть только при коэффициенте больше 1)
 * </pre>
 * Слова 14 записи B.01 ({@code 0x0249}) читаются как «стороны, дорожки» контейнера, и ни
 * одно значение сторон ни одному из двух форматов не принадлежит: 0x49 — не 0 и не 1.
 *
 * <p>Контейнеру из заголовка конвертеру нужны не все поля: {@code EXM} и {@code AL0}/{@code AL1}
 * не применяются, потому что маска экстента вычисляется из размера блока (см.
 * {@link CpmImageReader}), а таблицу занятости каталога заменяет сам каталог. Поле «счётчик
 * блоков» — длина файла в записях по 256 байт; оно сверяется с геометрией, но раскладку
 * не описывает.
 *
 * <p>Таблица перемежения в контейнере задана числами от 0: первый логический сектор дорожки
 * читается физическим сектором с номером из таблицы. В {@link DiskProfile} та же таблица
 * записана значениями от 1, поэтому при разборе она увеличивается на единицу. Когда
 * коэффициент перемежения равен 1, таблицы в файле нет и раскладка линейна.
 *
 * @author -=AVSh=-
 */
public final class KdiImageReader {
    /** Полный размер заголовка контейнера KDI: 32 байта параметров, байт контрольной суммы и таблица. */
    public static final int HEADER_SIZE = 256;
    /** Длина записи B.01 в начале файла: код запуска образа и адреса загрузчика. */
    public static final int B01_HEADER_SIZE = 32;
    /** Второе слово записи B.01 — хвост строки BIOS «(C) cerikopik»; в заголовке KDI это не бывает. */
    private static final int B01_MARKER = 0x2029;
    /** Первое слово записи B.01 — начало той же строки BIOS. */
    private static final int B01_SIGNATURE = 0x4328;
    /** Смещение байта контрольной суммы. */
    private static final int CHECKSUM_OFFSET = 32;
    /** Слагаемое контрольной суммы, по которому KDI отличается от чужих форматов. */
    private static final int CHECKSUM_BIAS = 0x66;
    /** Начало блока параметров диска в заголовке. */
    private static final int DPB_OFFSET = 16;
    /** Начало таблицы перемежения в заголовке. */
    private static final int SKEW_TABLE_OFFSET = 33;
    /** Размер логической записи CP/M в байтах. */
    private static final int RECORD_SIZE = 128;
    /** Размер блоков, которые бывают у CP/M 2.2; берётся из маски блока {@code BLM}. */
    private static final int[] BLOCK_SIZES = {1024, 2048, 4096, 8192, 16384};
    /** Предел записей каталога: номер записи в CP/M 8-битный, максимум — 256 записей. */
    private static final int MAX_DIR_ENTRIES = 256;

    /**
     * Параметры контейнера, прочитанные из заголовка.
     *
     * @author -=AVSh=-
     */
    public static final class KdiHeader {
        private final DiskProfile fProfile;
        private final int         fLoadAddress;
        private final int         fRunAddress;
        private final int         fImageRecords;
        private final int         fDiskSize;
        private final int         fDensity;
        private final int         fTracksPerInch;
        private final int         fSkewFactor;
        private final int         fSectorCode;
        private final int         fSectorsPerTrack;
        private final int         fDirectoryEntries;
        private final int[]       fSkewTable;

        /**
         * Конструктор.
         *
         * @param profile          раскладка диска, собранная из блока параметров
         * @param loadAddress      адрес загрузки образа
         * @param runAddress       адрес запуска образа
         * @param imageRecords     длина образа в записях по {@value #HEADER_SIZE} байт
         * @param diskSize         код диаметра дискеты
         * @param density          код плотности
         * @param tracksPerInch    дорожек на дюйм
         * @param skewFactor       коэффициент перемежения
         * @param sectorCode       код размера сектора
         * @param sectorsPerTrack  секторов на дорожке
         * @param directoryEntries число записей каталога
         * @param skewTable        таблица перемежения или null
         */
        private KdiHeader(final DiskProfile profile,
                          final int         loadAddress ,
                          final int         runAddress  ,
                          final int         imageRecords,
                          final int         diskSize,
                          final int         density ,
                          final int         tracksPerInch,
                          final int         skewFactor,
                          final int         sectorCode,
                          final int         sectorsPerTrack ,
                          final int         directoryEntries,
                          final int[]       skewTable) {

            this.fProfile          = profile     ;
            this.fLoadAddress      = loadAddress ;
            this.fRunAddress       = runAddress  ;
            this.fImageRecords     = imageRecords;
            this.fDiskSize         = diskSize;
            this.fDensity          = density ;
            this.fTracksPerInch    = tracksPerInch;
            this.fSkewFactor       = skewFactor;
            this.fSectorCode       = sectorCode;
            this.fSectorsPerTrack  = sectorsPerTrack ;
            this.fDirectoryEntries = directoryEntries;
            this.fSkewTable        = skewTable;
        }

        /**
         * @return раскладка для {@link CpmImageReader} и {@link DiskProfileFactory}
         */
        public DiskProfile getProfile() {
            return fProfile;
        }

        /**
         * @return адрес загрузки образа в памяти машины «Корвет»
         */
        public int getLoadAddress() {
            return fLoadAddress;
        }

        /**
         * @return адрес запуска образа в памяти машины «Корвет»
         */
        public int getRunAddress() {
            return fRunAddress;
        }

        /**
         * @return длина образа, записанная в заголовке, в записях по 256 байт
         */
        public int getImageRecords() {
            return fImageRecords;
        }

        /**
         * @return код диаметра дискеты: 0 — 8 дюймов, 1 — 5.25 дюйма
         */
        public int getDiskSize() {
            return fDiskSize;
        }

        /**
         * @return код плотности: 0 — FM, 1 — MFM
         */
        public int getDensity() {
            return fDensity;
        }

        /**
         * @return дорожек на дюйм
         */
        public int getTracksPerInch() {
            return fTracksPerInch;
        }

        /**
         * @return коэффициент перемежения: 0 или 1 — линейная раскладка, иначе — размер таблицы
         */
        public int getSkewFactor() {
            return fSkewFactor;
        }

        /**
         * @return код размера сектора: 0 — 128, 1 — 256, 2 — 512, 3 — 1024 байта
         */
        public int getSectorCode() {
            return fSectorCode;
        }

        /**
         * @return число секторов на дорожке из заголовка
         */
        public int getSectorsPerTrack() {
            return fSectorsPerTrack;
        }

        /**
         * @return число записей каталога ({@code DRM + 1} из блока параметров диска)
         */
        public int getDirectoryEntries() {
            return fDirectoryEntries;
        }

        /**
         * @return таблица перемежения значениями от 1 или null, если перемежения нет
         */
        public int[] getSkewTable() {
            return (fSkewTable == null) ? null : fSkewTable.clone();
        }
    }

    /**
     * Конструктор.
     */
    private KdiImageReader() {
        //
    }

    /**
     * Проверяет, что файл — образ в формате, который этот класс умеет разбирать: контейнер
     * с 256-байтным заголовком или запись B.01.
     *
     * <p>Признак контейнера — контрольная сумма: сумма байтов 1..31 плюс {@code 0x66} должна
     * лечь в байт 32 (байт 0 в неё не входит). Совпадение суммы ещё не гарантирует, что
     * перед нами KDI: та же сумма может случайно совпасть в начале чужого файла, поэтому
     * дополнительно проверяются длина файла и блок параметров диска — маска блока,
     * число секторов, размер каталога и число системных дорожек. Запись B.01 узнаётся по
     * слову {@code 0x0249} на своём месте.
     *
     * @param file файл образа
     * @return true — файл читается как KDI
     * @throws IOException если файл не читается
     */
    public static boolean isKdi(final File file) throws IOException {
        return isKdi(Files.readAllBytes(file.toPath()), file.getName());
    }

    /**
     * Проверяет те же признаки по уже загруженным байтам.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщения об ошибке
     * @return true — байты читаются как KDI
     */
    public static boolean isKdi(final byte[] image, final String name) {
        try {
            readHeader(image, name);
            return true;
        } catch (final IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Проверяет, что файл — запись B.01 без заголовка с параметрами диска.
     *
     * <p>Узнать её можно по началу файла: он открывается строкой BIOS «{@code (C) cerikopik}»,
     * и первые два слова ({@code 0x4328 0x2029} — это «{@code (C}» и «{@code ) }») в заголовке
     * контейнера не встречаются: там лежат адреса загрузки и запуска, у настоящих контейнеров
     * {@code 0x0100 0x0100}. Контрольная сумма контейнера у записи тоже не сходится, но
     * полагаться только на неё нельзя — случайная сумма в чужом файле тоже бывает, поэтому
     * проверяются оба слова и сумма сразу.
     *
     * @param file файл образа
     * @return true — файл записан как B.01
     * @throws IOException если файл не читается
     */
    public static boolean isB01(final File file) throws IOException {
        return isB01(Files.readAllBytes(file.toPath()));
    }

    /**
     * Проверяет признак записи B.01 по уже загруженным байтам.
     *
     * @param image байты файла образа
     * @return true — байты записаны как B.01
     */
    public static boolean isB01(final byte[] image) {
        if ((image.length <= B01_HEADER_SIZE)
                || (word(image, 0) != B01_SIGNATURE)
                || (word(image, 2) != B01_MARKER)) {
            return false;
        }
        int sum = CHECKSUM_BIAS;
        for (int i = 1; i < CHECKSUM_OFFSET; i++) {
            sum += image[i] & 0xFF;
        }
        return (sum & 0xFF) != (image[CHECKSUM_OFFSET] & 0xFF);
    }

    /**
     * Читает заголовок KDI и строит по нему раскладку.
     *
     * @param file файл образа
     * @return параметры контейнера и раскладка для {@link CpmImageReader}
     * @throws IOException если файл не KDI, его заголовок противоречив или длина не совпала
     *                     с геометрией
     */
    public static KdiHeader readHeader(final File file) throws IOException {
        return readHeader(Files.readAllBytes(file.toPath()), file.getName());
    }

    /**
     * Читает заголовок KDI из уже загруженных байтов.
     *
     * <p>Отдельный вход для {@link DiskProfileFactory}: там файл читается один раз, чтобы
     * проверить и контейнеры, и линейные образы, а не перечитывать его на каждую попытку.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщений об ошибке
     * @return параметры контейнера и раскладка для {@link CpmImageReader}
     * @throws IOException если байты не KDI, заголовок противоречив или длина не совпала с геометрией
     */
    public static KdiHeader readHeader(final byte[] image, final String name) throws IOException {
        if (image.length <= HEADER_SIZE) {
            throw new IOException("Файл короче заголовка KDI: " + name);
        }

        int sum = CHECKSUM_BIAS;
        for (int i = 1; i < CHECKSUM_OFFSET; i++) {
            sum += image[i] & 0xFF;
        }
        if ((sum & 0xFF) != (image[CHECKSUM_OFFSET] & 0xFF)) {
            if (isB01(image)) {
                throw new IOException(String.format(
                        "KDI: файл записан без заголовка, как в утилите B.01; его раскладку "
                        + "берут из профиля образа, а не из заголовка (%s)", name));
            }
            throw new IOException(String.format(
                    "Не KDI: контрольная сумма заголовка %02X вместо рассчитанной %02X (%s)",
                    image[CHECKSUM_OFFSET] & 0xFF, sum & 0xFF, name));
        }

        final int loadAddress     = word(image, 0);
        final int runAddress      = word(image, 2);
        final int imageRecords    = word(image, 4);
        final int diskSize        = image[6] & 0xFF;
        final int density         = image[7] & 0xFF;
        final int tracksPerInch   = image[8] & 0xFF;
        final int skewFactor      = image[9] & 0xFF;
        final int sectorsOnTrack  = word(image, 10);
        final int tracks          = word(image, 12);
        final int sidesCode       = image[14] & 0xFF;

        final int spt   = word(image, DPB_OFFSET);
        final int bsh   = image[DPB_OFFSET + 2] & 0xFF;
        final int blm   = image[DPB_OFFSET + 3] & 0xFF;
        final int exm   = image[DPB_OFFSET + 4] & 0xFF;
        final int dsm   = word(image, DPB_OFFSET + 5);
        final int drm   = word(image, DPB_OFFSET + 7);
        final int ofs   = word(image, DPB_OFFSET + 13);

        if ((sidesCode != 0) && (sidesCode != 1)) {
            throw new IOException(String.format(
                    "KDI: число сторон в заголовке должно быть 0 или 1, записано %d (%s)",
                    sidesCode, name));
        }
        if (sectorsOnTrack <= 0) {
            throw new IOException("KDI: в заголовке ноль секторов на дорожке (" + name + ")");
        }
        if ((skewFactor > HEADER_SIZE - SKEW_TABLE_OFFSET) || (skewFactor > sectorsOnTrack)) {
            throw new IOException(String.format(
                    "KDI: коэффициент перемежения %d не помещается в заголовок или больше числа секторов %d (%s)",
                    skewFactor, sectorsOnTrack, name));
        }

        final int blockMaskOffset = blockMaskOffset(blm);
        final int blockSize       = BLOCK_SIZES[blockMaskOffset];
        final byte sectorCode     = sectorCodeOf(image.length - HEADER_SIZE, tracks, sidesCode + 1,
                sectorsOnTrack, name);
        final int  sectorSize     = sectorSize(sectorCode);

        if ((spt <= 0) || (spt != sectorsOnTrack * (sectorSize / RECORD_SIZE))) {
            throw new IOException(String.format(
                    "KDI: SPT в блоке параметров диска (%d) не совпадает с геометрией (%d секторов по %d байт) (%s)",
                    spt, sectorsOnTrack, sectorSize, name));
        }
        if (drm + 1 > MAX_DIR_ENTRIES) {
            throw new IOException(String.format(
                    "KDI: записей каталога %d больше предела CP/M %d (%s)",
                    drm + 1, MAX_DIR_ENTRIES, name));
        }
        if ((ofs >= tracks) && (ofs != 0)) {
            throw new IOException(String.format(
                    "KDI: системных дорожек %d при %d дорожках на диске (%s)", ofs, tracks, name));
        }

        final int[] skewTable = readSkewTable(image, skewFactor);
        final String profileName = describe(name, diskSize, density, tracksPerInch, sidesCode + 1,
                tracks, sectorsOnTrack, sectorSize, blockSize, drm + 1, ofs);
        final DiskProfile profile = new DiskProfile(
                profileName, tracks, sidesCode + 1, sectorsOnTrack, sectorSize, ofs, blockSize,
                drm + 1, skewTable);

        final long expectedBody = (long) tracks * (sidesCode + 1) * sectorsOnTrack * sectorSize;
        if (expectedBody != (long) imageRecords * HEADER_SIZE) {
            throw new IOException(String.format(
                    "KDI: счётчик блоков %d не совпадает с геометрией %d байт (%s)",
                    imageRecords, expectedBody, name));
        }
        if (expectedBody > image.length - (long) HEADER_SIZE) {
            throw new IOException(String.format(
                    "KDI: образ обрезан — по заголовку нужно %d байт тела, в файле %d (%s)",
                    expectedBody, image.length - HEADER_SIZE, name));
        }

        return new KdiHeader(profile, loadAddress, runAddress, imageRecords, diskSize, density,
                tracksPerInch, skewFactor, sectorCode, sectorsOnTrack, drm + 1, skewTable);
    }

    /**
     * Отрезает служебную часть файла и возвращает линейный образ.
     *
     * <p>Чтение — это и есть отрезание: и тело контейнера, и образ за 32 байтами записи B.01
     * устроены как обычный линейный образ, который {@link CpmImageReader} уже умеет разбирать
     * по переданной раскладке.
     *
     * @param file файл образа
     * @return байты линейного образа (без заголовка контейнера или записи B.01)
     * @throws IOException если файл не читается или не является KDI
     */
    public static byte[] toLinearImage(final File file) throws IOException {
        final byte[] raw = Files.readAllBytes(file.toPath());
        return toLinearImage(raw, file.getName());
    }

    /**
     * Отрезает служебную часть от уже загруженных байтов.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщения об ошибке
     * @return байты линейного образа (без заголовка контейнера или записи B.01)
     * @throws IOException если байты не являются KDI
     */
    public static byte[] toLinearImage(final byte[] image, final String name) throws IOException {
        if (isB01(image)) {
            return Arrays.copyOfRange(image, B01_HEADER_SIZE, image.length);
        }
        readHeader(image, name);
        return Arrays.copyOfRange(image, HEADER_SIZE, image.length);
    }

    /**
     * Читает файлы из контейнера KDI.
     *
     * <p>Читать контейнер напрямую имеет смысл только там, где нужен заголовок: в конвертере
     * этим занимается {@link DiskProfileFactory#readAllFiles(File)}, чтобы формат определялся
     * в одном месте. Запись B.01 этот метод не разбирает — у неё нет заголовка с параметрами
     * диска, и раскладку для неё даёт {@link DiskProfileFactory}.
     *
     * @param file файл контейнера
     * @return файлы из каталога тела образа в порядке появления их первых записей
     * @throws IOException если файл не читается, не является KDI или его каталог не разбирается
     */
    public static List<CpmImageReader.CpmFile> readAllFiles(final File file) throws IOException {
        final KdiHeader header = readHeader(file);
        return CpmImageReader.readAllFiles(toLinearImage(file), header.getProfile());
    }

    /**
     * Читает слово Z80 (младший байт первым).
     *
     * @param data байты
     * @param off  смещение
     * @return значение от 0 до 65535
     */
    private static int word(final byte[] data, final int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
    }

    /**
     * По маске блока {@code BLM} находит размер блока CP/M.
     *
     * <p>{@code BLM} — число записей в блоке минус один и одновременно маска «младшие биты
     * номера записи внутри блока». Записей в блоке равно {@code BLM + 1}, то есть степень
     * двойки; из неё и берётся размер блока. Маска не вида {@code 2^n - 1} означает, что
     * блок параметров искажён.
     *
     * @param blm поле {@code BLM} из заголовка
     * @return логарифм размера блока по основанию 2, уложенный в {@link #BLOCK_SIZES}
     * @throws IOException если маска не описывает блок CP/M 2.2
     */
    private static int blockMaskOffset(final int blm) throws IOException {
        if ((blm & (blm + 1)) != 0) {
            throw new IOException(String.format("KDI: маска блока BLM=%02X не является маской %d-1", blm, blm + 1));
        }
        // Записей в блоке = BLM + 1; при 1024 байтах это 8 записей, BLM = 7.
        int offset = 0;
        for (int mask = 7; mask <= 0x7F; mask = (mask << 1) | 1) {
            if (mask == blm) {
                return offset;
            }
            offset++;
        }
        throw new IOException(String.format("KDI: маска блока BLM=%02X не соответствует блок CP/M 2.2", blm));
    }

    /**
     * Определяет размер сектора по коду из заголовка, сверяя его с длиной тела файла.
     *
     * <p>Код размера сектора ({@code SecSize}: 0 — 128, 1 — 256, 2 — 512, 3 — 1024 байта)
     * в заголовке KDI не лежит отдельным полем: он восстанавливается из длины тела, которая
     * кратна произведению дорожек, сторон и секторов на размер сектора.
     *
     * @param bodyLength     длина тела файла
     * @param tracks         число дорожек
     * @param sides          число сторон
     * @param sectorsOnTrack число секторов на дорожке
     * @param name           имя файла для сообщения об ошибке
     * @return код размера сектора
     * @throws IOException если тело не делится на геометрию или размер вне таблицы
     */
    private static byte sectorCodeOf(final long   bodyLength,
                                     final int    tracks,
                                     final int    sides,
                                     final int    sectorsOnTrack,
                                     final String name) throws IOException {

        final long sectors     = (long) tracks * sides * sectorsOnTrack;
        final long sectorBytes = (sectors == 0) ? 0 : bodyLength / sectors;
        if ((sectors == 0) || (bodyLength % sectors != 0) || (sectorBytes == 0)) {
            throw new IOException(String.format(
                    "KDI: тело %d байт не делится на %d дорожек x %d сторон x %d секторов (%s)",
                    bodyLength, tracks, sides, sectorsOnTrack, name));
        }
        for (byte code = 0; code <= 3; code++) {
            if (sectorSize(code) == sectorBytes) {
                return code;
            }
        }
        throw new IOException(String.format(
                "KDI: размер сектора %d байт вне ряда 128/256/512/1024 (%s)", sectorBytes, name));
    }

    /**
     * @param code код размера сектора (0 — 128, 1 — 256, 2 — 512, 3 — 1024)
     * @return размер сектора в байтах
     */
    private static int sectorSize(final int code) {
        return 128 << code;
    }

    /**
     * Читает таблицу перемежения из заголовка и переводит её к значениям от 1.
     *
     * @param image      байты файла
     * @param skewFactor коэффициент перемежения (0 или 1 — таблицы нет)
     * @return таблица для {@link DiskProfile} или null
     */
    private static int[] readSkewTable(final byte[] image, final int skewFactor) {
        if (skewFactor <= 1) {
            return null;
        }
        final int[] table = new int[skewFactor];
        for (int i = 0; i < skewFactor; i++) {
            // В KDI первый физический сектор дорожки имеет номер 0, в DiskProfile — 1.
            table[i] = (image[SKEW_TABLE_OFFSET + i] & 0xFF) + 1;
        }
        return table;
    }

    /**
     * Собирает имя профиля из параметров заголовка: по нему пользователь видит, откуда взят образ.
     *
     * @param fileName        имя файла
     * @param diskSize        код диаметра
     * @param density         код плотности
     * @param tracksPerInch   дорожек на дюйм
     * @param sides           число сторон
     * @param tracks          число дорожек
     * @param sectorsOnTrack  секторов на дорожке
     * @param sectorSize      размер сектора
     * @param blockSize       размер блока
     * @param dirEntries      записей каталога
     * @param reservedTracks  системных дорожек
     * @return имя профиля
     */
    private static String describe(final String fileName,
                                   final int    diskSize,
                                   final int    density ,
                                   final int    tracksPerInch,
                                   final int    sides ,
                                   final int    tracks,
                                   final int    sectorsOnTrack,
                                   final int    sectorSize,
                                   final int    blockSize ,
                                   final int    dirEntries,
                                   final int    reservedTracks) {

        final String diameter = (diskSize == 0) ? "8\"" : "5.25\"";
        final String densityName = (density == 0) ? "FM" : "MFM";
        final String geometry = String.format("%dx%dx%dx%d", tracks, sides, sectorsOnTrack, sectorSize);
        final long   kiloBytes = ((long) tracks * sides * sectorsOnTrack * sectorSize) / 1024;
        return String.format("KDI %s %s %d TPI %s (%d KB, block %d, %d dir, %d sys) — %s",
                diameter, densityName, tracksPerInch, geometry, kiloBytes,
                blockSize, dirEntries, reservedTracks, fileName);
    }
}
