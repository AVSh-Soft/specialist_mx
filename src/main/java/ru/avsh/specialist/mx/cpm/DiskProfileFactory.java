package ru.avsh.specialist.mx.cpm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Распознаёт формат образа CP/M по размеру файла и содержимому каталога.
 *
 * Профилей одного размера бывает несколько (200, 400 и 800 КБ), и различаются они числом
 * системных дорожек и размером блока. Размер файла отсекает заведомо чужие форматы, а между
 * совпавшими по размеру решает каталог: побеждает раскладка, в которой каталог начинается
 * так, как его читает {@link CpmImageReader}.
 *
 * @author -=AVSh=-
 */
public final class DiskProfileFactory {

    /** Одна запись каталога CP/M описывает 16 КБ — 128 записей по 128 байт. */
    private static final int MAX_EXTENT_RECORDS = 128;

    /** Код пользователя CP/M — от 0 до 15; всё выше — уже не каталог, а данные. */
    private static final int MAX_USER_NUMBER = 15;

    /** Байт «свободно» в каталоге CP/M. */
    private static final int FREE_BYTE = 0xE5;

    /** Размер записи каталога в байтах. */
    private static final int DIR_ENTRY_SIZE = 32;

    /** Размер логической записи CP/M — по ней считается счётчик записей в экстенте. */
    private static final int RECORD_SIZE = 128;

    private static final List<DiskProfile> KNOWN_PROFILES;

    static {
        final List<DiskProfile> list = new ArrayList<>();

        // Порядок важен: профили одного размера различаются только раскладкой, и при равной
        // оценке каталога побеждает первый. Первым идёт «Специалист MX» — тот формат, ради
        // которого писан конвертер, и образ этого размера скорее он, чем чужой.
        list.add(DiskProfile.SPECIALIST_MX_ODI_4SYS);

        // Специалист MX / Корвет 800 КБ — 2 системные дорожки, блок 2048
        list.add(DiskProfile.SPECIALIST_MX_800K);

        // Специалист MX / Корвет 800 КБ — 3 системные дорожки, блок 2048
        list.add(new DiskProfile(
                "Specialist MX / Korvet ODI (800 KB, 3 sys tracks)", 80, 2, 5, 1024, 3, 2048, 128, null
        ));

        // Специалист MX 800 КБ — 2 системные дорожки, блок 4096
        list.add(new DiskProfile(
                "Specialist MX ODI (800 KB, 4K block)", 80, 2, 5, 1024, 2, 4096, 256, null
        ));

        // Советский CP/M 720 КБ (737 280 байт) — 80x2x9x512
        list.add(new DiskProfile(
                "CP/M 720 KB (80x2x9x512)", 80, 2, 9, 512, 2, 2048, 128, null
        ));

        // Osborne 1 SSDD (200 КБ) — 40x1x5x1024, блок 2048 (osb1sssd у cpmtools:
        // BSH 4 BLM 15 DSM 91). При блоке 1024 два блока каталога занимали бы четыре
        // дорожки, и первые же блоки данных попадали бы в область каталога.
        list.add(new DiskProfile(
                "Osborne 1 SSDD (200 KB)", 40, 1, 5, 1024, 3, 2048, 64,
                new int[]{1, 3, 5, 2, 4}
        ));

        // Osborne 1 SSSD (100 КБ) — 40x1x10x256
        list.add(new DiskProfile(
                "Osborne 1 SSSD (100 KB)", 40, 1, 10, 256, 3, 2048, 64,
                new int[]{1, 3, 5, 7, 9, 2, 4, 6, 8, 10}
        ));

        // Kaypro 4 / 2X (400 КБ) — 40 цилиндров по две стороны, 10x512, блок 2048
        list.add(new DiskProfile(
                "Kaypro 4 (400 KB)", 40, 2, 10, 512, 1, 2048, 64, null
        ));

        // Kaypro II / 2 (200 КБ) — 40x1x10x512
        list.add(DiskProfile.KAYPRO_II);

        // Amstrad CPC / PCW Data (180 КБ) — 40x1x9x512, без системных дорожек
        list.add(new DiskProfile(
                "Amstrad CPC Data (180 KB)", 40, 1, 9, 512, 0, 1024, 64, null
        ));

        // Amstrad CPC / PCW System (180 КБ) — 40x1x9x512, 2 системные дорожки
        list.add(new DiskProfile(
                "Amstrad CPC System (180 KB)", 40, 1, 9, 512, 2, 1024, 64, null
        ));

        // IBM 8" SSSD (250 КБ) — 77x1x26x128, перемежение на 6
        list.add(DiskProfile.IBM_8_SSSD);

        KNOWN_PROFILES = Collections.unmodifiableList(list);
    }

    /**
     * Конструктор.
     */
    private DiskProfileFactory() {
        //
    }

    /**
     * Подбирает наиболее подходящий профиль диска по размеру файла и содержимому каталога.
     *
     * @param file файл образа диска
     * @return профиль, описывающий раскладку образа
     * @throws IOException если файл не читается или его размер не совпадает ни с одним форматом
     */
    public static DiskProfile detect(final File file) throws IOException {
        final byte[] image    = Files.readAllBytes(file.toPath());
        final long   fileSize = image.length;

        final List<DiskProfile> candidates = new ArrayList<>();
        for (final DiskProfile profile : KNOWN_PROFILES) {
            if (profile.getImageSize() == fileSize) {
                candidates.add(profile);
            }
        }

        if (candidates.isEmpty()) {
            throw new IOException(String.format(
                    "Неизвестный размер образа диска: %d байт. Поддерживаются: %s", fileSize, knownSizes()));
        }

        // Профилей одного размера бывает несколько, поэтому решение принимает каталог.
        return selectByDirectory(image, candidates);
    }

    /**
     * Возвращает профили, у которых размер образа совпадает с файлом.
     *
     * Расширение файла ни на что не влияет: один и тот же линейный образ встречается под именами
     * {@code .img}, {@code .dsk} и {@code .odi}, а внутри линейного образа формат определяется
     * только геометрией. Смотреть на расширение — значит повторить ту же ошибку в другом месте.
     *
     * @param file файл образа диска
     * @return профили-кандидаты (пустой список, если размер ни с одним не совпал)
     * @throws IOException если файл не читается
     */
    public static List<DiskProfile> candidates(final File file) throws IOException {
        final long fileSize = Files.size(file.toPath());

        final List<DiskProfile> result = new ArrayList<>();
        for (final DiskProfile profile : KNOWN_PROFILES) {
            if (profile.getImageSize() == fileSize) {
                result.add(profile);
            }
        }
        return result;
    }

    /**
     * Проверяет, что для файла такого размера вообще есть раскладка: по этому признаку GUI решает,
     * стоит ли вообще открывать образ в конвертере.
     *
     * @param file файл образа диска
     * @return true — размер образа известен конвертеру
     * @throws IOException если файл не читается
     */
    public static boolean isKnownSize(final File file) throws IOException {
        return !candidates(file).isEmpty();
    }

    /**
     * Перечисляет поддерживаемые размеры образов для сообщения об ошибке.
     *
     * @return размеры через запятую, от меньшего к большему
     */
    public static String knownSizes() {
        final TreeSet<Integer> sizes = new TreeSet<>();
        for (final DiskProfile profile : KNOWN_PROFILES) {
            sizes.add(profile.getImageSize());
        }
        final StringBuilder text = new StringBuilder();
        for (final int size : sizes) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(size).append(" байт");
        }
        return text.toString();
    }

    /**
     * Сравнивает профили-кандидаты по содержимому их областей каталога: побеждает тот, в котором
     * каталог действительно начинается так, как ожидает {@link CpmImageReader}.
     *
     * @param image      байты образа диска
     * @param candidates профили, подходящие по размеру образа
     * @return профиль с наибольшей оценкой каталога
     */
    private static DiskProfile selectByDirectory(final byte[] image, final List<DiskProfile> candidates) {
        DiskProfile best      = candidates.get(0);
        int         bestScore = Integer.MIN_VALUE;

        for (final DiskProfile candidate : candidates) {
            final int score = scoreDirectory(image, candidate);
            if (score > bestScore) {
                bestScore = score;
                best      = candidate;
            }
        }
        return best;
    }

    /**
     * Оценивает, насколько область каталога для данного профиля похожа на настоящий каталог CP/M.
     *
     * Область каталога читается ровно так же, как её читает {@link CpmImageReader}: начиная
     * с первой дорожки после зарезервированных, блок за блоком, по четыре 32-байтных слота
     * на 128-байтную запись.
     *
     * Решает не мусор в конце области, а заполненная часть: у правильной раскладки записи
     * согласованы между собой — имя это печатный ASCII, номера блоков лежат внутри диска
     * и не указывают на каталог, а число указателей покрывает {@code rc}. У неправильной
     * раскладки первая же запись оказывается несогласованной, даже если по смещению случайно
     * лежит байт {@code 0xE5}. Поэтому несогласованная запись заканчивает подсчёт и понижает
     * оценку, а свободные записи ({@code 0xE5}) после согласованных — нормальный конец каталога.
     *
     * @param image байты образа диска
     * @param p     проверяемый профиль
     * @return чем больше оценка, тем правдоподобнее каталог для этого профиля
     */
    private static int scoreDirectory(final byte[] image, final DiskProfile p) {
        final int     maxBlocks       = p.totalBlocks();
        final boolean is16Bit         = p.is16BitBlockPointers();
        final int     recordsPerBlock = p.getBlockSize() / RECORD_SIZE;

        int goodEntries = 0;
        int badEntries  = 0;
        int firstStatus = -1;

        // Записи каталога лежат подряд с шагом 32 байта, начиная с блока каталога: у профиля
        // с 4 системными дорожками это два блока после их конца, у профиля с 2 — сразу за ними.
        final int     dirBase         = new BlockLayout(p).dirEntryOffset(0, DIR_ENTRY_SIZE);
        for (int i = 0; i < p.getDirEntries(); i++) {
            final int offset = dirBase + i * DIR_ENTRY_SIZE;
            if ((offset < 0) || ((offset + DIR_ENTRY_SIZE) > image.length)) {
                return -1000;
            }

            final int status = image[offset] & 0xFF;
            if (firstStatus < 0) {
                firstStatus = status;
            }

            // 0xE5 — свободная запись. В начале области это пустой (или «стёртый») диск,
            // после заполненных — обычный конец каталога. В обоих случаях подсчёт прекращается.
            if (status == FREE_BYTE) {
                break;
            }

            // Код пользователя — от 0 до 15. Всё остальное означает, что на месте каталога
            // лежат данные файла или код программы: у bst_cpm_v1.3.odi сразу за каталогом
            // идёт загрузчик. Это не «конец каталога»: настоящий каталог свободные записи
            // заполняет байтом 0xE5, а не кодом, поэтому такая запись — признак чужой раскладки.
            if (status > MAX_USER_NUMBER) {
                badEntries++;
                break;
            }

            if (isGoodEntry(image, offset, is16Bit, recordsPerBlock, maxBlocks)) {
                goodEntries++;
            } else {
                badEntries++;
                break;
            }
        }

        // Заполненная часть решает всё: каждая согласованная запись — плюс, каждая
        // несогласованная — сильный минус. Первая запись лишь разводит случаи, когда
        // согласованных записей нет вовсе, — пустой диск.
        final int filled = (goodEntries * 10) - (badEntries * 20);
        if (goodEntries == 0) {
            // Ни одной согласованной записи: у правильной раскладки каталог начинается либо
            // с живых записей, либо с пометки 0xE5. Первая запись с ненулевым кодом
            // пользователя и при этом несогласованная означает, что по этому смещению каталога
            // нет вовсе, — так выглядит раскладка, у которой «каталог» попал на данные.
            return ((firstStatus == FREE_BYTE) ? 0 : -50) + freeAreaScore(image, p);
        }
        // Каталог согласован, но это может быть и каталог «Специалиста MX» с другим числом
        // системных дорожек: тот же блок 1024, те же указатели. Различают их только начало
        // диска (там загрузчик, а не данные) и наличие самих записей каталога по смещению.
        final int start = (isSystemArea(image, p) ? 20 : 0) + (hasDirectoryBytes(image, p) ? 20 : 0);
        return filled + start;
    }

    /**
     * Проверяет, согласована ли одна запись каталога: имя, номера блоков и счётчик записей.
     *
     * @param image           байты образа диска
     * @param offset          смещение записи
     * @param is16Bit         true — 16-битные номера блоков
     * @param recordsPerBlock сколько записей по 128 байт в одном блоке
     * @param maxBlocks       сколько блоков на диске
     * @return true — запись выглядит как настоящая
     */
    private static boolean isGoodEntry(final byte[] image,
                                       final int    offset,
                                       final boolean is16Bit,
                                       final int    recordsPerBlock,
                                       final int    maxBlocks) {
        // Имя файла — печатные ASCII-символы, дополненные пробелами.
        for (int c = 1; c <= 11; c++) {
            final int ch = image[offset + c] & 0x7F;
            if ((ch < 0x20) || (ch > 0x7E)) {
                return false;
            }
        }

        // Под указатели отведено 16 байт: либо восемь пар (16-битные номера, младший
        // байт первым), либо шестнадцать одиночных байт.
        final int pointerSlots = is16Bit ? 8 : 16;
        int       usedPointers = 0;
        for (int b = 0; b < pointerSlots; b++) {
            final int blk = blockPointer(image, offset, b, is16Bit);
            if ((blk == 0) || (blk == FREE_BYTE)) {
                continue;
            }
            usedPointers++;
            // Номер за пределами диска означает неверный размер блока или ширину указателя.
            if (blk >= maxBlocks) {
                return false;
            }
        }

        // Запись без блоков ничего не описывает. Зато лишние места под указатели — обычное дело:
        // при блоке 1024 запись покрывает 16 КБ, а в поле указателей номера записаны парами,
        // и «нулевые» места — это просто половина пары, которая ни на что не указывает.
        // Поэтому сверх числа мест под указатели проверяется только очевидная нехватка:
        // номеров заведомо больше, чем может быть у записи.
        final int recordsNeeded = ((image[offset + 15] & 0xFF) + recordsPerBlock - 1) / recordsPerBlock;
        return (usedPointers > 0) && (recordsNeeded <= pointerSlots * recordsPerBlock);
    }

    /**
     * Оценивает, насколько байты на месте каталога похожи на настоящий каталог CP/M.
     *
     * Свободная часть каталога проверяется только когда заполненных записей нет или область
     * каталога прочитана целиком: между записями каталога лежат данные файлов, и там нули
     * вполне обычны. Первые же байты каталога — другое дело: у настоящего CP/M-диска это
     * либо живая запись, либо пометка {@code 0xE5} от форматирования, но не нули от данных.
     *
     * @param image первая половина области каталога (начало записи)
     * @param p     проверяемый профиль
     * @return 1 — свободная часть каталога заполнена как положено, 0 — непонятно, -1 — это данные
     */
    private static int freeAreaScore(final byte[] image, final DiskProfile p) {
        final int base = new BlockLayout(p).dirEntryOffset(0, DIR_ENTRY_SIZE);
        if ((base + 32) > image.length) {
            return -1;
        }

        int  free  = 0;
        int  erased = 0;
        for (int i = 0; (i < 32) && (free == 0); i++) {
            final int b = image[base + i] & 0xFF;
            if (b == FREE_BYTE) {
                erased++;
            } else if (b == 0) {
                free++;
            }
        }
        if (free > 0) {
            return -1;
        }
        return (erased > 0) ? 1 : 0;
    }

    /**
     * Проверяет, что в области каталога стоят живые записи.
     *
     * Угадать, что каталога нет, можно по байтам: форматирование заполняет область каталога
     * байтом {@code 0xE5}, а записи файлов — это печатные имена с номерами блоков. Если же
     * по смещению каталога лежат нули (пустая область) или код загрузчика, то каталога здесь
     * нет вовсе, и складная оценка получится только за счёт случайных байтов.
     *
     * @param image байты образа диска
     * @param p     проверяемый профиль
     * @return true — по смещению каталога стоят записи, а не пустота и не код
     */
    private static boolean hasDirectoryBytes(final byte[] image, final DiskProfile p) {
        final int base = new BlockLayout(p).dirEntryOffset(0, DIR_ENTRY_SIZE);
        if (((base + 32) > image.length)
                || (image[base] == 0) || (image[base] == FREE_BYTE)) {
            return false;
        }
        for (int c = 1; c <= 8; c++) {
            final int ch = image[base + c] & 0x7F;
            if ((ch < 0x20) || (ch > 0x7E)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Проверяет, что по смещению каталога лежит начало системной области: загрузчик с таблицей
     * параметров диска либо след системных файлов.
     *
     * Каталог начинается сразу после зарезервированных дорожек, поэтому угадать смещение значит
     * проверить, чем заняты эти дорожки. Загрузчик «Специалиста MX» кладёт в начало диска
     * команду перехода ({@code C3 xx xx}) и адреса буферов, а его CP/M — таблицу параметров
     * диска с характерными значениями: число секторов на дорожке, маска блока, размер блока.
     * Диск, размеченный на 4 системные дорожки, в начале содержит настоящий загрузчик; если
     * профиль думает, что дорожек две, то «началом системной области» окажется код BDOS или
     * каталог, и признак это покажет.
     *
     * @param image байты образа диска
     * @param p     проверяемый профиль
     * @return true — по смещению каталога действительно лежит системная область
     */
    private static boolean isSystemArea(final byte[] image, final DiskProfile p) {
        final int base = p.getSystemAreaSize();
        if ((base < 0) || (base + 32 > image.length)) {
            return false;
        }
        if (p.getReservedTracks() == 0) {
            return false;
        }

        // Загрузчик начинается с перехода на свой код.
        if ((image[base] & 0xFF) == 0xC3) {
            return true;
        }

        // Внутри 8 системных дорожек — DPB с числом записей на дорожке и маской блока.
        final int  dirBytes  = p.getDirEntries() * DIR_ENTRY_SIZE;
        final long limit     = Math.min((long) p.getImageSize() - dirBytes - 16, 0x2000);
        final int  sectorsPerTrack = p.getSectorsPerTrack();
        final int  blockMask = p.getBlockSize() / RECORD_SIZE - 1;
        for (int i = base; i < (base + limit); i++) {
            if (((image[i] & 0xFF) == sectorsPerTrack) && ((image[i + 1] & 0xFF) == blockMask)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Читает номер блока из записи каталога по индексу указателя.
     *
     * При 16-битных указателях номер занимает пару байт, при 8-битных — один байт: в поле
     * записи 16 байт, и это 16 однобайтных номеров подряд.
     *
     * @param image        байты образа диска
     * @param entryOffset  смещение записи каталога
     * @param pointerIndex индекс указателя в записи
     * @param is16Bit      true — 16-битные номера блоков
     * @return номер блока
     */
    private static int blockPointer(final byte[] image,
                                    final int entryOffset,
                                    final int pointerIndex,
                                    final boolean is16Bit) {
        if (is16Bit) {
            final int lo = image[entryOffset + 16 + pointerIndex * 2] & 0xFF;
            final int hi = image[entryOffset + 16 + pointerIndex * 2 + 1] & 0xFF;
            return (hi << 8) | lo;
        }
        return image[entryOffset + 16 + pointerIndex] & 0xFF;
    }
}
