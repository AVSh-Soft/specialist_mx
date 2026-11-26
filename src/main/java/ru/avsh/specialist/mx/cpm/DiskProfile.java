package ru.avsh.specialist.mx.cpm;

/**
 * Параметры дискеты CP/M: геометрия, зарезервированные дорожки, размер блока и каталога.
 *
 * Профиль полностью описывает раскладку образа: где находятся системные дорожки, как в каталоге
 * нумеруются блоки и сколько записей каталог вмещает. По набору таких параметров конвертер
 * читает и пишет файлы, а {@link DiskProfileFactory} узнаёт формат по содержимому образа.
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
    public DiskProfile(final String name,
                       final int    tracks,
                       final int    sides,
                       final int    sectorsPerTrack,
                       final int    sectorSize,
                       final int    reservedTracks,
                       final int    blockSize,
                       final int    dirEntries,
                       final int[]  skewTable) {
        this.fName            = name;
        this.fTracks          = tracks;
        this.fSides           = sides;
        this.fSectorsPerTrack = sectorsPerTrack;
        this.fSectorSize      = sectorSize;
        this.fReservedTracks  = reservedTracks;
        this.fBlockSize       = blockSize;
        this.fDirEntries      = dirEntries;
        this.fSkewTable       = skewTable;
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
     * @return количество блоков, оставшихся под каталог и файлы
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
     * Выбирает ширину указателя на блок в записи каталога.
     *
     * 16-битные указатели нужны только при более чем 255 блоках: 8-битное поле не вмещает номер
     * блока 256 и выше. На ровно 256 блоках CP/M требует 8-битную схему с 16 указателями —
     * иначе 16-битное представление разбивает блок на 8 записей-указателей, и первый же блок
     * диска не входит в нумерацию. Поэтому граница — именно «больше 255».
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

    /** Kaypro II (200 КБ). */
    public static final DiskProfile KAYPRO_II = new DiskProfile(
            "Kaypro II (200 KB)", 40, 1, 10, 512, 1, 1024, 64, null
    );

    /** Специалист MX ODI (800 КБ), две системные дорожки и блок 2048. */
    public static final DiskProfile SPECIALIST_MX_800K = new DiskProfile(
            "Specialist MX ODI (800 KB)", 80, 2, 5, 1024, 2, 2048, 128, null
    );

    /**
     * Специалист MX с загрузчиком и CP/M на 4 системных дорожках ({@code bst_cpm_v1.3.odi},
     * {@code CPM_TurboPascalZ80.odi}): блок 1024 (896 блоков, 16-битные указатели), каталог
     * 256 записей, системная область 20 480 байт.
     */
    public static final DiskProfile SPECIALIST_MX_ODI_4SYS = new DiskProfile(
            "Specialist MX ODI (800 KB, 4 sys tracks)", 80, 2, 5, 1024, 4, 1024, 256, null
    );
}
