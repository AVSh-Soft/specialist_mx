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
 * Каталог читается блоками по раскладке {@link DiskProfile}: запись каталога описывает один
 * экстент файла (до 16 КБ), а файл может занимать несколько записей — они собираются по имени
 * и коду пользователя и склеиваются в порядке номера экстента.
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

    /** Бит «только чтение» в записи каталога. */
    private static final int ATTR_READ_ONLY = 0x80;

    /** Бит «системный» в записи каталога. */
    private static final int ATTR_SYSTEM = 0x80;

    /**
     * Файл, извлечённый из образа CP/M.
     *
     * @author -=AVSh=-
     */
    public static final class CpmFile {
        private final String  fName;
        private final int     fUserNumber;
        private final boolean fReadOnly;
        private final boolean fSystem;
        private final byte[]  fData;

        /**
         * Конструктор.
         *
         * @param name       имя файла в форме 8.3
         * @param userNumber код пользователя CP/M
         * @param readOnly   true — файл помечен «только чтение»
         * @param system     true — файл помечен «системный»
         * @param data       содержимое файла
         */
        public CpmFile(final String  name,
                       final int     userNumber,
                       final boolean readOnly,
                       final boolean system,
                       final byte[]  data) {
            this.fName       = name;
            this.fUserNumber = userNumber;
            this.fReadOnly   = readOnly;
            this.fSystem     = system;
            this.fData       = data;
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
        private final boolean       fSystem;
        private final int           fExtentNum;
        private final int           fRecordCount;
        private final List<Integer> fBlocks;

        Extent(final int           user,
               final String        fullName,
               final boolean       readOnly,
               final boolean       system,
               final int           extentNum,
               final int           recordCount,
               final List<Integer> blocks) {
            this.fUser        = user;
            this.fFullName    = fullName;
            this.fReadOnly    = readOnly;
            this.fSystem      = system;
            this.fExtentNum   = extentNum;
            this.fRecordCount = recordCount;
            this.fBlocks      = blocks;
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

    private final byte[]      fImage;
    private final DiskProfile fProfile;
    private final BlockLayout fLayout;

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
            // на реальных дисках (например, системный диск Osborne 1) освобождённые записи
            // стоят посреди области каталога, а живые — после них. У «Специалиста MX» каталог
            // размечается короче, чем позволяет профиль (у bst_cpm_v1.3.odi занято шесть
            // записей из 256), и за концом каталога идут данные файла: они не 0xE5 и код
            // пользователя у них не 0–15, поэтому конец каталога опознаётся по ним.
            if (status == FREE_BYTE) {
                continue;
            }

            // Код пользователя — от 0 до 15. Всё остальное означает, что дальше в области
            // каталога уже лежат данные файла: у образа «Специалиста MX» (bst_cpm_v1.3.odi)
            // каталог размечается короче, чем позволяет профиль, и за концом каталога идёт
            // загрузчик. Конец каталога надёжнее всего опознаётся по началу этих данных.
            if (status > MAX_USER_NUMBER) {
                break;
            }

            final Extent ext = parseExtent(dirArea, offset, status);
            if (ext.fRecordCount == 0) {
                continue;
            }

            final String        key  = status + ":" + ext.fFullName;
            List<Extent>        list = groupedExtents.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groupedExtents.put(key, list);
            }
            list.add(ext);
        }

        final List<CpmFile> result = new ArrayList<>();
        for (final List<Extent> extents : groupedExtents.values()) {
            Collections.sort(extents, new Comparator<Extent>() {
                @Override
                public int compare(final Extent a, final Extent b) {
                    return Integer.compare(a.fExtentNum, b.fExtentNum);
                }
            });

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (final Extent ext : extents) {
                // Одна запись каталога — один экстент: столько записей по 128 байт, сколько
                // блоков перечислено в её указателях. У «Специалиста MX» (блок 1024) это
                // 8 блоков × 8 записей = 64 записи, а RC=128 в первом экстенте означает, что
                // файл продолжается в следующей записи каталога с EX=1 (так размечен
                // TURBO.COM на рабочем диске). Ограничение по RC не даёт прочитать в файл
                // блоки следующего экстента, если RC меньше объявленных блоков.
                final int recordsPerEntry = ext.fBlocks.size() * fProfile.getBlockSize() / RECORD_SIZE;
                int       bytesRemaining  = Math.min(ext.fRecordCount, recordsPerEntry) * RECORD_SIZE;
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
     * Читает область каталога из образа.
     *
     * Каталог CP/M плоский — папок в файловой системе нет: записи каталога лежат подряд,
     * с шагом 32 байта, начиная сразу за системными дорожками, и любая из них может описывать
     * любой файл на диске. Каталог занимает первые блоки диска как обычный файл, но его записи
     * не выровнены по блокам. Проверено на рабочих образах: у {@code tpas.odi} каталог это
     * 40 записей подряд (0x5000…0x52FF), хотя блок диска — 1024 байта. Чтение «по четыре
     * записи в блоке» рвёт каталог на 8 записей на блок и прячет всё, что дальше, — файлы
     * становятся короче настоящих, а записи второго экстента теряются.
     *
     * @return байты области каталога в объёме {@link DiskProfile#getDirEntries()} записей
     */
    private byte[] readDirArea() {
        final byte[] dirArea = new byte[fProfile.getDirEntries() * DIR_ENTRY_SIZE];
        final int    base    = fLayout.dirEntryOffset(0, DIR_ENTRY_SIZE);
        final int    length  = Math.min(dirArea.length, Math.max(0, fImage.length - base));
        if (length > 0) {
            System.arraycopy(fImage, base, dirArea, 0, length);
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
        String name = extractAscii(dir, off + 1, 8).trim();
        String ext  = extractAscii(dir, off + 9, 3).trim();

        // Спецсимволы CP/M (например, '|' — «неразрывная» часть имени, '$', '#') в именах
        // CP/M обычны, но в имени файла на ПК недопустимы. Заменяем их подчёркиванием: без
        // этого имя, извлечённое из образа, нельзя ни записать обратно, ни сохранить на ПК.
        name = name.replaceAll(INVALID_HOST_CHARS, "_");
        ext  = ext .replaceAll(INVALID_HOST_CHARS, "_");

        final String fullName = ext.isEmpty() ? name : name + "." + ext;

        final boolean readOnly = (dir[off + 9]  & ATTR_READ_ONLY) != 0;
        final boolean system   = (dir[off + 10] & ATTR_SYSTEM   ) != 0;

        final int ex        = dir[off + 12] & 0x1F;
        final int s2        = dir[off + 14] & 0x3F;
        final int extentNum = (s2 << 5) | ex;
        final int recordCount = dir[off + 15] & 0xFF;

        final List<Integer> blocks = new ArrayList<>();
        if (fProfile.is16BitBlockPointers()) {
            for (int p = 0; p < 8; p++) {
                final int lo = dir[off + 16 + p * 2] & 0xFF;
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
