package com.yucli.cli;

import com.yucli.util.AnsiStyle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainBannerTest {

    @Test
    void bannerShowsCurrentProductNameAsPlainText() throws Exception {
        String banner = renderBanner();

        assertTrue(banner.contains("YuCLI"), "Banner must show the current product name clearly");
        assertFalse(banner.contains("PAICLI"), "Banner must not show the old product name");
    }

    @Test
    void bannerUsesLargeLightBlueBlockLogoStyle() throws Exception {
        String banner = renderBanner();

        assertTrue(banner.contains("██╗   ██╗██╗   ██╗"), "Banner should use a large block-letter logo");
        assertTrue(banner.contains("╚████╔╝"), "Banner should keep the tall block-letter shape");
        assertFalse(banner.contains("+----------------------------------------------------------+"),
                "Banner should no longer be the old small boxed style");
        assertTrue(banner.contains("/help 查看命令"), "Banner should include startup hints under the logo");
        if (AnsiStyle.isEnabled()) {
            assertTrue(banner.contains("\u001B[1;96m"), "Banner logo should use bright light-cyan ANSI styling");
        }
    }

    private static String renderBanner() throws Exception {
        Method printBanner = Main.class.getDeclaredMethod("printBanner");
        printBanner.setAccessible(true);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            printBanner.invoke(null);
        } finally {
            System.setOut(originalOut);
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void userSkillsDirectoryUsesCanonicalYuCliHome() {
        Path expected = Path.of(System.getProperty("user.home"), ".YuCLI", "skills");

        assertEquals(expected, Main.userSkillsDir());
    }
}
