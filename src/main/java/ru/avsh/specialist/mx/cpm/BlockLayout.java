package ru.avsh.specialist.mx.cpm;

/**
 * Переводит номер блока CP/M в смещение байта внутри линейного образа диска.
 *
 * Номера блоков в каталоге считаются не от начала файла, а от начала области данных —
 * сразу после зарезервированных (системных) дорожек: {@code dataOffset(0, 0)} указывает
 * на первый байт каталога. Поэтому смещение всегда начинается с
 * {@link DiskProfile#getSystemAreaSize()}.
 *
 * Без таблицы перемежения блоки идут линейно: блок {@code b} лежит по смещению
 * {@code sys + b * blockSize}. С таблицей (IBM 8" SSSD, Osborne 1) записи переставляются
 * внутри «дорожки», которой здесь считается полный круг цилиндра со всеми сторонами:
 * {@code sectorsPerTrack} в {@link DiskProfile} — это число секторов на цилиндр подряд,
 * а таблица задана значениями 1-based.
 *
 * @author -=AVSh=-
 */
final class BlockLayout {
    /** Размер логической записи CP/M в байтах. */
    private static final int RECORD_SIZE = 128;
    private final DiskProfile fProfile;

    /**
     * Конструктор.
     *
     * @param profile раскладка образа, по которой считаются смещения блоков
     */
    BlockLayout(final DiskProfile profile) {
        this.fProfile = profile;
    }

    /**
     * Возвращает смещение логической записи в образе.
     *
     * @param blockIndex    номер блока в каталоге (0 — первый блок каталога)
     * @param recordInBlock номер записи по 128 байт внутри блока
     * @return смещение байта в линейном образе
     */
    int dataOffset(final int blockIndex, final int recordInBlock) {
        final int recordsPerBlock = fProfile.getBlockSize() / RECORD_SIZE;
        final int linearRecord    = blockIndex * recordsPerBlock + recordInBlock;
        final int dataBase        = fProfile.getSystemAreaSize() + fProfile.getDirOffset();

        final int[] skewTable = fProfile.getSkewTable();
        if ((skewTable == null) || (skewTable.length == 0)) {
            return dataBase + linearRecord * RECORD_SIZE;
        }

        final int sectorSize       = fProfile.getSectorSize();
        final int recordsPerSector = Math.max(1, sectorSize / RECORD_SIZE);
        final int sectorsPerTrack  = fProfile.getSectorsPerTrack();
        final int recordsPerTrack  = sectorsPerTrack * recordsPerSector;

        final int trackIndex     = linearRecord / recordsPerTrack;
        final int recordInTrack  = linearRecord % recordsPerTrack;
        final int logicalSector  = recordInTrack / recordsPerSector;
        final int recordInSector = recordInTrack % recordsPerSector;

        final int physicalSector = (logicalSector < skewTable.length)
                ? (skewTable[logicalSector] - 1)
                : logicalSector;

        return dataBase
                + trackIndex * (sectorsPerTrack * sectorSize)
                + physicalSector * sectorSize
                + recordInSector * RECORD_SIZE;
    }

    /**
     * Возвращает смещение записи каталога в образе с учётом раскладки блоков и таблицы перемежения.
     *
     * @param entryIndex номер записи каталога (0, 1, 2...)
     * @param entrySize  размер записи каталога в байтах (32)
     * @return байтовое смещение записи каталога в образе
     */
    int dirEntryOffset(final int entryIndex, final int entrySize) {
        final int byteOffsetInDir = entryIndex * entrySize;
        final int blockSize       = fProfile.getBlockSize();
        final int blockIndex      = byteOffsetInDir / blockSize;
        final int offsetInBlock   = byteOffsetInDir % blockSize;
        final int recordInBlock   = offsetInBlock / RECORD_SIZE;
        final int offsetInRecord  = offsetInBlock % RECORD_SIZE;

        return dataOffset(blockIndex, recordInBlock) + offsetInRecord;
    }
}
