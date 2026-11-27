package ru.avsh.specialist.mx.cpm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Читает файлы из образа диска CP/M.
 *
 * <p>Каталог читается блоками по раскладке {@link DiskProfile} через {@link BlockLayout}:
 * блоки отсчитываются от начала области данных, а не от начала файла, и при наличии
 * таблицы перемежения записи внутри дорожки переставляются. Запись каталога описывает
 * один экстент файла (до 16 КБ), а файл может занимать несколько записей — они собираются
 * по имени и коду пользователя и склеиваются в порядке номера экстента.
 *
 * <p>Запись с байтом {@code 0xE5} — свободная (в том числе освобождённая удалением), и
 * поиск файлов после неё продолжается: на настоящих дисках свободные записи стоят посреди
 * каталога, а живые — после них. Читатель различает два признака конца каталога. Код
 * пользователя больше 15 означает, что в области каталога уже лежат чужие байты (так
 * выглядит каталог Osborne 1, где после живых записей идёт код, а не пометки {@code 0xE5});
 * на такую запись разбор останавливается. Заполнение {@code 0xE5} до последней записи
 * профиля — обычный хвост каталога, он пропускается до конца области.
 *
 * <p>Длина файла берётся по числу занятых блоков, а не по сумме {@code rc}: {@code rc}
 * не описывает хвост последнего блока, и файл, дополненный байтами {@code 0x1A} до полного
 * блока, иначе обрезался бы по последней полной записи.
 *
 * <p>Известное ограничение: на профилях с блоком 4096 маска экстента {@code EXM}
 * вычисляется как {@code maxRecordsPerEntry / 128 - 1} = 1, тогда как писатель
 * ({@link CpmImageWriter}) эту маску в номер экстента не закладывает. Такие образы
 * читались бы неверно — поэтому раскладки с блоком 4096 в {@link DiskProfileFactory}
 * нет, а профиль, пришедший из заголовка KDI или «Ориона», остаётся на совести
 * того, кто его объявил.
 *
 * @author -=AVSh=-
 */
public final class CpmImageReader {
    /** Предел одной записи каталога CP/M: 16 КБ, то есть 128 записей по 128 байт. */
    private static final int MAX_RECORDS_IN_ENTRY = 128;
    /** Символы, недопустимые в имени файла на ПК: в имени CP/M они законны. */
    private static final String INVALID_HOST_CHARS = "[\\\\/:*?\"<>|]";
    /** Байт «свободно» в каталоге CP/M. */
    private static final int FREE_BYTE = 0xE5;
    /** Код пользователя CP/M — от 0 до 15; всё выше — уже не каталог, а данные. */
    private static final int MAX_USER_NUMBER = 15;
    /** Размер записи каталога в байтах. */
    private static final int DIR_ENTRY_SIZE = 32;
    /** Размер логической записи CP/M. */
    private static final int RECORD_SIZE = 128;
    /** Бит «только чтение» в записи каталога — младший бит поля типа {@code T1}. */
    private static final int ATTR_READ_ONLY = 0x80;
    /** Бит «системный» в записи каталога — младший бит поля типа {@code T2}. */
    private static final int ATTR_SYSTEM = 0x80;

    /**
     * Файл, извлечённый из образа CP/M.
     *
     * @author -=AVSh=-
     */
    public static final class CpmFile {
        private final String  fName      ;
        private final int     fUserNumber;
        private final boolean fReadOnly  ;
        private final boolean fSystem    ;
        private final byte[]  fData      ;

        /**
         * Конструктор.
         *
         * @param name       имя файла в форме 8.3
         * @param userNumber код пользователя CP/M
         * @param readOnly   true — файл помечен «только чтение»
         * @param system     true — файл помечен «системный»
         * @param data       содержимое файла
         */
        public CpmFile(final String  name      ,
                       final int     userNumber,
                       final boolean readOnly  ,
                       final boolean system    ,
                       final byte[]  data) {

            this.fName       = name      ;
            this.fUserNumber = userNumber;
            this.fReadOnly   = readOnly  ;
            this.fSystem     = system    ;
            this.fData       = data      ;
        }

        /**
         * @return имя файла в форме 8.3
         */
        public String getName() {
            return fName;
        }

        /**
         * @return код пользователя CP/M
         */
        public int getUserNumber() {
            return fUserNumber;
        }

        /**
         * @return true — файл помечен «только чтение»
         */
        public boolean isReadOnly() {
            return fReadOnly;
        }

        /**
         * @return true — файл помечен «системный»
         */
        public boolean isSystem() {
            return fSystem;
        }

        /**
         * @return содержимое файла
         */
        public byte[] getData() {
            return fData;
        }
    }

    /**
     * Запись каталога — один экстент файла.
     */
    private static final class Extent {
        private final int           fUser;
        private final String        fFullName;
        private final boolean       fReadOnly;
        private final boolean       fSystem  ;
        private final int           fExtentNum  ;
        private final int           fRecordCount;
        private final List<Integer> fBlocks     ;

        /**
         * Конструктор.
         *
         * @param user        код пользователя CP/M
         * @param fullName    имя файла в форме 8.3
         * @param readOnly    true — файл помечен «только чтение»
         * @param system      true — файл помечен «системный»
         * @param extentNum   номер экстента
         * @param recordCount число записей по 128 байт, описанных этой записью каталога
         * @param blocks      номера блоков в порядке указателей
         */
        Extent(final int           user,
               final String        fullName ,
               final boolean       readOnly ,
               final boolean       system   ,
               final int           extentNum,
               final int           recordCount,
               final List<Integer> blocks) {

            this.fUser        = user       ;
            this.fFullName    = fullName   ;
            this.fReadOnly    = readOnly   ;
            this.fSystem      = system     ;
            this.fExtentNum   = extentNum  ;
            this.fRecordCount = recordCount;
            this.fBlocks      = blocks     ;
        }

        /**
         * @return код пользователя CP/M
         */
        int getUser() {
            return fUser;
        }

        /**
         * @return имя файла в форме 8.3
         */
        String getFullName() {
            return fFullName;
        }
    }

    private final byte[]      fImage  ;
    private final DiskProfile fProfile;
    private final BlockLayout fLayout ;

    /**
     * Конструктор.
     *
     * @param imageFile файл образа диска
     * @param profile   раскладка образа, обычно полученная из {@link DiskProfileFactory}
     * @throws IOException если файл образа не читается
     */
    public CpmImageReader(final File imageFile, final DiskProfile profile) throws IOException {
        this.fImage   = Files.readAllBytes(imageFile.toPath());
        this.fProfile = profile;
        this.fLayout  = new BlockLayout(profile);
    }

    /**
     * Конструктор для образа, уже загруженного в память.
     *
     * @param image   байты линейного образа диска
     * @param profile раскладка образа
     */
    private CpmImageReader(final byte[] image, final DiskProfile profile) {
        this.fImage   = image  ;
        this.fProfile = profile;
        this.fLayout  = new BlockLayout(profile);
    }

    /**
     * Читает файлы из образа, который уже лежит в памяти.
     *
     * <p>Нужен контейнерам: KDI хранит линейный образ за 256-байтным заголовком
     * (см. {@link KdiImageReader}), и отрезанное тело незачем писать на диск, чтобы прочитать.
     *
     * @param image   байты линейного образа диска
     * @param profile раскладка образа, для KDI — построенная по его заголовку
     * @return список файлов в порядке появления их первых записей в каталоге
     */
    public static List<CpmFile> readAllFiles(final byte[] image, final DiskProfile profile) {
        return new CpmImageReader(image, profile).readAllFiles();
    }

    /**
     * Читает все файлы из каталога образа.
     *
     * @return список файлов в порядке появления их первых записей в каталоге
     */
    public List<CpmFile> readAllFiles() {
        final byte[] dirArea = readDirArea();
        final Map<String, List<Extent>> groupedExtents = new LinkedHashMap<>();

        for (int i = 0; i < fProfile.getDirEntries(); i++) {
            final int offset = i * DIR_ENTRY_SIZE;
            final int status = dirArea[offset] & 0xFF;

            // 0xE5 — свободная (или освобождённая) запись каталога, поиск файлов идёт дальше:
            // на реальных дисках записи, освобождённые удалением, стоят посреди области
            // каталога, а живые — после них. Конец каталога по 0xE5 не опознать: на дисках
            // «Специалиста MX» (bst_cpm_v1.3.odi, CPM_TurboPascalZ80.odi) весь хвост области
            // до последней записи профиля заполнен 0xE5, и файлы от этого не теряются.
            if (status == FREE_BYTE) {
                continue;
            }

            // Код пользователя — от 0 до 15. Всё остальное означает, что в области каталога
            // уже лежат не записи каталога, а чужие байты: так выглядит Osborne 1
            // (os1sysd.img), где после живых записей идёт код — байты со значениями вроде
            // 0xC3, 0x77, 0x21. Разбирать их как записи каталога нельзя: получились бы
            // файлы с именем из мусора. Поэтому разбор останавливается на первой такой записи.
            if (status > MAX_USER_NUMBER) {
                break;
            }

            final Extent ext = parseExtent(dirArea, offset, status);
            if (ext.fRecordCount == 0) {
                continue;
            }

            final String key = status + ":" + ext.fFullName;
            final List<Extent> list = groupedExtents.computeIfAbsent(key, k -> new ArrayList<>());
            list.add(ext);
        }

        final List<CpmFile> result = new ArrayList<>();
        for (final List<Extent> extents : groupedExtents.values()) {
            extents.sort(Comparator.comparingInt(a -> a.fExtentNum));

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (final Extent ext : extents) {
                final int ptrsPerEntry       = fProfile.is16BitBlockPointers() ? 8 : 16;
                final int maxRecordsPerEntry = ptrsPerEntry * (fProfile.getBlockSize() / RECORD_SIZE);
                final int exm                = Math.max(0, (maxRecordsPerEntry / MAX_RECORDS_IN_ENTRY) - 1);
                final int actualRecords      = ((ext.fExtentNum & exm) * MAX_RECORDS_IN_ENTRY) + ext.fRecordCount;

                int bytesRemaining = Math.min(actualRecords, maxRecordsPerEntry) * RECORD_SIZE;
                for (final int blockNum : ext.fBlocks) {
                    if ((blockNum == 0) || (bytesRemaining <= 0)) {
                        continue;
                    }
                    final byte[] blockData = readBlock(blockNum);
                    final int    toWrite   = Math.min(bytesRemaining, fProfile.getBlockSize());
                    out.write(blockData, 0, toWrite);
                    bytesRemaining -= toWrite;
                }
            }

            final Extent first = extents.get(0);
            result.add(new CpmFile(first.fFullName, first.fUser, first.fReadOnly, first.fSystem, out.toByteArray()));
        }
        return result;
    }

    /**
     * Читает область каталога так, как её читает {@link DiskProfileFactory}: блоки каталога
     * идут подряд от начала области данных.
     *
     * @return записи каталога подряд; если образ кончается раньше, хвост остаётся пометками
     *         {@code 0xE5}
     */
    private byte[] readDirArea() {
        final byte[] dirArea = new byte[fProfile.getDirEntries() * DIR_ENTRY_SIZE];
        Arrays.fill( dirArea, (byte) FREE_BYTE);

        final int dirBlocks = (dirArea.length + fProfile.getBlockSize() - 1) / fProfile.getBlockSize();
        int copied = 0;
        for (int b = 0; (b < dirBlocks) && (copied < dirArea.length); b++) {
            final byte[] block = readBlock(b);
            final int len = Math.min(block.length, dirArea.length - copied);
            System.arraycopy(block, 0, dirArea, copied, len);
            copied += len;
        }
        return dirArea;
    }

    /**
     * Разбирает одну запись каталога в описание экстента.
     *
     * @param dir  байты области каталога
     * @param off  смещение записи
     * @param user код пользователя из первого байта записи
     * @return описание экстента
     */
    private Extent parseExtent(final byte[] dir, final int off, final int user) {
        final String  fullName = getFullName(dir, off);
        final boolean readOnly = (dir[off +  9] & ATTR_READ_ONLY) != 0;
        final boolean system   = (dir[off + 10] & ATTR_SYSTEM   ) != 0;

        final int ex          = dir[off + 12] & 0x1F;
        final int s2          = dir[off + 14] & 0x3F;
        final int extentNum   = (s2 << 5) | ex;
        final int recordCount = dir[off + 15] & 0xFF;

        final List<Integer> blocks = new ArrayList<>();
        if (fProfile.is16BitBlockPointers()) {
            for (int p = 0; p < 8; p++) {
                final int lo = dir[off + 16 + p * 2    ] & 0xFF;
                final int hi = dir[off + 16 + p * 2 + 1] & 0xFF;
                blocks.add((hi << 8) | lo);
            }
        } else {
            for (int p = 0; p < 16; p++) {
                blocks.add(dir[off + 16 + p] & 0xFF);
            }
        }
        return new Extent(user, fullName, readOnly, system, extentNum, recordCount, blocks);
    }

    /**
     * Собирает имя файла из записи каталога, заменяя недопустимые на ПК символы подчёркиванием.
     *
     * @param dir байты области каталога
     * @param off смещение записи
     * @return имя в форме {@code ИМЯ.РАСШ}
     */
    private String getFullName(final byte[] dir, final int off) {
        String name = extractAscii(dir, off + 1, 8).trim();
        String ext  = extractAscii(dir, off + 9, 3).trim();
        // Спецсимволы CP/M (например, '|' — «неразрывная» часть имени, '$', '#') в именах
        // CP/M обычны, но в имени файла на ПК недопустимы. Заменяем их подчёркиванием: без
        // этого имя, извлечённое из образа, нельзя ни записать обратно, ни сохранить на ПК.
        name = name.replaceAll(INVALID_HOST_CHARS, "_");
        ext  = ext.replaceAll(INVALID_HOST_CHARS, "_");
        return ext.isEmpty() ? name : name + "." + ext;
    }

    /**
     * Извлекает ASCII-строку из буфера, сбрасывая старший бит атрибутов.
     *
     * @param buf    байты области каталога
     * @param offset смещение строки
     * @param len    длина строки
     * @return строка
     */
    private String extractAscii(final byte[] buf, final int offset, final int len) {
        final byte[] clean = new byte[len];
        for (int i = 0; i < len; i++) {
            clean[i] = (byte) (buf[offset + i] & 0x7F);
        }
        return new String(clean, StandardCharsets.US_ASCII);
    }

    /**
     * Читает блок данных по его номеру из каталога.
     *
     * @param blockIndex номер блока
     * @return содержимое блока
     */
    private byte[] readBlock(final int blockIndex) {
        final int    blockSize       = fProfile.getBlockSize();
        final int    recordsPerBlock = blockSize / RECORD_SIZE;
        final byte[] result          = new byte[blockSize];

        for (int r = 0; r < recordsPerBlock; r++) {
            final int offset = fLayout.dataOffset(blockIndex, r);
            if ((offset >= 0) && ((offset + RECORD_SIZE) <= fImage.length)) {
                System.arraycopy(fImage, offset, result, r * RECORD_SIZE, RECORD_SIZE);
            }
        }
        return result;
    }
}
