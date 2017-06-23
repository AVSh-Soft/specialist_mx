package ru.avsh.specialistmx;

import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс "Speaker (динамик)".
 * @author -=AVSh=-
 */
final class Speaker {
    // Константы для разбивки на сэмплы
    private static final float SAMPLE_RATE            = 44100F;
    private static final float CYCLES_PER_SAMPLE      = ClockGenerator.CLOCK_SPEED / SAMPLE_RATE;
    private static final float HALF_CYCLES_PER_SAMPLE =           CYCLES_PER_SAMPLE /  2;
    // Константы для отбора полупериодов
    private static final int   BEG_HALF_CYCLE         = ClockGenerator.CLOCK_SPEED / 40; // Стартовая максимальная длина полупериода (частота от 20Гц)
    private static final int   MAX_HALF_CYCLE         = ClockGenerator.CLOCK_SPEED /  4; // Максимальная длина полупериода для воспроизведения пауз без искажений (от 2Гц)
    // Константы для задания времени наполнения буфера
    private static final int   BUF_TIME               = 100; // В миллисекундах
    private static final int   BUF_SAMPLES_TIME       =      Math.round(SAMPLE_RATE * BUF_TIME / 1000); // В семплах
    private static final int   BUF_CYCLES_TIME        = ClockGenerator.CLOCK_SPEED * BUF_TIME / 1000 ; // В тактах
    // Прочие константы
    private static final float SAMPLES_PER_MS         = SAMPLE_RATE / 1000;
    private static final int   SAMPLES_PER_2MS        = Math.round(2 * SAMPLES_PER_MS) ;
    private static final float AUDIO_LEVEL_FACTOR     = 128 / HALF_CYCLES_PER_SAMPLE * 0.25F; // Уровень громкости 25%
    private static final int   CPU_PULSE_TIME         = Math.round(ClockGenerator.TIME_OF_PULSE / 1_000_000F) * ClockGenerator.CLOCK_SPEED / 1000;

    private final Object fMutex;
    private final SourceDataLine fSDL;
    private final ClockGenerator fGen;
    private final SoundQueue fSoundQueue;
    private final SoundProcessor fSoundProcessor;

    private volatile long    fPrevTime;
    private volatile boolean fCurBit  ;
    private volatile boolean fCurBit8255;
    private volatile boolean fCurBit8253;

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Внутренний класс "Звуковая очередь".
     */
    private class SoundQueue extends ConcurrentLinkedQueue<Integer> {
        // Счетчик времени всех полупериодов в очереди
        private final AtomicInteger fTime = new AtomicInteger();

        @Override
        public boolean offer(Integer integer) {
            boolean result = super.offer(integer);
            if (result) {
                fTime.getAndAdd(integer);
            }
            return result;
        }

        @Override
        public Integer poll() {
            Integer integer = super.poll();
            if (integer != null) {
                fTime.getAndAdd(-integer);
            }
            return integer;
        }

        /**
         * Возвращает время всех полупериодов в очереди.
         * @return время всех полупериодов в очереди
         */
        int getTime() {
            return fTime.get();
        }
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Внутренний класс "Звуковой процессор".
     */
    private class SoundProcessor implements Runnable {
        private final int    fBufSize = fSDL.getBufferSize(); // Размера буфера в SDL хватает на воспроизведение в течении 1/2 сек
        private final byte[] fBuf     =   new byte[fBufSize]; // Выделяем буфер под сэмплы == буферу SDL

        private volatile boolean fRunning = false;
        private volatile boolean fBusy    = false;

        /**
         * Воспроизводит звук.
         * @param buf буфер, содержащий звуковые данные
         * @param len длина звуковых данных (нужно соблюдать условие len <= SDL.getBufferSize())
         */
        void playSound(byte[] buf, int len) {
            if (len > 0) {
                // Если запущен SDL:
                if (fSDL.isActive()) {
                    // - ждем пока в SDL освободится место под сэмплы
                    while (fSDL.available() < len) {
                        try {
                            Thread.sleep(1L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    // - отправляем сэмплы на воспроизведение
                    fSDL.write(buf, 0, len);
                } else {
                    // Иначе запускаем SDL и отправляем сэмплы на воспроизведение
                    fSDL.start();
                    fSDL.write(buf, 0, len);
                }
            }
        }

        /**
         * Показывает активен звуковой процессор или нет.
         * @return - true = звуковой процессор активен
         */
        boolean isRunning() {
            return fRunning;
        }

        /**
         * Показывает занят звуковой процессор или нет.
         * @return - true = звуковой процессор занят
         */
        boolean isBusy() {
            return fBusy;
        }

        @Override
        public void run() {
            int     samples  ;
            int     halfCycle;
            int     index    = 0 ;
            float   positive = 0F;
            float   negative = 0F;
            boolean bit      = false;
            long    samplesCounter = fSDL.getLongFramePosition();

            try {
                for (; ; ) {
                    // Если очередь пуста -
                    if (fSoundQueue.isEmpty()) {
                        // и если остались сэмплы в буфере -
                        if (index > 0) {
                            // выполняем воспроизведение оставшихся сэмплов
                            playSound(fBuf, index);
                            // увеличиваем счетчик сэмплов
                            samplesCounter += index;
                            index = 0;
                            continue;
                        }
                        // Вычисляем примерное количество невоспроизведенных сэмплов
                        samples = (int) (samplesCounter - fSDL.getLongFramePosition());
                        // Если воспроизведение завершилось - ждем звуковые данные
                        if (samples <= 0) {
                            // -= Обработка звука завершена =-
                            fRunning = false;
                            // Если SDL активен:
                            if (fSDL.isActive()) {
                                // - устанавливаем состояние "Звуковой процессор занят"
                                fBusy = true;
                                // - ожидаем полного завершения воспроизведения
                                fSDL.drain();
                                // - сбрасываем все данные в буфере SDL
                                fSDL.flush();
                                // - останавливаем SDL
                                fSDL.stop();
                                // - устанавливаем состояние "Звуковой процессор не занят"
                                fBusy = false;
                            }
                            // Если было прерывание потока - выходим из цикла
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }
                            // Переводим поток в ожидание
                            synchronized (fMutex) {
                                while (fSoundQueue.isEmpty()) {
                                    fMutex.wait();
                                }
                            }
                            // -= Обработка звука возобновлена =-
                            fRunning = true;
                            // Сбрасываем счетчики полупериодов
                            positive = negative = 0F;
                            // Читаем текущее значение счетчика сэмплов SDL
                            samplesCounter = fSDL.getLongFramePosition();
                            // Перед обработкой звуковых данных, заполняем очередь до значения BUF_CYCLES_TIME
                            for (long endTime = fGen.getCyclesCounter() + BUF_CYCLES_TIME - fSoundQueue.getTime(), timeLeft;
                                 (timeLeft = Math.min(endTime - fGen.getCyclesCounter(), BUF_CYCLES_TIME - fSoundQueue.getTime())) > 0; ) {
                                if (timeLeft >= CPU_PULSE_TIME) {
                                    Thread.sleep(1L);
                                }
                            }
                        } else {
                            // Иначе - отправляем поток спать на 1мс для освобождения ядра процессора от 100% загрузки
                            if (samples >= SAMPLES_PER_2MS) {
                                Thread.sleep(1L);
                            }
                        }
                    } else {
                        // Иначе, инвертируем бит для перехода к следующему звуковому полупериоду
                        bit = !bit;
                        // Если очередь не пуста - извлекаем полупериод из очереди
                        halfCycle = fSoundQueue.poll();
                        // Разбиваем полупериод из очереди на семплы
                        if (bit) {
                            positive += halfCycle;
                        } else {
                            negative += halfCycle;
                        }
                        for (; positive + negative >= CYCLES_PER_SAMPLE; index++) {
                            // Выполняем воспроизведение, если буфер заполнен сэмплами до значения BUF_SAMPLES_TIME (или до fBufSize, если BUF_SAMPLES_TIME > fBufSize)
                            if (index == Math.min(BUF_SAMPLES_TIME, fBufSize)) {
                                playSound(fBuf, index);
                                // увеличиваем счетчик сэмплов
                                samplesCounter += index;
                                index = 0;
                            }
                            // Амплитуду каждого сэмпла считаем как среднее арифметическое значений на участке CYCLES_PER_SAMPLE
                            if (bit) {
                                fBuf[index] = (byte) Math.round((HALF_CYCLES_PER_SAMPLE - negative) * AUDIO_LEVEL_FACTOR);
                                positive -= CYCLES_PER_SAMPLE - negative;
                                negative = 0F;
                            } else {
                                fBuf[index] = (byte) Math.round((positive - HALF_CYCLES_PER_SAMPLE) * AUDIO_LEVEL_FACTOR);
                                negative -= CYCLES_PER_SAMPLE - positive;
                                positive = 0F;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }
        }
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    /**
     * Конструктор.
     * @param  gen для связи Speaker с тактовым геренратором ClockGenerator
     * @throws LineUnavailableException if a matching source data line
     *         is not available due to resource restrictions
     */
    Speaker(@NotNull ClockGenerator gen) throws LineUnavailableException {
        // Устанавливаем ссылку на тактовый генератор
        fGen = gen;
        // Инициализируем и открываем SDL
        AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        fSDL = AudioSystem.getSourceDataLine(af);
        fSDL.open(af);
        // Инициализируем Mutex
        fMutex = new Object();
        // Инициализируем флаг текущий бит
        fCurBit = true;
        // Создаем очередь под звуковые полупериоды
        fSoundQueue = new SoundQueue();
        // Создаем звуковой процессор, который выполняет обработку очереди звуковых полупериодов (разбивку на сэмплы и воспроизведение)
        fSoundProcessor = new SoundProcessor();
        // Запускаем звуковой процессор
        new Thread(fSoundProcessor).start();
    }

    /**
     * Отправляет звуковые данные на воспроизведение.
     */
    private void play() {
        // Эмулируем соединение выходов ВВ55 и ВИ53
        boolean curBit = !(fCurBit8255 || fCurBit8253);
        if (fCurBit ^ curBit) {
            fCurBit = curBit;

            // Замеряем время звукового полупериода в тактах тактового генератора
            long halfCycle = -fPrevTime + (fPrevTime = fGen.getCyclesCounter());

            if (fSoundProcessor.isRunning()) {
                if (halfCycle <= MAX_HALF_CYCLE) {
                    fSoundQueue.offer((int) halfCycle);
                }
            } else {
                if (halfCycle <= BEG_HALF_CYCLE) {
                    // Ждем готовности звукового процессора
                    if (fSoundProcessor.isBusy()) {
                        for (int t = BUF_TIME; fSoundProcessor.isBusy() && (t > 0); t--) {
                            try {
                                Thread.sleep(1L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    fSoundQueue.offer((int) halfCycle);
                    // Запускаем звуковой процессор
                    synchronized (fMutex) {
                        fMutex.notifyAll();
                    }
                }
            }
        }
    }

    /**
     * Воспроизводит звуковые данные, поступающие из порта КР580ВВ55А.
     * @param bit бит, поступающий с вывода C5 порта ВВ55
     */
    void play8255(boolean bit) {
        if (fCurBit8255 ^ bit) {
            fCurBit8255 = bit;
            play();
        }
    }

    /**
     * Воспроизводит звуковые данные, поступающие из таймера КР580ВИ53.
     * @param bit бит, поступающий с выхода таймера ВИ53
     */
    void play8253(boolean bit) {
        if (fCurBit8253 ^ bit) {
            fCurBit8253 = bit;
            play();
        }
    }

    /**
     * Сбрасывает Speaker.
     */
    void reset() {
        // Очищаем очередь
        fSoundQueue.clear();
        // Сбрасываем сохраненное время
        fPrevTime = 0;
        // Устанавливаем выходной бит, согласно схеме ПК "Специалист MX"
        fCurBit = true;
        // Сбрасываем биты звуковых устройств
        fCurBit8255 = fCurBit8253 = false;
    }

    /**
     * Закрывает SDL.
     */
    void close() {
        // Очищаем очередь
        fSoundQueue.clear();
        // Ожидаем завершения работы звукового процессора
        if (fSoundProcessor.fRunning || fSoundProcessor.fBusy) {
            for (int t = BUF_TIME; (fSoundProcessor.fRunning || fSoundProcessor.fBusy) && (t > 0); t--) {
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        // Закрываем SDL
        fSDL.close();
    }
}