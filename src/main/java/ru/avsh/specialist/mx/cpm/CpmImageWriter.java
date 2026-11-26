package ru.avsh.specialist.mx.cpm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Пишет файлы в образ диска CP/M.
 *
 * Образ можно взять уже существующий (дописать файлы в готовый диск) или создать заново —
 * чистым либо с системными дорожками из ресурсов проекта. Файлы раскладываются по свободным
 * блокам, на каждый экстент пишется своя запись каталога; имя приводится к форме 8.3, а при
 * конфликте имён подбирается суффикс {@code ~1}, {@code ~2}…
 *
 * @author -=AVSh=-
 */
public final class CpmImageWriter {

    /** Разрешаем только латиницу, цифры и безопасные спецсимволы CP/M. */
    private static final String INVALID_CPM_CHARS = "[^A-Z0-9_$#@!%&'()\\-~^+]";

    /** Байт «свободно» в каталоге и на месте незанятых данных. */
    private static final byte FREE_BYTE = (byte) 0xE5;

    /** Байт заполнения хвоста кластера — код конца файла в текстовых файлах CP/M. */
    private static final byte EOF_BYTE = (byte) 0x1A;

    /** Код пользователя CP/M — от 0 до 15. */
    private static final int MAX_USER_NUMBER = 0x0F;

    /** Размер записи каталога в байтах. */
    private static final int DIR_ENTRY_SIZE = 32;

    /** Размер логической записи CP/M. */
    private static final int RECORD_SIZE = 128;

    /** Биты «только чтение» (байт 9) и «системный» (байт 10) в записи каталога. */
    private static final int ATTR_READ_ONLY = 0x80;
    private static final int ATTR_SYSTEM    = 0x80;

    /** Предел номера экстента: он занимает пять бит в байте EX и шесть в байте S2. */
    private static final int EXTENT_MASK_EX = 0x1F;
    private static final int EXTENT_MASK_S2 = 0x3F;

    /** Сколько раз пробуем подобрать уникальное имя с суффиксом {@code ~N}. */
    private static final int MAX_UNIQUE_NAME_ATTEMPTS = 999;

    /** Наибольшая длина файла в CP/M 2.2: 512 логических экстентов по 16 КБ (8 МБ). */
    private static final int MAX_FILE_SIZE = 8 * 1024 * 1024;

    private final byte[]      fImage;
    private final DiskProfile fProfile;
    private final BlockLayout fLayout;

    /**
     * Открывает существующий (например, шаблонный загрузочный) образ для дозаписи файлов.
     *
     * @param existingImage файл существующего образа
     * @param profile       раскладка образа
     * @throws IOException если файл образа не читается
     */
    public CpmImageWriter(final File existingImage, final DiskProfile profile) throws IOException {
        this.fImage   = Files.readAllBytes(existingImage.toPath());
        this.fProfile = profile;
        this.fLayout  = new BlockLayout(profile);
    }

    /**
     * Создаёт пустой отформатированный образ, заполненный байтами 0xE5.
     *
     * @param profile раскладка образа
     */
    public CpmImageWriter(final DiskProfile profile) {
        this.fProfile = profile;
        this.fLayout  = new BlockLayout(profile);
        this.fImage   = new byte[profile.getImageSize()];
        Arrays.fill(fImage, FREE_BYTE);
    }

    /**
     * Создаёт пустой образ с загрузчиком, BDOS и BIOS из ресурсов проекта: системные дорожки
     * берутся из ресурса, каталог и место под файлы заполняются 0xE5.
     *
     * @param profile          формат образа; системная область должна совпадать по размеру с ресурсом
     * @param withSystemTracks true — образ загрузочный, false — только 0xE5 на всём диске
     * @throws IOException если системные дорожки запрошены, а ресурс недоступен
     */
    public CpmImageWriter(final DiskProfile profile, final boolean withSystemTracks) throws IOException {
        this.fProfile = profile;
        this.fLayout  = new BlockLayout(profile);
        if (withSystemTracks) {
            this.fImage = CpmSystemTracks.blankImage(profile);
        } else {
            this.fImage = new byte[profile.getImageSize()];
            Arrays.fill(fImage, FREE_BYTE);
        }
    }

    /**
     * Записывает список CP/M-файлов в образ с защитой от перезатирания внутри одного пакета.
     *
     * @param files     файлы для записи
     * @param overwrite true — заменять существующие файлы, false — подбирать новое имя
     * @throws IOException если на диске нет места или каталог заполнен
     */
    public void writeFiles(final List<CpmImageReader.CpmFile> files, final boolean overwrite) throws IOException {
        final Set<String> writtenInBatch = new HashSet<>();
        for (final CpmImageReader.CpmFile file : files) {
            writeFileInternal(file, overwrite, writtenInBatch);
        }
    }

    /**
     * Записывает один CP/M-файл в образ.
     *
     * @param file      файл для записи
     * @param overwrite true — заменять существующий файл, false — подбирать новое имя
     * @throws IOException если на диске нет места или каталог заполнен
     */
    public void writeFile(final CpmImageReader.CpmFile file, final boolean overwrite) throws IOException {
        writeFileInternal(file, overwrite, new HashSet<String>());
    }

    /**
     * Импортирует обычные файлы с диска ПК напрямую в образ.
     *
     * @param hostFiles  список файлов на ПК (папки пропускаются)
     * @param userNumber номер области пользователя CP/M (обычно 0)
     * @param overwrite  перезаписывать ли уже существующие в образе файлы
     * @throws IOException если на диске нет места или каталог заполнен
     */
    public void writeHostFiles(final List<File> hostFiles, final int userNumber, final boolean overwrite)
            throws IOException {
        final List<CpmImageReader.CpmFile> cpmFiles = new ArrayList<>();
        for (final File hostFile : hostFiles) {
            if ((hostFile == null) || !hostFile.isFile()) {
                continue;
            }
            final byte[] data = Files.readAllBytes(hostFile.toPath());
            cpmFiles.add(new CpmImageReader.CpmFile(
                    hostFile.getName(),
                    userNumber & MAX_USER_NUMBER,
                    !hostFile.canWrite(),
                    false,
                    data
            ));
        }
        writeFiles(cpmFiles, overwrite);
    }

    /**
     * Сохраняет итоговый образ на диск.
     *
     * @param targetFile файл, куда пишется образ
     * @throws IOException если файл не удаётся записать
     */
    public void saveTo(final File targetFile) throws IOException {
        Files.write(targetFile.toPath(), fImage);
    }

    /**
     * Раскладывает один файл по свободным блокам и переписывает каталог.
     *
     * @param file           файл для записи
     * @param overwrite      true — заменять существующий файл
     * @param writtenInBatch имена, уже записанные в текущем пакете
     * @throws IOException если на диске нет места или каталог заполнен
     */
    private void writeFileInternal(final CpmImageReader.CpmFile file,
                                   final boolean overwrite,
                                   final Set<String> writtenInBatch) throws IOException {
        // Считываем область каталога: записи лежат подряд с шагом 32 байта от начала каталога
        final byte[] dirArea = readDirArea();

        // Форматируем базовое имя в 8.3
        final String[] nameExt = format83Name(file.getName());
        String fName = nameExt[0];
        String fExt  = nameExt[1];

        final int user = file.getUserNumber() & MAX_USER_NUMBER;

        final String dirName = String.format("%-8s", fName).substring(0, 8);
        final String dirExt  = String.format("%-3s", fExt ).substring(0, 3);
        fName = dirName;
        fExt  = dirExt;

        final String batchKey = user + ":" + fName + "." + fExt;

        // Разрешаем конфликты имён:
        // Если файл уже лежит в образе И мы его ещё не записывали в этом пакете:
        //   - при overwrite == true освобождаем его экстенты и пишем новую версию на их место;
        //   - при overwrite == false подбираем имя с суффиксом ~1, ~2...
        // Если же такое 8.3-имя уже было записано в текущем пакете — всегда подбираем суффикс ~1, ~2...
        List<Integer> reuseSlots = new ArrayList<>();
        if (writtenInBatch.contains(batchKey)) {
            fName = resolveUniqueName(dirArea, user, fName, fExt);
        } else if (findFileInDir(dirArea, user, fName, fExt)) {
            if (overwrite) {
                reuseSlots = collectFileSlots(dirArea, user, fName, fExt);
            } else {
                fName = resolveUniqueName(dirArea, user, fName, fExt);
            }
        }

        writtenInBatch.add(user + ":" + fName + "." + fExt);

        final int     totalBlocks  = fProfile.totalBlocks();
        final boolean[] usedBlocks = new boolean[totalBlocks];
        for (int b = 0; b < dirBlocksCount(); b++) {
            usedBlocks[b] = true;
        }
        markUsedBlocksFromDir(dirArea, usedBlocks);

        final byte[] data = file.getData();
        if (data.length > MAX_FILE_SIZE) {
            // CP/M грузит .COM и открывает файл по адресу, а адресное пространство кончается
            // на 64 КБ: более длинный файл не читается ни одной программой, зато занимает
            // место на диске и молча получает обрезанное содержимое.
            throw new IOException(String.format(
                    "Файл слишком велик для CP/M (более %d байт): %s", MAX_FILE_SIZE, file.getName()));
        }
        final int    totalRecords = (data.length + RECORD_SIZE - 1) / RECORD_SIZE;
        final int    blocksNeeded = (data.length + fProfile.getBlockSize() - 1) / fProfile.getBlockSize();

        // Блоки перезаписываемого файла уже свободны (их экстенты освобождены): сначала берём их,
        // иначе при перезаписи блоками «с хвоста» каждый проход съедал бы новые блоки диска.
        final int[] allocatedBlocks = new int[blocksNeeded];
        int found = 0;
        for (final int slot : reuseSlots) {
            for (final int blk : readEntryBlocks(dirArea, slot * DIR_ENTRY_SIZE)) {
                if ((blk <= 0) || (found >= blocksNeeded)) {
                    continue;
                }
                allocatedBlocks[found++] = blk;
                usedBlocks[blk] = true;
            }
        }
        for (int b = dirBlocksCount(); (b < totalBlocks) && (found < blocksNeeded); b++) {
            if (!usedBlocks[b]) {
                allocatedBlocks[found++] = b;
                usedBlocks[b] = true;
            }
        }
        if (found < blocksNeeded) {
            throw new IOException("Недостаточно места на диске для файла: " + file.getName());
        }

        final byte[] blockBuf = new byte[fProfile.getBlockSize()];
        for (int i = 0; i < blocksNeeded; i++) {
            Arrays.fill(blockBuf, EOF_BYTE);
            final int srcOffset = i * fProfile.getBlockSize();
            final int len       = Math.min(fProfile.getBlockSize(), data.length - srcOffset);
            if (len > 0) {
                System.arraycopy(data, srcOffset, blockBuf, 0, len);
            }
            writeBlock(allocatedBlocks[i], blockBuf);
        }

        final boolean is16Bit         = fProfile.is16BitBlockPointers();
        final int     ptrsPerEntry    = is16Bit ? 8 : 16;

        // Экстент — это столько записей по 128 байт, сколько блоков перечислено в записи:
        // у «Специалиста MX» (блок 1024) 8 указателей × 8 записей = 64 записи, то есть полблока.
        // Проверено по рабочему диску (tpas.odi): TURBO.COM занимает две записи — EX=0 с rc=128
        // (первая переполнена на единицу, потому что RC=0 означает 128) и EX=1 с rc=114, каждая
        // со своими восемью блоками. Файл длиннее 64 записей (8 КБ) поэтому и получает несколько
        // записей с EX=0, EX=1, EX=2… Подставлять в EX номер блока нельзя: каталог с такой
        // разметкой BDOS не находит вовсе (DIR показывает «No file»).
        final int     recordsPerEntry = ptrsPerEntry * (fProfile.getBlockSize() / RECORD_SIZE);

        final int totalExtents = Math.max(1, (totalRecords + recordsPerEntry - 1) / recordsPerEntry);
        int recordsLeft = totalRecords;
        int blockCursor = 0;

        for (int extIdx = 0; extIdx < totalExtents; extIdx++) {
            // Перезаписываемый файл пишется на свои прежние записи каталога: освобождённая
            // запись в начале каталога иначе осталась бы дырой до самого конца каталога.
            final int freeSlot = (extIdx < reuseSlots.size()) ? reuseSlots.get(extIdx) : findFreeDirSlot(dirArea);
            if (freeSlot < 0) {
                throw new IOException("Каталог заполнен (нет свободных записей) для файла: " + file.getName());
            }

            final int off = freeSlot * DIR_ENTRY_SIZE;
            Arrays.fill(dirArea, off, off + DIR_ENTRY_SIZE, (byte) 0x00);

            dirArea[off] = (byte) user;
            System.arraycopy(fName.getBytes(StandardCharsets.US_ASCII), 0, dirArea, off + 1, 8);
            System.arraycopy(fExt .getBytes(StandardCharsets.US_ASCII), 0, dirArea, off + 9, 3);

            if (file.isReadOnly()) {
                dirArea[off + 9] |= (byte) ATTR_READ_ONLY;
            }
            if (file.isSystem()) {
                dirArea[off + 10] |= (byte) ATTR_SYSTEM;
            }

            // В записи столько блоков, сколько их нужно описанной части файла, а не столько,
            // сколько осталось от файла целиком: лишние блоки в записи BDOS посчитает частью
            // файла и прочитает его длиннее настоящего.
            final int exm = Math.max(0, (recordsPerEntry / RECORD_SIZE) - 1);
            final int blocksInThisEntry = Math.min(ptrsPerEntry, blocksNeeded - blockCursor);
            final int recordsInThisEntry = Math.min(recordsLeft, recordsPerEntry);
            final int logicalExtent = (recordsInThisEntry == 0)
                    ? (extIdx * (exm + 1))
                    : (extIdx * (exm + 1) + ((recordsInThisEntry - 1) / RECORD_SIZE));
            final int rc = (recordsInThisEntry == 0)
                    ? 0
                    : (((recordsInThisEntry - 1) % RECORD_SIZE) + 1);

            dirArea[off + 12] = (byte) (logicalExtent & EXTENT_MASK_EX);
            dirArea[off + 14] = (byte) ((logicalExtent >> 5) & EXTENT_MASK_S2);
            dirArea[off + 15] = (byte) (rc & 0xFF);

            for (int p = 0; p < blocksInThisEntry; p++) {
                final int blk = allocatedBlocks[blockCursor++];
                if (is16Bit) {
                    dirArea[off + 16 + p * 2]     = (byte)  (blk & 0xFF);
                    dirArea[off + 16 + p * 2 + 1] = (byte) ((blk >> 8) & 0xFF);
                } else {
                    dirArea[off + 16 + p] = (byte) (blk & 0xFF);
                }
            }
            recordsLeft -= recordsInThisEntry;
        }

        writeDirArea(dirArea);
    }

    private byte[] readDirArea() {
        final byte[] dirArea = new byte[fProfile.getDirEntries() * DIR_ENTRY_SIZE];
        Arrays.fill(dirArea, FREE_BYTE);

        final int dirBlocks = dirBlocksCount();
        int copied = 0;
        for (int b = 0; (b < dirBlocks) && (copied < dirArea.length); b++) {
            final byte[] block = readBlock(b);
            final int len = Math.min(block.length, dirArea.length - copied);
            System.arraycopy(block, 0, dirArea, copied, len);
            copied += len;
        }
        return dirArea;
    }

    private void writeDirArea(final byte[] dirArea) {
        final int dirBlocks = dirBlocksCount();
        final int blockSize = fProfile.getBlockSize();
        int offset = 0;
        for (int b = 0; (b < dirBlocks) && (offset < dirArea.length); b++) {
            final byte[] block = new byte[blockSize];
            Arrays.fill(block, FREE_BYTE);
            final int len = Math.min(blockSize, dirArea.length - offset);
            System.arraycopy(dirArea, offset, block, 0, len);
            writeBlock(b, block);
            offset += len;
        }
    }


    /**
     * Возвращает число блоков, занятых каталогом.
     *
     * @return блоки каталога (они же первые блоки диска)
     */
    private int dirBlocksCount() {
        final int dirBytes = fProfile.getDirEntries() * DIR_ENTRY_SIZE;
        return (dirBytes + fProfile.getBlockSize() - 1) / fProfile.getBlockSize();
    }

    /**
     * Помечает занятыми блоки, на которые ссылаются все живые записи каталога.
     *
     * @param dirArea    байты области каталога
     * @param usedBlocks отметки занятости блоков
     */
    private void markUsedBlocksFromDir(final byte[] dirArea, final boolean[] usedBlocks) {
        final boolean is16Bit = fProfile.is16BitBlockPointers();
        final int     ptrs    = is16Bit ? 8 : 16;
        for (int i = 0; i < fProfile.getDirEntries(); i++) {
            final int off = i * DIR_ENTRY_SIZE;
            final int status = dirArea[off] & 0xFF;
            // Запись за концом каталога (код пользователя вне 0–15) не описывает файл: дальше
            // в области каталога лежат данные, и их номера блоков нельзя считать указателями.
            if ((status == (FREE_BYTE & 0xFF)) || (status > MAX_USER_NUMBER)) {
                continue;
            }
            for (final int blk : readEntryBlocks(dirArea, off)) {
                if ((blk > 0) && (blk < usedBlocks.length)) {
                    usedBlocks[blk] = true;
                }
            }
        }
    }

    /**
     * Ищет в каталоге запись с таким именем и кодом пользователя.
     *
     * @param dirArea байты области каталога
     * @param user    код пользователя
     * @param fName   имя в форме записи каталога (8 байт с пробелами)
     * @param fExt    расширение в форме записи каталога (3 байта с пробелами)
     * @return true — запись найдена
     */
    private boolean findFileInDir(final byte[] dirArea, final int user, final String fName, final String fExt) {
        for (int i = 0; i < fProfile.getDirEntries(); i++) {
            final int off = i * DIR_ENTRY_SIZE;
            if (((dirArea[off] & 0xFF) == user) && matchesName(dirArea, off, fName, fExt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Освобождает все экстенты файла и возвращает номера их записей каталога: на эти записи
     * пишется новая версия файла, поэтому её экстенты остаются на прежнем месте.
     *
     * @param dirArea байты области каталога
     * @param user    код пользователя
     * @param fName   имя в форме записи каталога
     * @param fExt    расширение в форме записи каталога
     * @return номера освобождённых записей каталога
     */
    private List<Integer> collectFileSlots(final byte[] dirArea,
                                           final int    user,
                                           final String fName,
                                           final String fExt) {
        final List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < fProfile.getDirEntries(); i++) {
            final int off = i * DIR_ENTRY_SIZE;
            if (((dirArea[off] & 0xFF) == user) && matchesName(dirArea, off, fName, fExt)) {
                slots.add(i);
                dirArea[off] = FREE_BYTE;
            }
        }
        return slots;
    }

    /**
     * Возвращает номера блоков, на которые указывают экстенты в записи каталога.
     *
     * @param dirArea байты области каталога
     * @param off     смещение записи каталога
     * @return номера блоков в порядке указателей
     */
    private int[] readEntryBlocks(final byte[] dirArea, final int off) {
        final boolean is16Bit = fProfile.is16BitBlockPointers();
        final int     ptrs    = is16Bit ? 8 : 16;
        final int[]   blocks  = new int[ptrs];
        for (int p = 0; p < ptrs; p++) {
            blocks[p] = is16Bit
                    ? ((dirArea[off + 16 + p * 2] & 0xFF) | ((dirArea[off + 16 + p * 2 + 1] & 0xFF) << 8))
                    : (dirArea[off + 16 + p] & 0xFF);
        }
        return blocks;
    }

    /**
     * Сравнивает имя и расширение в записи каталога с ожидаемыми.
     *
     * @param dirArea байты области каталога
     * @param off     смещение записи каталога
     * @param fName   имя в форме записи каталога (8 байт с пробелами)
     * @param fExt    расширение в форме записи каталога (3 байта с пробелами)
     * @return true — имена совпадают
     */
    private boolean matchesName(final byte[] dirArea, final int off, final String fName, final String fExt) {
        final byte[] nBytes = fName.getBytes(StandardCharsets.US_ASCII);
        final byte[] eBytes = fExt .getBytes(StandardCharsets.US_ASCII);
        for (int j = 0; j < 8; j++) {
            if ((dirArea[off + 1 + j] & 0x7F) != nBytes[j]) {
                return false;
            }
        }
        for (int j = 0; j < 3; j++) {
            if ((dirArea[off + 9 + j] & 0x7F) != eBytes[j]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Находит первую свободную запись каталога.
     *
     * @param dirArea байты области каталога
     * @return номер записи или -1, если каталог заполнен
     */
    private int findFreeDirSlot(final byte[] dirArea) {
        for (int i = 0; i < fProfile.getDirEntries(); i++) {
            if ((dirArea[i * DIR_ENTRY_SIZE] & 0xFF) == (FREE_BYTE & 0xFF)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Подбирает свободное имя в форме 8.3 с суффиксом {@code ~N}.
     *
     * @param dirArea         байты области каталога
     * @param user            код пользователя
     * @param basePaddedName  занятое имя в форме записи каталога
     * @param paddedExt       расширение в форме записи каталога
     * @return свободное имя в форме записи каталога
     * @throws IOException если свободное имя подобрать не удалось
     */
    private String resolveUniqueName(final byte[] dirArea,
                                     final int    user,
                                     final String basePaddedName,
                                     final String paddedExt) throws IOException {
        final String trimmedBase = basePaddedName.trim();

        for (int index = 1; index <= MAX_UNIQUE_NAME_ATTEMPTS; index++) {
            final String suffix     = "~" + index;
            final int    maxBaseLen = 8 - suffix.length();
            final String prefix     = (trimmedBase.length() > maxBaseLen)
                    ? trimmedBase.substring(0, maxBaseLen)
                    : trimmedBase;

            final String candidate = String.format("%-8s", prefix + suffix);
            if (!findFileInDir(dirArea, user, candidate, paddedExt)) {
                return candidate;
            }
        }
        throw new IOException("Не удалось подобрать уникальное имя CP/M 8.3 для файла: " + trimmedBase);
    }

    /**
     * Приводит имя файла к форме 8.3: верхний регистр, допустимые символы CP/M, дополнение
     * пробелами.
     *
     * @param rawName исходное имя
     * @return имя и расширение, каждое дополнено пробелами до 8 и 3 символов
     */
    private String[] format83Name(final String rawName) {
        final String upper = rawName.toUpperCase().trim();
        final int    dot   = upper.lastIndexOf('.');
        String namePart = (dot >= 0) ? upper.substring(0, dot) : upper;
        String extPart  = (dot >= 0) ? upper.substring(dot + 1) : "";

        namePart = namePart.replaceAll(INVALID_CPM_CHARS, "_");
        extPart  = extPart .replaceAll(INVALID_CPM_CHARS, "_");

        if (namePart.isEmpty()) {
            namePart = "_";
        }

        final String paddedName = String.format("%-8s", namePart).substring(0, 8);
        final String paddedExt  = String.format("%-3s", extPart ).substring(0, 3);

        // Запятая в конце имени или расширения — признак удалённого файла (стандартное
        // расширение CP/M для «стёртого»). Имя TEST.BIN. превратилось бы в невидимую запись,
        // поэтому запятые заменяются подчёркиванием.
        return new String[]{paddedName.replace(',', '_'), paddedExt.replace(',', '_')};
    }

    /**
     * Читает блок данных по его номеру из каталога.
     *
     * @param blockIndex номер блока
     * @return содержимое блока
     */
    private byte[] readBlock(final int blockIndex) {
        final int    recordsPerBlock = fProfile.getBlockSize() / RECORD_SIZE;
        final byte[] result          = new byte[fProfile.getBlockSize()];

        for (int r = 0; r < recordsPerBlock; r++) {
            final int offset = fLayout.dataOffset(blockIndex, r);
            if ((offset >= 0) && ((offset + RECORD_SIZE) <= fImage.length)) {
                System.arraycopy(fImage, offset, result, r * RECORD_SIZE, RECORD_SIZE);
            }
        }
        return result;
    }

    /**
     * Записывает блок данных по его номеру из каталога.
     *
     * @param blockIndex номер блока
     * @param blockData  содержимое блока
     */
    private void writeBlock(final int blockIndex, final byte[] blockData) {
        final int recordsPerBlock = fProfile.getBlockSize() / RECORD_SIZE;

        for (int r = 0; r < recordsPerBlock; r++) {
            final int offset = fLayout.dataOffset(blockIndex, r);
            if ((offset >= 0) && ((offset + RECORD_SIZE) <= fImage.length)) {
                System.arraycopy(blockData, r * RECORD_SIZE, fImage, offset, RECORD_SIZE);
            }
        }
    }
}
