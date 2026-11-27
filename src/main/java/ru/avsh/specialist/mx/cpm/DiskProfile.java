package ru.avsh.specialist.mx.cpm;

/**
 * Параметры дискеты CP/M: геометрия, зарезервированные дорожки, размер блока и каталога.
 *
 * Профиль описывает только раскладку образа: где начинается системная область, как номера
 * блоков переводятся в смещение (см. {@link BlockLayout}) и сколько записей вмещает каталог.
 * Читает и пишет файлы по профилю {@link CpmImageReader} и {@link CpmImageWriter},
 * а узнаёт формат по содержимому образа {@link DiskProfileFactory}.
 *
 * Профиль — не полное описание дискеты: он не несёт таблицу аллокации блоков, а порядок
 * обхода секторов задаётся либо линейно (таблица перемежения {@code null}), либо таблицей
 * {@link #getSkewTable()}; «дорожка» здесь — это полный круг цилиндра, то есть обе стороны
 * подряд.
 *
 * @author -=AVSh=-
 */
public final class DiskProfile {
    private final String  fName;
    private final int     fTracks;
    private final int     fSides;
    private final int     fSectorsPerTrack;
    private final int     fSectorSize;
    private final int     fReservedTracks;
    private final int     fBlockSize;
    private final int     fDirEntries;
    private final int[]   fSkewTable;
    private final int     fDirOffset;

    /**
     * Конструктор.
     *
     * @param name            имя профиля для сообщений и диалогов
     * @param tracks          число цилиндров
     * @param sides           число сторон (1 или 2)
     * @param sectorsPerTrack секторов на дорожке
     * @param sectorSize      размер сектора в байтах
     * @param reservedTracks  число системных дорожек в начале диска
     * @param blockSize       размер блока в байтах
     * @param dirEntries      число записей каталога
     * @param skewTable       таблица перемежения секторов или null, если перемежения нет
     */
    public DiskProfile(final String name  ,
                       final int    tracks,
                       final int    sides ,
                       final int    sectorsPerTrack,
                       final int    sectorSize,
                       final int    reservedTracks ,
                       final int    blockSize ,
                       final int    dirEntries,
                       final int[]  skewTable) {

        this(name, tracks, sides, sectorsPerTrack, sectorSize, reservedTracks, blockSize,
                dirEntries, skewTable, 0);
    }

    /**
     * Конструктор профиля, у которого каталог стоит не сразу за системными дорожками.
     *
     * <p>Обычному CP/M-образу смещение не нужно: каталог занимает первые блоки области данных,
     * и это записано в {@code reservedTracks}. Но запись B.01 держит каталог на 32 байта
     * раньше конца системной области — там, где у обычного образа лежит хвост последней
     * системной дорожки, — и сдвиг дорожками не выражается.
     *
     * @param name            имя профиля для сообщений и диалогов
     * @param tracks          число цилиндров
     * @param sides           число сторон (1 или 2)
     * @param sectorsPerTrack секторов на дорожке
     * @param sectorSize      размер сектора в байтах
     * @param reservedTracks  число системных дорожек в начале диска
     * @param blockSize       размер блока в байтах
     * @param dirEntries      число записей каталога
     * @param skewTable       таблица перемежения секторов или null, если перемежения нет
     * @param dirOffset       поправка к началу области данных: каталог начинается здесь
     */
    public DiskProfile(final String name  ,
                       final int    tracks,
                       final int    sides ,
                       final int    sectorsPerTrack,
                       final int    sectorSize,
                       final int    reservedTracks ,
                       final int    blockSize ,
                       final int    dirEntries,
                       final int[]  skewTable ,
                       final int    dirOffset) {

        this.fName            = name  ;
        this.fTracks          = tracks;
        this.fSides           = sides ;
        this.fSectorsPerTrack = sectorsPerTrack;
        this.fSectorSize      = sectorSize;
        this.fReservedTracks  = reservedTracks;
        this.fBlockSize       = blockSize ;
        this.fDirEntries      = dirEntries;
        this.fSkewTable       = skewTable ;
        this.fDirOffset       = dirOffset ;
    }

    /**
     * @return имя профиля
     */
    public String getName() {
        return fName;
    }

    /**
     * @return число цилиндров
     */
    public int getTracks() {
        return fTracks;
    }

    /**
     * @return число сторон
     */
    public int getSides() {
        return fSides;
    }

    /**
     * @return секторов на дорожке
     */
    public int getSectorsPerTrack() {
        return fSectorsPerTrack;
    }

    /**
     * @return размер сектора в байтах
     */
    public int getSectorSize() {
        return fSectorSize;
    }

    /**
     * @return число системных дорожек
     */
    public int getReservedTracks() {
        return fReservedTracks;
    }

    /**
     * @return размер блока в байтах
     */
    public int getBlockSize() {
        return fBlockSize;
    }

    /**
     * @return число записей каталога
     */
    public int getDirEntries() {
        return fDirEntries;
    }

    /**
     * @return таблица перемежения секторов или null
     */
    public int[] getSkewTable() {
        return fSkewTable;
    }

    /**
     * @return полный размер образа диска в байтах
     */
    public int getImageSize() {
        return fTracks * fSides * fSectorsPerTrack * fSectorSize;
    }

    /**
     * Считает блоки, которые остаются под каталог и файлы, — по ним выбирается ширина
     * указателя на блок и задаётся верхняя граница допустимых номеров блоков.
     *
     * Блоки адресуются от начала области данных, поэтому каталог занимает первые
     * {@code dirEntries * 32 / blockSize} блоков, а размер результата равен числу блоков
     * всей области данных, включая каталог. Если размер системной области не кратен
     * размеру блока (так у IBM 8" SSSD: 6656 байт против блока 1024), последний блок
     * области данных простирается дальше конца образа — {@link BlockLayout} такой
     * «хвост» просто не читает.
     *
     * @return количество блоков в области данных вместе с блоками каталога
     */
    public int totalBlocks() {
        final long totalTracks = (long) fTracks * fSides - fReservedTracks;
        final long totalBytes  = totalTracks * fSectorsPerTrack * fSectorSize;
        return (int) (totalBytes / fBlockSize);
    }

    /**
     * Возвращает размер системной области — занятой загрузчиком, BDOS и BIOS.
     *
     * @return количество байт от начала диска до каталога
     */
    public int getSystemAreaSize() {
        return fReservedTracks * fSectorsPerTrack * fSectorSize;
    }

    /**
     * Возвращает поправку к началу области данных.
     *
     * Обычно она нулевая: каталог занимает первые блоки сразу за системными дорожками.
     * Ненулевой она бывает только у профилей, где каталог стоит со сдвигом внутри образа, —
     * у записи B.01 (см. {@link DiskProfileFactory}).
     *
     * @return сдвиг начала области данных относительно конца системных дорожек
     */
    public int getDirOffset() {
        return fDirOffset;
    }

    /**
     * Выбирает ширину указателя на блок в записи каталога: одна байтовая ячейка или пара.
     *
     * Под номера блоков в записи каталога отведено всегда 16 байт: в них укладывается либо
     * 16 однобайтовых указателей, либо 8 пар. Пары нужны, когда номеров блоков больше 255
     * и 8-битное поле их не вмещает — это CP/M 2.2 с {@code DSM > 255}. На ровно 256 блоках
     * 16-битная схема оставляет в записи только 8 указателей и разрезает блок на два
     * экстента, поэтому граница — именно «больше 255»: 256 блоков в CP/M 2.2 адресуются
     * по-старому, одним байтом.
     *
     * @return true, если номера блоков занимают по две байта
     */
    public boolean is16BitBlockPointers() {
        return totalBlocks() > 255;
    }

    /** Стандарт IBM 8" SSSD (250 КБ). */
    public static final DiskProfile IBM_8_SSSD = new DiskProfile(
            "IBM 8\" SSSD (250 KB)", 77, 1, 26, 128, 2, 1024, 64,
            new int[]{1, 7, 13, 19, 25, 5, 11, 17, 23, 3, 9, 15, 21, 2, 8, 14, 20, 26, 6, 12, 18, 24, 4, 10, 16, 22}
    );

    /** Специалист MX ODI (800 КБ), две системные дорожки и блок 2048. */
    public static final DiskProfile SPECIALIST_MX_800K = new DiskProfile(
            "Specialist MX ODI (800 KB)", 80, 2, 5, 1024, 2, 2048, 128, null
    );

    /**
     * Специалист MX с загрузчиком и CP/M на 4 системных дорожках ({@code bst_cpm_v1.3.odi},
     * {@code CPM_TurboPascalZ80.odi}): блок 2048 (304 блока, 16-битные указатели), каталог
     * 128 записей (четыре блока, 16 КБ записи), системная область 20 480 байт.
     *
     * <p>Известное ограничение: диски с блоком 4096 на этом коде не читаются корректно —
     * см. {@link CpmImageReader} и {@link CpmImageWriter}. Раскладки с блоком 4096
     * в конвертере нет.
     */
    public static final DiskProfile SPECIALIST_MX_ODI_4SYS = new DiskProfile(
            "Specialist MX ODI (800 KB, 4 sys tracks)", 80, 2, 5, 1024, 4, 2048, 128, null
    );

    /**
     * Строит профиль по параметрам, объявленным в теле образа: «Орион-стандарт» кладёт
     * DPB в заголовок файла, а геометрию, размер блока и число записей каталога выводит
     * из него {@link OrionImageReader}.
     *
     * <p>Размер образа у такого профиля не обязан совпадать ни с одним штатным профилем
     * (у {@code Games01.kdi} он меньше номинального), поэтому профиль считается по месту,
     * а не берётся из {@link #SPECIALIST_MX_800K}.
     *
     * @param name            имя профиля для сообщений и диалогов
     * @param tracks          число дорожек всего диска
     * @param sides           число сторон (1 или 2)
     * @param sectorsPerTrack секторов на дорожке
     * @param sectorSize      размер сектора в байтах
     * @param reservedTracks  число системных дорожек в начале диска
     * @param blockSize       размер блока в байтах
     * @param dirEntries      число записей каталога
     * @param skewTable       таблица перемежения секторов или null
     * @param dirOffset       поправка к началу области данных
     * @return профиль с заданной раскладкой
     */
    public static DiskProfile of(final String name  ,
                                 final int    tracks,
                                 final int    sides ,
                                 final int    sectorsPerTrack,
                                 final int    sectorSize,
                                 final int    reservedTracks ,
                                 final int    blockSize ,
                                 final int    dirEntries,
                                 final int[]  skewTable ,
                                 final int    dirOffset) {

        return new DiskProfile(name, tracks, sides, sectorsPerTrack, sectorSize, reservedTracks,
                blockSize, dirEntries, skewTable, dirOffset);
    }
}
