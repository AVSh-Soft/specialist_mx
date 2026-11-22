package ru.avsh.specialist.mx.helpers;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Надежный трекер временных интервалов.
 */
public class RobustIntervalTracker {
    private final long[] window;
    private        int  index = 0;
    private        int  count = 0;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Конструктор.
     *
     * @param windowSize сколько последних интервалов учитывать (например, 100 или 1000)
     */
    public RobustIntervalTracker(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be > 0");
        }
        this.window = new long[windowSize];
    }

    /**
     * Метод, который непрерывно принимает временные интервалы.
     *
     * @param timeInterval временной интервал
     */
    public void recordInterval(final long timeInterval) {
        lock.writeLock().lock();
        try {
            window[index] = timeInterval;
                   index  = (index + 1) % window.length;
            if (count < window.length) {
                count++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Метод для получения устойчивого усреднённого значения (медианы).
     *
     * @return медиана
     */
    public long getMedianInterval() {
        final long[] snapshot ;
        lock.readLock().lock();
        try {
            if (count == 0) {
                return 0L;
            }
            snapshot = Arrays.copyOf(window, count);
        } finally {
            lock.readLock().unlock();
        }

        Arrays.sort(snapshot);
        final int mid = snapshot.length / 2;
        return ((snapshot.length % 2) == 0)
                ? (long) ((snapshot[mid - 1] + snapshot[mid]) / 2.0)
                :          snapshot[mid];
    }

    /**
     * Метод для получения усеченного среднего.
     *
     * @param trimTopPercent отсечение, например, 0.10 для отсечения 10% выбросов
     * @return усеченное среднее
     */
    public long getTrimmedMean(final double trimTopPercent) {
        final long[] snapshot ;
        lock.readLock().lock();
        try {
            if (count == 0) {
                return 0L;
            }
            snapshot = Arrays.copyOf(window, count);
        } finally {
            lock.readLock().unlock();
        }

        Arrays.sort(snapshot);
        int limit = (int) (snapshot.length * (1.0 - trimTopPercent));
        if (limit == 0) {
            limit = 1;
        }

        long sum   = 0;
        for (int i = 0; i < limit; i++) {
            sum += snapshot[i];
        }
        return sum / limit;
    }
}

