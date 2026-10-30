package win.vampyuyu.survival.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MsgTest {
    @Test
    public void numbersAreCompact() {
        assertEquals("0", Msg.number(0));
        assertEquals("999", Msg.number(999));
        assertEquals("12.5", Msg.number(12.5));
        assertEquals("1k", Msg.number(1000));
        assertEquals("1.2k", Msg.number(1234));
        assertEquals("1.9k", Msg.number(1999));
        assertEquals("999.9k", Msg.number(999_999));
        assertEquals("1M", Msg.number(1_000_000));
        assertEquals("3.4M", Msg.number(3_456_789));
        assertEquals("1.5B", Msg.number(1_500_000_000L));
        assertEquals("2T", Msg.number(2e12));
        assertEquals("-1.2k", Msg.number(-1234));
        assertEquals("0", Msg.number(Double.NaN));
    }

    @Test
    public void durationsAreHumanReadable() {
        assertEquals("0s", Msg.duration(0));
        assertEquals("0s", Msg.duration(-5));
        assertEquals("1s", Msg.duration(1));
        assertEquals("45s", Msg.duration(45_000));
        assertEquals("1m 5s", Msg.duration(65_000));
        assertEquals("5m", Msg.duration(300_000));
        assertEquals("1h 2m", Msg.duration(3_723_000));
        assertEquals("1h", Msg.duration(3_605_000));
        assertEquals("2d 3h", Msg.duration(2 * 86_400_000L + 3 * 3_600_000L + 5000));
    }

    @Test
    public void coinsAreGrouped() {
        assertEquals("1,234,567 coins", Msg.coins(1_234_567.8));
        assertEquals("1 coin", Msg.coins(1));
        assertEquals("0 coins", Msg.coins(0));
        assertEquals("2.5k coins", Msg.coinsShort(2500));
    }
}
