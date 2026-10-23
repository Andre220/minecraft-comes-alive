package net.conczin.mca.expansion;

import net.conczin.mca.MCA;
import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.pages.TextPage;
import net.conczin.mca.client.book.pages.TitlePage;
import net.conczin.mca.item.ExtendedWrittenBookItem;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;

/**
 * The guide book of the expansions: one chapter per system the expansions add to MCA. Texts are
 * {@code mca.books.expansion_guide.*} in {@code assets/mca_books/lang}; each new system adds a
 * chapter (or pages) here and there.
 */
public final class ExpansionGuide {
    private static final String NAME = "expansion_guide";
    /**
     * Pages per chapter, in order; chapter 0 is the introduction. Chapters: looks, radial menu,
     * needs, action queue, schedule, property, construction, gatherers, workshops, hauler, debugging,
     * smarter villagers, requests, village progression, other mods and windows.
     */
    private static final int[] PAGES = {2, 4, 2, 6, 2, 2, 6, 6, 6, 5, 3, 2, 7, 7, 4, 5};

    public static final Item BOOK = ItemsMCA.register("book_" + NAME,
            new ExtendedWrittenBookItem(ItemsMCA.baseProps("book_" + NAME), create()));

    private ExpansionGuide() {
    }

    /** Loads this class, so the book joins MCA's items. */
    public static void bootstrap() {
    }

    private static Book create() {
        // a background of MCA's own: the page turn arrows are drawn from the background texture
        Book book = new Book(NAME)
                .setBackground(MCA.locate("textures/gui/books/paper.png"))
                .addPage(new TitlePage(NAME));
        int page = 0;
        for (int chapter = 0; chapter < PAGES.length; chapter++) {
            if (chapter > 0) {
                String key = "mca.books." + NAME + ".chapter." + chapter;
                book.addPage(new TitlePage(key, key + ".subtitle", ChatFormatting.BLACK));
            }
            for (int i = 0; i < PAGES[chapter]; i++) {
                book.addPage(new TextPage(NAME, page++));
            }
        }
        return book;
    }
}
