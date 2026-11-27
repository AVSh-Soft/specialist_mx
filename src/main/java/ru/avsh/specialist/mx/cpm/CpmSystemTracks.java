package ru.avsh.specialist.mx.cpm;

import ru.avsh.specialist.mx.helpers.Constants;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Системные дорожки загрузочного диска «Специалиста MX» — загрузчик, BDOS, BIOS и CBIOS.
 *
 * Байты системной области конвертера лежат в ресурсах: 4 дорожки по 5 секторов по 1024 байта,
 * ровно 20 480 байт от начала диска. Собраны из установщика CP/M {@code SGcpm1.4.i80}
 * (данные с 0x4EF — 9216 байт, хвост с 0x28EF — килобайт в 0x4C00): так эта программа
 * пишет диск сама, и результат совпадает с {@code CPM_TurboPascalZ80.odi} до байта.
 *
 * <p>Ресурс привязан к профилю {@link DiskProfile#SPECIALIST_MX_ODI_4SYS}: размер системной
 * области (20 480) должен совпадать с ним точно. Поэтому {@link #blankImage(DiskProfile)}
 * годится только для профилей с четырьмя системными дорожками по 5×1024 байта, и GUI
 * использует его единственно с этим профилем.
 *
 * @author -=AVSh=-
 */
public final class CpmSystemTracks {
    /** Файл с системными дорожками в ресурсах проекта. */
    public static final String RESOURCE = "spmx_cpm_sys.bin";
    /** Байт «свободно» на месте каталога и файлов CP/M. */
    private static final byte FREE_BYTE = (byte) 0xE5;

    /**
     * Конструктор.
     */
    private CpmSystemTracks() {
        //
    }

    /**
     * Читает системные дорожки из ресурсов.
     *
     * @return байты системной области (4 дорожки по 5x1024 байта)
     * @throws IOException если ресурс отсутствует или короче системной области диска
     */
    public static byte[] load() throws IOException {
        try (final InputStream stream = Constants.getResourceAsStream(RESOURCE).orElseThrow(
                  () -> new FileNotFoundException("в ресурсах нет " + RESOURCE))) {

            final byte[] tracks = new byte[DiskProfile.SPECIALIST_MX_ODI_4SYS.getSystemAreaSize()];
            int read = 0;
            while (read < tracks.length) {
                final int count = stream.read(tracks, read, tracks.length - read);
                if (count < 0) {
                    throw new IOException(String.format("%s короче системной области диска: %d из %d байт",
                            RESOURCE, read, tracks.length));
                }
                read += count;
            }
            return tracks;
        }
    }

    /**
     * Создаёт пустой загрузочный образ: системные дорожки из ресурсов и 0xE5 под каталог и файлы.
     *
     * Помимо копирования ресурса образ отличается от него только заполнением области данных
     * байтом «свободно»: каталог при этом не размечается, и первый {@link CpmImageReader}
     * увидит пустой диск — файлов на нём ноль.
     *
     * @param profile формат образа, у которого системная область того же размера, что в ресурсе
     * @return байты образа
     * @throws IOException если ресурс недоступен
     */
    public static byte[] blankImage(final DiskProfile profile) throws IOException {
        final byte[] systemTracks = load();
        if (profile.getSystemAreaSize() != systemTracks.length) {
            throw new IllegalArgumentException(String.format(
                    "формат %s: системная область %d байт вместо %d",
                    profile.getName(), profile.getSystemAreaSize(), systemTracks.length));
        }
        final byte[] image = new byte[profile.getImageSize()];
        System.arraycopy(systemTracks, 0, image, 0, systemTracks.length);
        Arrays.fill(image, systemTracks.length, image.length, FREE_BYTE);
        return image;
    }
}
