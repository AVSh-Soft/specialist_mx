package ru.avsh.specialist.mx.units;

import ru.avsh.specialist.mx.gui.swing.debugger.types.DebugRegPair;
import ru.avsh.specialist.mx.units.memory.MemoryManager;
import ru.avsh.specialist.mx.units.types.MemoryUnit;

import java.lang.reflect.Field;

/**
 * Офлайн-проверка реализации CpuZ80 (без JavaFX и без GUI).
 * Запускается вручную: java -cp <classes> ru.avsh.specialist.mx.units.CPUZ80SelfTest
 */
public final class CPUZ80SelfTest {

    /**
     * Плоская ОЗУ 64K.
     */
    private static final class FlatRam implements MemoryUnit {
        private final int[] fMem = new int[0x10000];

        @Override
        public int storageSize() {
            return fMem.length;
        }

        @Override
        public int readByte(final int address) {
            return fMem[address & 0xFFFF];
        }

        @Override
        public void writeByte(final int address, final int value) {
            fMem[address & 0xFFFF] = value & 0xFF;
        }

        @Override
        public boolean equals(final Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private static Field fRegsField;

    private static int reg(final CpuZ80 cpu, final int index) {
        try {
            if (fRegsField == null) {
                fRegsField = CpuZ80.class.getDeclaredField("fRegs");
                fRegsField.setAccessible(true);
            }
            return ((int[]) fRegsField.get(cpu))[index];
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int fFailed;
    private static int fPassed;

    private static void check(final String name, final int expected, final int actual) {
        if (expected == actual) {
            fPassed++;
        } else {
            fFailed++;
            System.out.printf("FAIL %-28s expected=%04X actual=%04X%n", name, expected, actual);
        }
    }

    private static void check(final String name, final boolean expected, final boolean actual) {
        if (expected == actual) {
            fPassed++;
        } else {
            fFailed++;
            System.out.printf("FAIL %-28s expected=%s actual=%s%n", name, expected, actual);
        }
    }

    public static void main(final String[] args) {
        final FlatRam ram = new FlatRam();
        final MemoryManager memManager = new MemoryManager();
        memManager.addMemoryUnit(0x0000, ram);

        final CpuZ80 cpu = new CpuZ80(null, memManager, null);
        cpu.setActive(true);

        // LD A,n / LD B,n / ADD A,B -> 0x12 + 0x34 = 0x46
        cpu.reset(0x0100, false);
        ram.writeByte(0x0100, 0x3E);
        ram.writeByte(0x0101, 0x12); // LD A,12
        ram.writeByte(0x0102, 0x06);
        ram.writeByte(0x0103, 0x34); // LD B,34
        ram.writeByte(0x0104, 0x80);                              // ADD A,B
        runCmd(cpu, 3);
        check("ADD A,B", 0x46, reg(cpu, 7));
        check("PC после ADD", 0x0105, reg(cpu, 9));

        // SUB -> флаг N и C
        cpu.reset(0x0200, false);
        ram.writeByte(0x0200, 0x3E);
        ram.writeByte(0x0201, 0x01); // LD A,01
        ram.writeByte(0x0202, 0xD6);
        ram.writeByte(0x0203, 0x02); // SUB 02
        runCmd(cpu, 2);
        check("SUB результат", 0xFF, reg(cpu, 7));
        check("SUB флаг C", true, (reg(cpu, 6) & 0x01) != 0);
        check("SUB флаг N", true, (reg(cpu, 6) & 0x02) != 0);

        // DEC A: 01 -> 00, флаги S/Z от результата
        cpu.reset(0x1900, false);
        ram.writeByte(0x1900, 0x3E);
        ram.writeByte(0x1901, 0x01); // LD A,01
        ram.writeByte(0x1902, 0x3D);                              // DEC A
        runCmd(cpu, 2);
        check("DEC A результат", 0x00, reg(cpu, 7));
        check("DEC A флаг Z", true, (reg(cpu, 6) & 0x40) != 0);
        check("DEC A флаг S", false, (reg(cpu, 6) & 0x80) != 0);
        check("DEC A флаг N", true, (reg(cpu, 6) & 0x02) != 0);
        check("DEC A флаг H", false, (reg(cpu, 6) & 0x10) != 0);

        // INC A: FF -> 00, флаг Z от результата
        cpu.reset(0x1A00, false);
        ram.writeByte(0x1A00, 0x3E);
        ram.writeByte(0x1A01, 0xFF); // LD A,FF
        ram.writeByte(0x1A02, 0x3C);                              // INC A
        runCmd(cpu, 2);
        check("INC A результат", 0x00, reg(cpu, 7));
        check("INC A флаг Z", true, (reg(cpu, 6) & 0x40) != 0);
        check("INC A флаг N", false, (reg(cpu, 6) & 0x02) != 0);

        // INC B: 7F -> 80, флаг S от результата + P/V переполнения
        cpu.reset(0x1B00, false);
        ram.writeByte(0x1B00, 0x06);
        ram.writeByte(0x1B01, 0x7F); // LD B,7F
        ram.writeByte(0x1B02, 0x04);                              // INC B
        runCmd(cpu, 2);
        check("INC B результат", 0x80, reg(cpu, 0));
        check("INC B флаг S", true, (reg(cpu, 6) & 0x80) != 0);
        check("INC B флаг P/V", true, (reg(cpu, 6) & 0x04) != 0);

        // DEC B: 80 -> 7F, флаг S сбрасывается + P/V переполнения
        cpu.reset(0x1C00, false);
        ram.writeByte(0x1C00, 0x06);
        ram.writeByte(0x1C01, 0x80); // LD B,80
        ram.writeByte(0x1C02, 0x05);                              // DEC B
        runCmd(cpu, 2);
        check("DEC B результат", 0x7F, reg(cpu, 0));
        check("DEC B флаг S", false, (reg(cpu, 6) & 0x80) != 0);
        check("DEC B флаг P/V", true, (reg(cpu, 6) & 0x04) != 0);

        // DEC A от 00 даёт FF: Z сброшен, S взведён
        cpu.reset(0x1D00, false);
        ram.writeByte(0x1D00, 0x3E);
        ram.writeByte(0x1D01, 0x00); // LD A,00
        ram.writeByte(0x1D02, 0x3D);                              // DEC A
        runCmd(cpu, 2);
        check("DEC A 00 результат", 0xFF, reg(cpu, 7));
        check("DEC A 00 флаг Z", false, (reg(cpu, 6) & 0x40) != 0);
        check("DEC A 00 флаг S", true, (reg(cpu, 6) & 0x80) != 0);

        // LD HL,nn / LD (nn),HL / LD DE,(nn)
        cpu.reset(0x0300, false);
        ram.writeByte(0x0300, 0x21);
        ram.writeByte(0x0301, 0x34);
        ram.writeByte(0x0302, 0x12); // LD HL,1234
        ram.writeByte(0x0303, 0x22);
        ram.writeByte(0x0304, 0x00);
        ram.writeByte(0x0305, 0x40); // LD (4000),HL
        runCmd(cpu, 2);
        check("LD (nn),HL lo", 0x34, ram.readByte(0x4000));
        check("LD (nn),HL hi", 0x12, ram.readByte(0x4001));

        // PUSH/POP
        cpu.reset(0x0400, false);
        ram.writeByte(0x0400, 0x31);
        ram.writeByte(0x0401, 0x00);
        ram.writeByte(0x0402, 0x50); // LD SP,5000
        ram.writeByte(0x0403, 0x21);
        ram.writeByte(0x0404, 0xCD);
        ram.writeByte(0x0405, 0xAB); // LD HL,ABCD
        ram.writeByte(0x0406, 0xE5);                                                          // PUSH HL
        ram.writeByte(0x0407, 0x21);
        ram.writeByte(0x0408, 0x00);
        ram.writeByte(0x0409, 0x00); // LD HL,0000
        ram.writeByte(0x040A, 0xE1);                                                          // POP HL
        runCmd(cpu, 5);
        check("PUSH/POP HL lo", 0xCD, reg(cpu, 5));
        check("PUSH/POP HL hi", 0xAB, reg(cpu, 4));
        check("SP восстановлен", 0x5000, reg(cpu, 8));

        // JR и цикл DJNZ
        cpu.reset(0x0500, false);
        ram.writeByte(0x0500, 0x06);
        ram.writeByte(0x0501, 0x03); // LD B,3
        ram.writeByte(0x0502, 0x10);
        ram.writeByte(0x0503, 0xFE); // DJNZ -2 (на себя)
        runCmd(cpu, 7);
        check("DJNZ обнулил B", 0x00, reg(cpu, 0));

        // LD IX,nn / LD A,(IX+1)
        cpu.reset(0x0600, false);
        ram.writeByte(0x0600, 0xDD);
        ram.writeByte(0x0601, 0x21);
        ram.writeByte(0x0602, 0x00);
        ram.writeByte(0x0603, 0x60); // LD IX,6000
        ram.writeByte(0x0604, 0xDD);
        ram.writeByte(0x0605, 0x7E);
        ram.writeByte(0x0606, 0x01); // LD A,(IX+1)
        ram.writeByte(0x6001, 0x5A);
        runCmd(cpu, 2);
        check("LD A,(IX+1)", 0x5A, reg(cpu, 7));

        // LD (IX+2),n
        cpu.reset(0x0700, false);
        ram.writeByte(0x0700, 0xDD);
        ram.writeByte(0x0701, 0x21);
        ram.writeByte(0x0702, 0x00);
        ram.writeByte(0x0703, 0x70); // LD IX,7000
        ram.writeByte(0x0704, 0xDD);
        ram.writeByte(0x0705, 0x36);
        ram.writeByte(0x0706, 0x02);
        ram.writeByte(0x0707, 0x77); // LD (IX+2),77
        runCmd(cpu, 2);
        check("LD (IX+2),n", 0x77, ram.readByte(0x7002));

        // CB: SLA B
        cpu.reset(0x0800, false);
        ram.writeByte(0x0800, 0x06);
        ram.writeByte(0x0801, 0x81); // LD B,81
        ram.writeByte(0x0802, 0xCB);
        ram.writeByte(0x0803, 0x20); // SLA B
        runCmd(cpu, 2);
        check("SLA B", 0x02, reg(cpu, 0));
        check("SLA флаг C", true, (reg(cpu, 6) & 0x01) != 0);

        // CB: BIT 7,A
        cpu.reset(0x0900, false);
        ram.writeByte(0x0900, 0x3E);
        ram.writeByte(0x0901, 0x80); // LD A,80
        ram.writeByte(0x0902, 0xCB);
        ram.writeByte(0x0903, 0x7F); // BIT 7,A
        runCmd(cpu, 2);
        check("BIT 7 флаг Z", false, (reg(cpu, 6) & 0x40) != 0);
        check("BIT 7 флаг S", true, (reg(cpu, 6) & 0x80) != 0);

        // ED: LDIR (копия 3 байт)
        cpu.reset(0x0A00, false);
        ram.writeByte(0x5000, 0x11);
        ram.writeByte(0x5001, 0x22);
        ram.writeByte(0x5002, 0x33);
        ram.writeByte(0x0A00, 0x21);
        ram.writeByte(0x0A01, 0x00);
        ram.writeByte(0x0A02, 0x50); // LD HL,5000
        ram.writeByte(0x0A03, 0x11);
        ram.writeByte(0x0A04, 0x00);
        ram.writeByte(0x0A05, 0x60); // LD DE,6000
        ram.writeByte(0x0A06, 0x01);
        ram.writeByte(0x0A07, 0x03);
        ram.writeByte(0x0A08, 0x00); // LD BC,0003
        ram.writeByte(0x0A09, 0xED);
        ram.writeByte(0x0A0A, 0xB0);                              // LDIR
        runCmd(cpu, 7);
        check("LDIR байт 0", 0x11, ram.readByte(0x6000));
        check("LDIR байт 1", 0x22, ram.readByte(0x6001));
        check("LDIR байт 2", 0x33, ram.readByte(0x6002));
        check("LDIR BC=0", 0x0000, (reg(cpu, 0) << 8) | reg(cpu, 1));

        // ED: NEG
        cpu.reset(0x0B00, false);
        ram.writeByte(0x0B00, 0x3E);
        ram.writeByte(0x0B01, 0x05); // LD A,05
        ram.writeByte(0x0B02, 0xED);
        ram.writeByte(0x0B03, 0x44); // NEG
        runCmd(cpu, 2);
        check("NEG 05", 0xFB, reg(cpu, 7));

        // DAA: 0x09 + 0x08 = 0x11
        cpu.reset(0x0C00, false);
        ram.writeByte(0x0C00, 0x3E);
        ram.writeByte(0x0C01, 0x09); // LD A,09
        ram.writeByte(0x0C02, 0xC6);
        ram.writeByte(0x0C03, 0x08); // ADD A,08
        ram.writeByte(0x0C04, 0x27);                              // DAA
        runCmd(cpu, 3);
        check("DAA 09+08", 0x17, reg(cpu, 7));

        // CALL/RET
        cpu.reset(0x0D00, false);
        ram.writeByte(0x0D00, 0x31);
        ram.writeByte(0x0D01, 0xFF);
        ram.writeByte(0x0D02, 0x7F); // LD SP,7FFF
        ram.writeByte(0x0D03, 0xCD);
        ram.writeByte(0x0D04, 0x10);
        ram.writeByte(0x0D05, 0x0D); // CALL 0D10
        ram.writeByte(0x0D10, 0xC9);                                                          // RET
        runCmd(cpu, 3);
        check("CALL/RET PC", 0x0D06, reg(cpu, 9));
        check("CALL/RET SP", 0x7FFF, reg(cpu, 8));

        // Недокументированный байт — NOP
        cpu.reset(0x0F00, false);
        ram.writeByte(0x0F00, 0xED);
        ram.writeByte(0x0F01, 0x00); // ED 00 -> NOP
        runCmd(cpu, 1);
        check("ED 00 PC", 0x0F02, reg(cpu, 9));

        // OUT (n),A и IN A,(n) при отсутствии io-менеджера: адрес 16-битный (A:n)
        cpu.reset(0x1000, false);
        ram.writeByte(0x1000, 0x3E);
        ram.writeByte(0x1001, 0xA5); // LD A,A5
        ram.writeByte(0x1002, 0xD3);
        ram.writeByte(0x1003, 0x20); // OUT (20),A -> адрес A:n = 0xA520
        runCmd(cpu, 2);
        check("OUT (n),A", 0xA5, ram.readByte(0xA520));

        // ED 79: OUT (C),A -> адрес B:C
        cpu.reset(0x1100, false);
        ram.writeByte(0x1100, 0x0E);
        ram.writeByte(0x1101, 0x30); // LD C,30
        ram.writeByte(0x1102, 0x3E);
        ram.writeByte(0x1103, 0x5F); // LD A,5F
        ram.writeByte(0x1104, 0xED);
        ram.writeByte(0x1105, 0x79); // OUT (C),A -> адрес 0x0030 (B=0)
        runCmd(cpu, 3);
        check("OUT (C),A", 0x5F, ram.readByte(0x0030));

        // ED 78: IN A,(C) читает по адресу B:C
        ram.writeByte(0x0040, 0x3C);
        cpu.reset(0x1200, false);
        ram.writeByte(0x1200, 0x0E);
        ram.writeByte(0x1201, 0x40); // LD C,40
        ram.writeByte(0x1202, 0xED);
        ram.writeByte(0x1203, 0x78); // IN A,(C)
        runCmd(cpu, 2);
        check("IN A,(C)", 0x3C, reg(cpu, 7));

        // ADD HL,BC: S, Z, P/V сохраняются, N сбрасывается, H от бита 11, C от бита 15
        cpu.reset(0x1300, false);
        ram.writeByte(0x1300, 0x21);
        ram.writeByte(0x1301, 0x34);
        ram.writeByte(0x1302, 0x12); // LD HL,1234
        ram.writeByte(0x1303, 0x01);
        ram.writeByte(0x1304, 0xFF);
        ram.writeByte(0x1305, 0x0F); // LD BC,0FFF
        ram.writeByte(0x1306, 0x09);                                                          // ADD HL,BC
        runCmd(cpu, 3);
        check("ADD HL,BC lo", 0x33, reg(cpu, 5));
        check("ADD HL,BC hi", 0x22, reg(cpu, 4));
        check("ADD HL,BC флаг H", true, (reg(cpu, 6) & 0x10) != 0);
        check("ADD HL,BC флаг C", false, (reg(cpu, 6) & 0x01) != 0);

        // ADD HL,BC с переносом из бита 15: FFFF + 0001 = 0000, H и C
        cpu.reset(0x1400, false);
        ram.writeByte(0x1400, 0x21);
        ram.writeByte(0x1401, 0xFF);
        ram.writeByte(0x1402, 0xFF); // LD HL,FFFF
        ram.writeByte(0x1403, 0x01);
        ram.writeByte(0x1404, 0x01);
        ram.writeByte(0x1405, 0x00); // LD BC,0001
        ram.writeByte(0x1406, 0x09);                                                          // ADD HL,BC
        runCmd(cpu, 3);
        check("ADD HL,BC wrap lo", 0x00, reg(cpu, 5));
        check("ADD HL,BC wrap hi", 0x00, reg(cpu, 4));
        check("ADD HL,BC wrap флаг C", true, (reg(cpu, 6) & 0x01) != 0);
        check("ADD HL,BC wrap флаг H", true, (reg(cpu, 6) & 0x10) != 0);

        // ADD HL,BC wrap при предустановленных S/Z/P/V: Z не взводится, хотя результат 0000
        cpu.reset(0x1800, false);
        ram.writeByte(0x1800, 0x31);
        ram.writeByte(0x1801, 0x00);
        ram.writeByte(0x1802, 0x90); // LD SP,9000
        ram.writeByte(0x9000, 0xC4);
        ram.writeByte(0x9001, 0x00);                             // на стеке F=C4 (S=1, Z=1, P/V=1)
        ram.writeByte(0x1803, 0xF1);                                                          // POP AF
        ram.writeByte(0x1804, 0x21);
        ram.writeByte(0x1805, 0xFF);
        ram.writeByte(0x1806, 0xFF); // LD HL,FFFF
        ram.writeByte(0x1807, 0x01);
        ram.writeByte(0x1808, 0x01);
        ram.writeByte(0x1809, 0x00); // LD BC,0001
        ram.writeByte(0x180A, 0x09);                                                          // ADD HL,BC
        runCmd(cpu, 5);
        check("ADD HL,BC wrap hi 2", 0x00, reg(cpu, 4));
        check("ADD HL,BC wrap флаг C 2", true, (reg(cpu, 6) & 0x01) != 0);
        check("ADD HL,BC wrap флаг H 2", true, (reg(cpu, 6) & 0x10) != 0);
        check("ADD HL,BC wrap S сохранён", true, (reg(cpu, 6) & 0x80) != 0);
        check("ADD HL,BC wrap Z сохранён", true, (reg(cpu, 6) & 0x40) != 0);
        check("ADD HL,BC wrap P/V сохранён", true, (reg(cpu, 6) & 0x04) != 0);
        check("ADD HL,BC wrap N сброшен", false, (reg(cpu, 6) & 0x02) != 0);

        // P/V не должен сбрасываться: LD A,I не реализован, поэтому задаём P/V через POP AF
        cpu.reset(0x1500, false);
        ram.writeByte(0x1500, 0x31);
        ram.writeByte(0x1501, 0x00);
        ram.writeByte(0x1502, 0x80); // LD SP,8000
        ram.writeByte(0x8000, 0x04);
        ram.writeByte(0x8001, 0x00);                             // на стеке F=04 (P/V=1)
        ram.writeByte(0x1503, 0xF1);                                                          // POP AF
        ram.writeByte(0x1504, 0x21);
        ram.writeByte(0x1505, 0x00);
        ram.writeByte(0x1506, 0x00); // LD HL,0000
        ram.writeByte(0x1507, 0x01);
        ram.writeByte(0x1508, 0x00);
        ram.writeByte(0x1509, 0x00); // LD BC,0000
        ram.writeByte(0x150A, 0x09);                                                          // ADD HL,BC
        runCmd(cpu, 4);
        check("ADD HL,BC P/V сохранён", true, (reg(cpu, 6) & 0x04) != 0);
        check("ADD HL,BC N сброшен", false, (reg(cpu, 6) & 0x02) != 0);

        // ADD IX,BC при индексном префиксе: меняется IX, HL не трогается
        cpu.reset(0x1600, false);
        ram.writeByte(0x1600, 0x21);
        ram.writeByte(0x1601, 0xAA);
        ram.writeByte(0x1602, 0xAA); // LD HL,AAAA
        ram.writeByte(0x1603, 0xDD);
        ram.writeByte(0x1604, 0x21);
        ram.writeByte(0x1605, 0x34);
        ram.writeByte(0x1606, 0x12); // LD IX,1234
        ram.writeByte(0x1607, 0x01);
        ram.writeByte(0x1608, 0xFF);
        ram.writeByte(0x1609, 0x0F); // LD BC,0FFF
        ram.writeByte(0x160A, 0xDD);
        ram.writeByte(0x160B, 0x09);                              // ADD IX,BC
        runCmd(cpu, 4);
        check("ADD IX,BC HL не изменён", 0xAAAA, (reg(cpu, 4) << 8) | reg(cpu, 5));
        check("ADD IX,BC флаг H", true, (reg(cpu, 6) & 0x10) != 0);

        // ADD IY,IY: индекс складывается сам с собой; 0x0FFF + 0x0FFF = 0x1FFE -> H=1
        cpu.reset(0x1700, false);
        ram.writeByte(0x1700, 0xFD);
        ram.writeByte(0x1701, 0x21);
        ram.writeByte(0x1702, 0xFF);
        ram.writeByte(0x1703, 0x0F); // LD IY,0FFF
        ram.writeByte(0x1704, 0xFD);
        ram.writeByte(0x1705, 0x29);                              // ADD IY,IY
        runCmd(cpu, 2);
        check("ADD IY,IY H от бита 11", true, (reg(cpu, 6) & 0x10) != 0);
        check("ADD IY,IY флаг C", false, (reg(cpu, 6) & 0x01) != 0);

        // DD CB 02 00: RLC (IX+02),B — результат и в память, и в регистр B
        cpu.reset(0x1800, false);
        ram.writeByte(0x1800, 0xDD);
        ram.writeByte(0x1801, 0x21);
        ram.writeByte(0x1802, 0x00);
        ram.writeByte(0x1803, 0x50); // LD IX,5000
        ram.writeByte(0x1804, 0xDD);
        ram.writeByte(0x1805, 0xCB);
        ram.writeByte(0x1806, 0x02);
        ram.writeByte(0x1807, 0x00); // RLC (IX+02),B
        ram.writeByte(0x5002, 0x81);
        runCmd(cpu, 2);
        check("DD CB RLC память", 0x03, ram.readByte(0x5002) & 0xFF);
        check("DD CB RLC регистр B", 0x03, reg(cpu, 0));
        check("DD CB RLC PC", 0x1808, reg(cpu, 9));

        // DD CB 02 40 / 02 46: BIT 0,(IX+02) при любом r меняет только флаги.
        // У BIT нет результата для записи, поэтому поле r не используется:
        // ни память, ни регистры не меняются, формы 0x40 и 0x46 ведут себя одинаково
        for (final int op : new int[] { 0x40, 0x46 }) {
            cpu.reset(0x1810, false);
            ram.writeByte(0x1810, 0xDD);
            ram.writeByte(0x1811, 0x21);
            ram.writeByte(0x1812, 0x00);
            ram.writeByte(0x1813, 0x50); // LD IX,5000
            ram.writeByte(0x1814, 0xDD);
            ram.writeByte(0x1815, 0xCB);
            ram.writeByte(0x1816, 0x02);
            ram.writeByte(0x1817, op); // BIT 0,(IX+02)
            ram.writeByte(0x5002, 0x80);
            ram.writeByte(0x5003, 0x80); // байт, лежащий сразу за ячейкой
            runCmd(cpu, 2);
            final String tag = String.format("DD CB 02 %02X", op);
            check(tag + " память не изменена", 0x80, ram.readByte(0x5002) & 0xFF);
            check(tag + " регистр B не изменён", 0x00, reg(cpu, 0));
            check(tag + " флаг Z", true, (reg(cpu, 6) & 0x40) != 0);
        }

        // EX AF,AF' меняет местами основные и альтернативные A и F
        cpu.reset(0x1E00, false);
        ram.writeByte(0x1E00, 0x3E);
        ram.writeByte(0x1E01, 0x11); // LD A,11
        ram.writeByte(0x1E02, 0x37);                              // SCF (F: C=1)
        ram.writeByte(0x1E03, 0x08);                              // EX AF,AF' (A'<=11, F'<=C=1)
        ram.writeByte(0x1E04, 0x3E);
        ram.writeByte(0x1E05, 0x22); // LD A,22
        runCmd(cpu, 4);
        check("EX AF,AF' A=22", 0x22, reg(cpu, 7));
        check("EX AF,AF' A'=11", 0x11, cpu.debugGetValRegPair(DebugRegPair.AF_ALT) >> 8);
        check("EX AF,AF' F' C=1", true, cpu.debugGetValRegPair(DebugRegPair.AF_ALT) == 0x1101);
        check("EX AF,AF' F сброшен", 0x00, reg(cpu, 6));
        cpu.reset(0x1E10, false);
        ram.writeByte(0x1E10, 0x3E);
        ram.writeByte(0x1E11, 0x11); // LD A,11
        ram.writeByte(0x1E12, 0x08);                              // EX AF,AF'
        ram.writeByte(0x1E13, 0x3E);
        ram.writeByte(0x1E14, 0x33); // LD A,33
        ram.writeByte(0x1E15, 0x08);                              // EX AF,AF'
        runCmd(cpu, 4);
        check("EX AF,AF' повторно A=11", 0x11, reg(cpu, 7));
        check("EX AF,AF' повторно A'=33", 0x33, cpu.debugGetValRegPair(DebugRegPair.AF_ALT) >> 8);

        // EXX меняет местами BC, DE, HL с их альтернативными парами
        cpu.reset(0x1F00, false);
        ram.writeByte(0x1F00, 0x01);
        ram.writeByte(0x1F01, 0x34);
        ram.writeByte(0x1F02, 0x12); // LD BC,1234
        ram.writeByte(0x1F03, 0x11);
        ram.writeByte(0x1F04, 0x78);
        ram.writeByte(0x1F05, 0x56); // LD DE,5678
        ram.writeByte(0x1F06, 0x21);
        ram.writeByte(0x1F07, 0xBC);
        ram.writeByte(0x1F08, 0x9A); // LD HL,9ABC
        ram.writeByte(0x1F09, 0xD9);                                                          // EXX
        ram.writeByte(0x1F0A, 0x01);
        ram.writeByte(0x1F0B, 0xCD);
        ram.writeByte(0x1F0C, 0xAB); // LD BC,ABCD
        runCmd(cpu, 5);
        check("EXX BC=ABCD", 0xABCD, (reg(cpu, 0) << 8) | reg(cpu, 1));
        check("EXX BC'=1234", 0x1234, cpu.debugGetValRegPair(DebugRegPair.BC_ALT));
        check("EXX DE'=5678", 0x5678, cpu.debugGetValRegPair(DebugRegPair.DE_ALT));
        check("EXX HL'=9ABC", 0x9ABC, cpu.debugGetValRegPair(DebugRegPair.HL_ALT));
        check("EXX DE=0 после обмена", 0x0000, (reg(cpu, 2) << 8) | reg(cpu, 3));
        ram.writeByte(0x1F0D, 0xD9); // EXX
        runCmd(cpu, 1);
        check("EXX обратно BC=1234", 0x1234, (reg(cpu, 0) << 8) | reg(cpu, 1));
        check("EXX обратно BC'=ABCD", 0xABCD, cpu.debugGetValRegPair(DebugRegPair.BC_ALT));

        // Отладочный доступ: запись в альтернативные пары и сброс по reset
        cpu.debugSetValRegPair(DebugRegPair.BC_ALT, 0x1357);
        cpu.debugSetValRegPair(DebugRegPair.DE_ALT, 0x2468);
        cpu.debugSetValRegPair(DebugRegPair.HL_ALT, 0x0ACE);
        check("debug BC'", 0x1357, cpu.debugGetValRegPair(DebugRegPair.BC_ALT));
        check("debug DE'", 0x2468, cpu.debugGetValRegPair(DebugRegPair.DE_ALT));
        check("debug HL'", 0x0ACE, cpu.debugGetValRegPair(DebugRegPair.HL_ALT));
        cpu.reset(0x1E20, false);
        check("reset очищает BC'", 0, cpu.debugGetValRegPair(DebugRegPair.BC_ALT));
        check("reset очищает AF'", 0, cpu.debugGetValRegPair(DebugRegPair.AF_ALT));

        // Перенос общих регистров между процессорами (переключение CPU)
        final CpuZ80 srcZ80 = cpu;
        final MemoryManager mmI80 = new MemoryManager();
        mmI80.addMemoryUnit(0x0000, new FlatRam());
        final CpuI8080 dstI80 = new CpuI8080(null, mmI80, null);

        srcZ80.setCommonRegPair(0, 0x1234); // BC
        srcZ80.setCommonRegPair(2, 0x5678); // DE
        srcZ80.setCommonRegPair(4, 0x9ABC); // HL
        srcZ80.setCommonRegPair(6, 0xD6EF); // AF
        srcZ80.setCommonRegPair(8, 0xF001); // SP
        srcZ80.setCommonRegPair(9, 0xC123); // PC
        final int[] pairs = {0, 2, 4, 6, 8, 9};
        for (final int pair : pairs) {
            dstI80.setCommonRegPair(pair, srcZ80.getCommonRegPair(pair));
        }
        check("Z80->i8080 BC", 0x1234, dstI80.getCommonRegPair(0));
        check("Z80->i8080 DE", 0x5678, dstI80.getCommonRegPair(2));
        check("Z80->i8080 HL", 0x9ABC, dstI80.getCommonRegPair(4));
        check("Z80->i8080 AF", 0xD6C7, dstI80.getCommonRegPair(6));
        check("Z80->i8080 SP", 0xF001, dstI80.getCommonRegPair(8));
        check("Z80->i8080 PC", 0xC123, dstI80.getCommonRegPair(9));

        // Обратный перенос: i8080 маскирует F (биты 5/3/1 у него не флаги),
        // поэтому Z80 получает F без этих бит — SZ0A0P1C
        dstI80.setCommonRegPair(6, 0x00FF);
        srcZ80.setCommonRegPair(6, dstI80.getCommonRegPair(6));
        check("i8080->Z80 AF", 0x00D7, srcZ80.getCommonRegPair(6));
        check("i8080->Z80 F маскируется i8080", 0xD7, reg(cpu, 6));

        System.out.printf("%nИТОГ: passed=%d failed=%d%n", fPassed, fFailed);
        if (fFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Выполняет указанное количество команд.
     */
    private static void runCmd(final CpuZ80 cpu, final int count) {
        for (int cmd = 0; cmd < count; cmd++) {
            for (int i = 0; i < 200; i++) {
                if (cpu.cycle()) {
                    break;
                }
            }
        }
    }
}
