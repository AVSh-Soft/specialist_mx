package ru.avsh.specialist.mx.cpm;

/**
 * Пересчёт логических записей CP/M в байтовые смещения образа диска.
 *
 * Данные идут подряд: блок N занимает байты {@code системнаяОбласть + N × размер блока},
 * дорожка в образе — все её секторы на одной стороне (5 × 1024 байта у «Специалиста MX»).
 * Рабочий диск {@code CPM_TurboPascalZ80.odi} проверен по всем объявленным блокам: по этой
 * формуле заняты все они, а «блок × размер блока × стороны» даёт пустые места.
 *
 * @author -=AVSh=-
 */
final class BlockLayout {

    /** Размер логической записи CP/M. */
    private static final int RECORD_SIZE = 128;

    private final DiskProfile fProfile;

    /**
     * Конструктор.
     *
     * @param profile раскладка образа
     */
    BlockLayout(final DiskProfile profile) {
        this.fProfile = profile;
    }

    /**
     * Возвращает смещение 128-байтной записи данных в образе.
     *
     * @param blockIndex    номер блока из записи каталога
     * @param recordInBlock номер 128-байтной записи внутри блока
     * @return смещение в образе
     */
    int dataOffset(final int blockIndex, final int recordInBlock) {
        return fProfile.getSystemAreaSize()
                + blockIndex * fProfile.getBlockSize()
                + recordInBlock * RECORD_SIZE;
    }

    /**
     * Возвращает смещение записи каталога в образе.
     *
     * Каталог — плоская таблица, папок в CP/M нет: его записи идут подряд с шагом 32 байта
     * сразу за системными дорожками. Рабочий диск {@code CPM_TurboPascalZ80.odi} начинается
     * каталогом уже на 0x5000 — там же, где кончаются четыре системные дорожки.
     *
     * @param entryIndex номер записи каталога
     * @param entrySize  размер записи каталога в байтах
     * @return смещение записи каталога в образе
     */
    int dirEntryOffset(final int entryIndex, final int entrySize) {
        return fProfile.getSystemAreaSize() + entryIndex * entrySize;
    }

    /**
     * @return размер одной 128-байтной записи CP/M
     */
    static int recordSize() {
        return RECORD_SIZE;
    }
}
