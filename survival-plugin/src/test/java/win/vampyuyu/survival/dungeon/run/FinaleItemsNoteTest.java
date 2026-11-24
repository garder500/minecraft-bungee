package win.vampyuyu.survival.dungeon.run;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The reward line says where the items went instead of always "in your inventory". */
public class FinaleItemsNoteTest {
    @Test
    public void saysWhereTheItemsWent() {
        assertEquals(" (no item this time)", Finale.itemsNote(0, 0));
        assertEquals(" (3 items in your inventory)", Finale.itemsNote(3, 0));
        assertEquals(" (1 item in your inventory)", Finale.itemsNote(1, 0));
        assertEquals(" (2 items wait: <yellow>/dungeon claim<gray>)", Finale.itemsNote(0, 2));
        assertEquals(" (1 item in your inventory, 1 item waits: <yellow>/dungeon claim<gray>)", Finale.itemsNote(1, 1));
    }
}
