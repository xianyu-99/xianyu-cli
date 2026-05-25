package com.yucli.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainBannerTest {

    @Test
    void bannerShowsCurrentProductNameAsPlainText() throws Exception {
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

        String banner = out.toString(StandardCharsets.UTF_8);
        assertTrue(banner.contains("YuCLI"), "Banner must show the current product name clearly");
        assertFalse(banner.contains("PAICLI"), "Banner must not show the old product name");
    }
}
