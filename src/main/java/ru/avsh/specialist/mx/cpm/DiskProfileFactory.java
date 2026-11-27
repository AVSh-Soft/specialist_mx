package ru.avsh.specialist.mx.cpm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Распознаёт формат образа CP/M по содержимому файла.
 *
 * <p>Три категории форматов обрабатываются по-разному. Запись B.01 (см.
 * {@link KdiImageReader#isB01(byte[])}) — это образ «Специалиста MX» с 32-байтным заголовком
 * загрузчика в начале файла: раскладка у неё известна профилем {@link DiskProfile#SPECIALIST_MX_800K},
 * угадывать нечего. Контейнер KDI (см. {@link KdiImageReader}) описывает себя сам: в его
 * 256-байтном заголовке лежат геометрия и таблица параметров диска, поэтому профиль строится
 * из заголовка, а тело читается как линейный образ без угадывания. Линейный образ, наоборот,
 * ничего о себе не сообщает: размер файла отсекает заведомо чужие форматы, а между совпавшими
 * по размеру решает каталог — побеждает раскладка, у которой байты на месте каталога читаются
 * как настоящие записи CP/M.
 *
 * <p>В списке раскладок {@link #KNOWN_PROFILES} лежат только те, что прочитали образы,
 * снятые с настоящих дисков: «Специалист MX» с четырьмя и с двумя системными дорожками,
 * Osborne 1 SSDD (200 КБ) и IBM 8" SSSD. Раскладки, для которых эталонного образа нет,
 * (Osborne 1 SSSD на 100 КБ, Kaypro II и 4, Amstrad CPC Data и System, CP/M 720 КБ,
 * «Специалист» с блоком 4096) из конвертера убраны: непроверенная раскладка молча отдаёт
 * мусор вместо имён файлов, и держать её хуже, чем не открывать образ вовсе.
 *
 * Расширение файла не учитывается: линейный образ встречается под именами {@code .img},
 * {@code .dsk} и {@code .odi}, а под {@code .kdi} лежат и контейнер, и запись B.01. Определять
 * формат по расширению значит повторять ту же ошибку в другом месте. Эмуляторные контейнеры,
 * которых здесь нет (.td0, .imd, DSK Amstrad), нужно предварительно разложить в KDI или
 * линейный образ.
 *
 * <p>Отдельная категория — образы с сайта «Корвета» ({@code Abris.kdi}, {@code cpm80.kdi},
 * {@code Ep.kdi}, {@code Proffer.kdi} и д.): настоящего KDI-заголовка у них нет, зато первый
 * сектор объявляет раскладку диска — так называемый «Орион-стандарт». Его разбирает
 * {@link OrionImageReader}: из DPB на смещении 16 берутся размер блока, число записей
 * каталога и размер дорожки, а {@code OFS} указывает, где на самом деле лежит каталог.
 * Голосование каталогом остаётся только для линейных образов, которые о себе ничего
 * не сообщают.
 *
 * @author -=AVSh=-
 */
public final class DiskProfileFactory {
    /** Код пользователя CP/M — от 0 до 15; всё выше — уже не каталог, а данные. */
    private static final int MAX_USER_NUMBER = 15;
    /** Байт «свободно» в каталоге CP/M. */
    private static final int FREE_BYTE = 0xE5;
    /** Размер записи каталога в байтах. */
    private static final int DIR_ENTRY_SIZE = 32;
    /** Размер логической записи CP/M — по ней считается счётчик записей в экстенте. */
    private static final int RECORD_SIZE = 128;
    /**
     * Системных дорожек в записи B.01. Каталог CP/M начинается по смещению 20 480 файла,
     * то есть через 20 448 байт после 32-байтного загрузчика; три дорожки по 5120 дали бы
     * 15 360 байт, а каталог по 20 448 попадает на четвёртую — так же, как у
     * {@code SPECIALIST_MX_ODI_4SYS}, но с поправкой на то, что системная область профиля
     * отсчитывается от начала образа, а не файла.
     */
    private static final int B01_SYSTEM_TRACKS = 4;
    /**
     * Поправка к началу области данных записи B.01: каталог CP/M стоит по смещению 20 480
     * файла, то есть 20 448 от начала образа, — на 32 байта раньше конца четырёх системных
     * дорожек. Эти 32 байта и есть загрузчик «Корвета», который лежит в начале файла.
     */
    private static final int B01_DIR_OFFSET = -32;

    private static final List<DiskProfile> KNOWN_PROFILES;

    static {
        final List<DiskProfile> list = new ArrayList<>();

        // В списке только те раскладки, которые читаются образами, снятыми с настоящих
        // дисков, и закреплены в CpmConverterSelfTest. Порядок важен: профили одного размера
        // различаются только раскладкой, и при равной оценке каталога побеждает первый.
        // Первым идёт «Специалист MX» — тот формат, ради которого писан конвертер, и образ
        // этого размера скорее он, чем чужой.
        list.add(DiskProfile.SPECIALIST_MX_ODI_4SYS);

        // Специалист MX / Корвет 800 КБ — 2 системные дорожки, блок 2048
        list.add(DiskProfile.SPECIALIST_MX_800K);

        // Osborne 1 SSDD (200 КБ) — 40x1x5x1024, блок 2048 (osb1sssd у cpmtools:
        // BSH 4 BLM 15 DSM 91). При блоке 1024 два блока каталога занимали бы четыре
        // дорожки, и первые же блоки данных попадали бы в область каталога.
        list.add(new DiskProfile(
                "Osborne 1 SSDD (200 KB)", 40, 1, 5, 1024, 3, 2048, 64,
                new int[]{1, 3, 5, 2, 4}
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
     * Подбирает наиболее подходящий профиль диска по содержимому файла.
     *
     * <p>Файл читается один раз и проходит три проверки подряд: запись B.01 (см.
     * {@link KdiImageReader#isB01(byte[])}), заголовок контейнера KDI (см.
     * {@link KdiImageReader}), затем размер линейного образа с голосованием каталога.
     *
     * @param file файл образа диска
     * @return профиль, описывающий раскладку образа
     * @throws IOException если файл не читается или его размер не совпадает ни с одним форматом
     */
    public static DiskProfile detect(final File file) throws IOException {
        final byte[] image = Files.readAllBytes(file.toPath());
        final String name  = file.getName();

        if (KdiImageReader.isB01(image)) {
            return b01Profile(name);
        }

        final KdiImageReader.KdiHeader kdi = tryReadKdi(image, name);
        if (kdi != null) {
            return kdi.getProfile();
        }

        if (OrionImageReader.isOrionImage(image, name)) {
            return OrionImageReader.readProfile(image, name);
        }

        final List<DiskProfile> candidates = linearCandidates(image.length);
        if (candidates.isEmpty()) {
            throw new IOException(String.format(
                    "Неизвестный размер образа диска: %d байт. Поддерживаются: %s",
                    image.length, knownSizes()));
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
        final byte[] image = Files.readAllBytes(file.toPath());

        if (KdiImageReader.isB01(image)) {
            return Collections.singletonList(b01Profile(file.getName()));
        }

        final KdiImageReader.KdiHeader kdi = tryReadKdi(image, file.getName());
        if (kdi != null) {
            // Контейнер описывает раскладку сам, поэтому кандидат у него ровно один.
            return Collections.singletonList(kdi.getProfile());
        }

        if (OrionImageReader.isOrionImage(image, file.getName())) {
            return Collections.singletonList(OrionImageReader.readProfile(image, file.getName()));
        }
        return linearCandidates(image.length);
    }

    /**
     * Проверяет, что конвертер умеет открывать образ такого файла: по этому признаку GUI решает,
     * стоит ли вообще отдавать файл конвертеру.
     *
     * <p>Совпадения размера мало: у контейнера KDI файл на 256 байт длиннее линейного тела,
     * у записи B.01 — на 32 байта, а у линейного образа того же диска ровно {@code 819200}
     * байт. Поэтому служебные части проверяются отдельно, по своим признакам.
     *
     * @param file файл образа диска
     * @return true — размер или заголовок образа конвертеру известен
     * @throws IOException если файл не читается
     */
    public static boolean isKnownSize(final File file) throws IOException {
        return !candidates(file).isEmpty();
    }

    /**
     * Читает файлы с образа любого поддерживаемого формата.
     *
     * <p>Формат определяется по содержимому, тем же порядком, что и в {@link #detect(File)}:
     * запись B.01 разбирается по профилю «Специалиста» после отрезания 32 байт, контейнер KDI —
     * вместе со своим заголовком, линейный образ — по найденной раскладке.
     *
     * @param file файл образа диска
     * @return файлы в порядке появления их первых записей в каталоге
     * @throws IOException если формат не распознан или каталог не разбирается
     */
    public static List<CpmImageReader.CpmFile> readAllFiles(final File file) throws IOException {
        final byte[] image = Files.readAllBytes(file.toPath());
        final String name  = file.getName();

        if (KdiImageReader.isB01(image)) {
            return CpmImageReader.readAllFiles(b01ToLinear(image), b01Profile(name));
        }

        final KdiImageReader.KdiHeader kdi = tryReadKdi(image, name);
        if (kdi != null) {
            return CpmImageReader.readAllFiles(
                    KdiImageReader.toLinearImage(image, name), kdi.getProfile());
        }

        if (OrionImageReader.isOrionImage(image, name)) {
            return OrionImageReader.readAllFiles(image, name);
        }

        final List<DiskProfile> candidates = linearCandidates(image.length);
        if (candidates.isEmpty()) {
            throw new IOException(String.format(
                    "Неизвестный размер образа диска: %d байт. Поддерживаются: %s",
                    image.length, knownSizes()));
        }
        return CpmImageReader.readAllFiles(image, selectByDirectory(image, candidates));
    }

    /**
     * Строит профиль для записи B.01.
     *
     * <p>Запись B.01 — образ «Специалиста MX» с 32 байтами загрузчика «Корвета» в начале
     * файла. Загрузчик наложен поверх начала образа, а не добавлен перед ним: каталог CP/M
     * стоит в файле по смещению 20 480, то есть через четыре системные дорожки по 5120 байт
     * и на 32 байта раньше, чем кончается четырёхдорожечная системная область. Те самые
     * 32 байта и есть загрузчик — их и сдвигает {@link #b01ToLinear(byte[])}.
     *
     * <p>Раскладка — как у {@link DiskProfile#SPECIALIST_MX_ODI_4SYS} (80 цилиндров, две
     * стороны, пять секторов по 1024, блок 2048, 128 записей каталога, 16-битные указатели
     * блоков), только начало области данных сдвинуто на {@value #B01_DIR_OFFSET} байт.
     * Готовый профиль не переиспользуется: имя должно сказать в диалоге конвертера, что
     * образ пришёл из записи B.01, а не из {@code *.odi}.
     *
     * <p>Голосовать каталогом здесь нельзя: записи B.01 не описывают себя вообще, а каталог
     * другого формата того же размера читается по сдвинутой раскладке и даёт мусор вместо
     * имён файлов. Поэтому B.01 узнаётся по подписи и признаку в {@link KdiImageReader#isB01}.
     *
     * @param fileName имя файла образа
     * @return профиль раскладки образа «Специалиста MX» на 800 КБ
     */
    private static DiskProfile b01Profile(final String fileName) {
        return new DiskProfile("B.01 Specialist MX — " + fileName,
                80, 2, 5, 1024, B01_SYSTEM_TRACKS, 2048, 128, null, B01_DIR_OFFSET);
    }

    /**
     * Собирает линейный образ из записи B.01.
     *
     * <p>В файле записи B.01 первые 32 байта — загрузчик «Корвета», наложенный поверх начала
     * образа. Данные диска начинаются с байта 32 файла, поэтому образ сдвигается на 32 байта
     * назад: байты {@code [32..819199]} становятся байтами {@code [0..819167]}, длина образа
     * остаётся {@code 819168} байт.
     *
     * <p>После сдвига каталог CP/M оказывается на 20 448 — ровно там, куда его ставит
     * {@link DiskProfile#getDirOffset()} профиля записи B.01, а номера блоков в каталоге
     * отсчитываются уже от начала сдвинутого образа. Сами байты загрузчика копируются обратно
     * на места {@code 0..31}: они лежат внутри последней системной дорожки, на чтение каталога
     * и файлов не влияют, но с ними картинка диска в отладчике остаётся узнаваемой.
     *
     * @param image байты файла записи B.01
     * @return линейный образ «Специалиста MX» со сдвигом на 32 байта
     */
    private static byte[] b01ToLinear(final byte[] image) {
        final byte[] linear = Arrays.copyOfRange(image, KdiImageReader.B01_HEADER_SIZE,
                image.length);
        System.arraycopy(image, 0, linear, 0, KdiImageReader.B01_HEADER_SIZE);
        return linear;
    }

    /**
     * Пробует прочитать заголовок контейнера KDI.
     *
     * @param image байты файла образа
     * @param name  имя файла для сообщения об ошибке
     * @return заголовок контейнера или null, если файл не KDI
     */
    private static KdiImageReader.KdiHeader tryReadKdi(final byte[] image, final String name) {
        try {
            return KdiImageReader.readHeader(image, name);
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Возвращает профили, размер образа которых равен длине файла.
     *
     * @param fileSize длина файла в байтах
     * @return профили-кандидаты (пустой список, если размер ни с одним не совпал)
     */
    private static List<DiskProfile> linearCandidates(final int fileSize) {
        final List<DiskProfile> result = new ArrayList<>();
        for (final DiskProfile profile : KNOWN_PROFILES) {
            if (profile.getImageSize() == fileSize) {
                result.add(profile);
            }
        }
        return result;
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
        final byte[] dirArea = readDirArea(image, p);
        for (int i = 0; i < p.getDirEntries(); i++) {
            final int offset = i * DIR_ENTRY_SIZE;
            if ((offset < 0) || ((offset + DIR_ENTRY_SIZE) > dirArea.length)) {
                return -1000;
            }

            final int status = dirArea[offset] & 0xFF;
            if (firstStatus < 0) {
                firstStatus = status;
            }

            // 0xE5 — свободная запись. В начале области это пустой (или «стёртый») диск,
            // после заполненных — обычный конец каталога. В обоих случаях подсчёт прекращается.
            if (status == FREE_BYTE) {
                break;
            }

            // Код пользователя — от 0 до 15. Всё остальное означает, что по этому смещению
            // каталога нет: там чужие байты — у Osborne 1 за живыми записями идёт код
            // программы. Настоящий каталог свободные записи заполняет байтом 0xE5, а не
            // кодом, поэтому такая запись — признак чужой раскладки.
            if (status > MAX_USER_NUMBER) {
                badEntries++;
                break;
            }

            if (isGoodEntry(dirArea, offset, is16Bit, recordsPerBlock, maxBlocks)) {
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
            return ((firstStatus == FREE_BYTE) ? 0 : -50) + freeAreaScore(dirArea);
        }
        // Каталог согласован, но это может быть и каталог «Специалиста MX» с другим числом
        // системных дорожек: тот же блок 1024, те же указатели. Различают их только начало
        // диска (там загрузчик, а не данные) и наличие самих записей каталога по смещению.
        final int start = (isSystemArea(image, p) ? 20 : 0) + (hasDirectoryBytes(dirArea) ? 20 : 0);
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
     * Добавляет к оценке начало области каталога, если каталог начинается пометкой «свободно»
     * или нулём.
     *
     * Ноль в начале области данных встречается ещё реже, чем {@code 0xE5}, и означает
     * форматированный, но не использованный диск: так CP/M 2.2 готовит новый образ.
     *
     * @param dirArea записи каталога подряд
     * @return 1 — область начинается пометкой «свободно», 0 — нулём, -1 — ничего из этого
     */
    private static int freeAreaScore(final byte[] dirArea) {
        if (dirArea.length < DIR_ENTRY_SIZE) {
            return -1;
        }
        int free = 0;
        int erased = 0;
        for (int i = 0; (i < DIR_ENTRY_SIZE) && (free == 0); i++) {
            final int b = dirArea[i] & 0xFF;
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
     * Проверяет, что в области каталога есть хотя бы одна строка с именем файла.
     *
     * @param dirArea записи каталога подряд
     * @return true — в первой записи лежат печатные символы имени
     */
    private static boolean hasDirectoryBytes(final byte[] dirArea) {
        if ((dirArea.length < DIR_ENTRY_SIZE) || (dirArea[0] == 0) || (dirArea[0] == FREE_BYTE)) {
            return false;
        }

        for (int c = 1; c <= 8; c++) {
            final int ch = dirArea[c] & 0x7F;
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
        final int sysSize = p.getSystemAreaSize();
        if ((sysSize <= 0) || (sysSize > image.length) || (p.getReservedTracks() == 0)) {
            return false;
        }
        // Загрузчик начинается с перехода на свой код в самом начале диска (смещение 0).
        if ((image[0] & 0xFF) == 0xC3) {
            return true;
        }
        // Внутри системных дорожек (0 .. sysSize) ищем DPB с SPT и BLM.
        final int sectorsPerTrack = p.getSectorsPerTrack() * (p.getSectorSize() / RECORD_SIZE);
        final int blockMask = p.getBlockSize() / RECORD_SIZE - 1;
        for (int i = 0; i < sysSize - 16; i++) {
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

    /**
     * Собирает область каталога кандидата в логическом порядке записей (с учётом таблицы
     * перемежения).
     *
     * @param image байты образа диска
     * @param p     проверяемый профиль
     * @return записи каталога подряд; если образ кончается раньше, хвост остаётся {@code 0xE5}
     */
    private static byte[] readDirArea(final byte[] image, final DiskProfile p) {
        final int dirEntries = p.getDirEntries();
        final byte[] dirArea = new byte[dirEntries * DIR_ENTRY_SIZE];
        Arrays.fill(dirArea, (byte) 0xE5);

        final BlockLayout layout = new BlockLayout(p);
        for (int i = 0; i < dirEntries; i++) {
            final int off = layout.dirEntryOffset(i, DIR_ENTRY_SIZE);
            if ((off < 0) || ((off + DIR_ENTRY_SIZE) > image.length)) {
                break;
            }
            System.arraycopy(image, off, dirArea, i * DIR_ENTRY_SIZE, DIR_ENTRY_SIZE);
        }
        return dirArea;
    }
}
