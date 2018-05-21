package ru.avsh.specialist.mx.units.memory.sub;

import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.util.Arrays;
import java.util.Objects;

/**
 * Адресуемое запоминающее устройство "Простая память".
 *
 * @author -=AVSh=-
 */
public final class SimpleMemory implements MemoryUnit {
    private final int fStorageSize;
    private final byte[]      fRAM;

    /**
     * Конструктор.
     *
     * @param length длина
     */
    public SimpleMemory(int length) {
        fStorageSize =  length;
        fRAM = new byte[length];
    }

    @Override
    public int storageSize() {
        return fStorageSize;
    }

    @Override
    public int readByte(int address) {
        if (address >= 0 && address < fStorageSize) {
            return (int) fRAM[address] & 0xFF;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= 0 && address < fStorageSize) {
            fRAM[address] = (byte) value;
        }
    }

    @Override
    public void reset(boolean clear) {
        if (clear) {
            Arrays.fill(fRAM, (byte) 0);
        }
    }

    @Override
    public synchronized String toString() {
        return String.format("%s {Type = R/W; Size = %s}",
                getClass().getSimpleName(), Integer.toHexString(storageSize()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleMemory that = (SimpleMemory) o;
        return Objects.equals(this.fStorageSize, that.fStorageSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fStorageSize);
    }
}