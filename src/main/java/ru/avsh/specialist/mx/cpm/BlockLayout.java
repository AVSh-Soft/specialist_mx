package ru.avsh.specialist.mx.cpm;

final class BlockLayout {
    private static final int RECORD_SIZE = 128;
    private final DiskProfile fProfile;

    BlockLayout(final DiskProfile profile) {
        this.fProfile = profile;
    }

    int dataOffset(final int blockIndex, final int recordInBlock) {
        final int recordsPerBlock = fProfile.getBlockSize() / RECORD_SIZE;
        final int linearRecord    = blockIndex * recordsPerBlock + recordInBlock;

        final int[] skewTable = fProfile.getSkewTable();
        if ((skewTable == null) || (skewTable.length == 0)) {
            return fProfile.getSystemAreaSize() + linearRecord * RECORD_SIZE;
        }

        final int sectorSize       = fProfile.getSectorSize();
        final int recordsPerSector = Math.max(1, sectorSize / RECORD_SIZE);
        final int sectorsPerTrack  = fProfile.getSectorsPerTrack();
        final int recordsPerTrack  = sectorsPerTrack * recordsPerSector;

        final int trackIndex          = linearRecord / recordsPerTrack;
        final int recordInTrack       = linearRecord % recordsPerTrack;
        final int logicalSector       = recordInTrack / recordsPerSector;
        final int recordInSector      = recordInTrack % recordsPerSector;

        final int physicalSector = (logicalSector < skewTable.length)
                ? (skewTable[logicalSector] - 1)
                : logicalSector;

        return fProfile.getSystemAreaSize()
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


    static int recordSize() {
        return RECORD_SIZE;
    }
}
