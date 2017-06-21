package ru.avsh.specialistmx;

import java.util.Arrays;
import java.util.Objects;

/**
 * Устройство памяти "Оперативная память".
 * @author -=AVSh=-
 */
final class cMD_SimpleRAM implements MemoryDevice {
    private final int fLength;
    private final byte[] fRAM;

    cMD_SimpleRAM(int length) {
        fLength = length;
        fRAM = new byte[length];
    }

    @Override
    public int getMemoryDeviceLength() {
        return fLength;
    }

    @Override
    public int readByte(int address) {
        if (address >= 0 && address < fLength) {
            return (int) fRAM[address] & 0xFF;
        }
        return -1;
    }

    @Override
    public void writeByte(int address, int value) {
        if (address >= 0 && address < fLength) {
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
    synchronized public String toString() {
        return String.format("%s {Type = R/W; Length = %s}",
                getClass().getSimpleName(), Integer.toHexString(getMemoryDeviceLength()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        cMD_SimpleRAM that = (cMD_SimpleRAM) o;
        return Objects.equals(this.fLength, that.fLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fLength);
    }
}