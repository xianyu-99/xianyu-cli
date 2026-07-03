package com.yucli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserToolProviderTest {

    private BrowserToolProvider provider;
    private TestCdpSession mockSession;

    static class TestWebSocketClient extends CdpWebSocketClient {
        @Override
        public boolean isConnected() {
            return true; // 让 ensureSession 复用测试会话
        }
    }

    static class TestCdpSession extends CdpSession {
        boolean navigateCalled = false;
        String navigatedUrl = null;
        boolean navigateWaitForLoad = false;
        String currentUrl = "https://example.test/page";

        boolean clickCalled = false;
        String clickedSelector = null;

        boolean typeCalled = false;
        String typeSelector = null;
        String typeText = null;
        boolean typeSubmit = false;

        String evaluateScript = null;
        boolean failCleanDom = false;
        int cleanDomCalls = 0;
        int cleanDomMaxLength = -1;

        public TestCdpSession() {
            super(new TestWebSocketClient());
        }

        @Override
        public void navigate(String url, boolean waitForLoad) throws Exception {
            this.navigateCalled = true;
            this.navigatedUrl = url;
            this.navigateWaitForLoad = waitForLoad;
        }

        @Override
        public String getCurrentUrl() throws Exception {
            return currentUrl;
        }

        @Override
        public void click(String selector) throws Exception {
            this.clickCalled = true;
            this.clickedSelector = selector;
        }

        @Override
        public void type(String selector, String text, boolean submit) throws Exception {
            this.typeCalled = true;
            this.typeSelector = selector;
            this.typeText = text;
            this.typeSubmit = submit;
        }

        @Override
        public JsonNode evaluate(String expression) throws Exception {
            this.evaluateScript = expression;
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree("{\"result\":{\"type\":\"string\",\"value\":\"mock result\"}}");
        }

        @Override
        public String getCleanDom(int maxLength) throws Exception {
            this.cleanDomCalls++;
            this.cleanDomMaxLength = maxLength;
            if (failCleanDom) {
                throw new RuntimeException("dom unavailable");
            }
            return "<body>mock clean dom</body>";
        }
    }

    @BeforeEach
    void setUp() {
        provider = new BrowserToolProvider();
        mockSession = new TestCdpSession();
        provider.setSession(mockSession);
    }

    @Test
    void navigateStillSucceedsWhenDomSummaryFails() {
        mockSession.failCleanDom = true;

        String result = provider.navigate(Map.of("url", "https://example.test", "wait_for_load", "false"));

        assertTrue(mockSession.navigateCalled);
        assertEquals("https://example.test", mockSession.navigatedUrl);
        assertFalse(mockSession.navigateWaitForLoad);
        assertTrue(result.contains("已导航到: https://example.test/page"));
        assertTrue(result.contains("DOM 摘要获取失败"));
        assertFalse(result.contains("导航失败"));
    }

    @Test
    void testBrowserClick() {
        String result = provider.click(Map.of("selector", "#btn"));
        assertTrue(mockSession.clickCalled);
        assertEquals("#btn", mockSession.clickedSelector);
        assertTrue(result.contains("已点击元素: #btn"));
        assertTrue(result.contains("mock clean dom"));
        assertEquals(8000, mockSession.cleanDomMaxLength);
    }

    @Test
    void clickStillSucceedsWhenDomSummaryFails() {
        mockSession.failCleanDom = true;

        String result = provider.click(Map.of("selector", "#btn"));

        assertTrue(mockSession.clickCalled);
        assertTrue(result.contains("已点击元素: #btn"));
        assertTrue(result.contains("DOM 摘要获取失败"));
    }

    @Test
    void clickCanSkipDomSummaryWhenDisabled() {
        String result = provider.click(Map.of("selector", "#btn", "include_dom_summary", "false"));

        assertTrue(mockSession.clickCalled);
        assertEquals("#btn", mockSession.clickedSelector);
        assertTrue(result.contains("已点击元素: #btn"));
        assertFalse(result.contains("DOM 摘要"));
        assertEquals(0, mockSession.cleanDomCalls);
    }

    @Test
    void testBrowserType() {
        String result = provider.type(Map.of("selector", "#input", "text", "hello", "submit", "true"));
        assertTrue(mockSession.typeCalled);
        assertEquals("#input", mockSession.typeSelector);
        assertEquals("hello", mockSession.typeText);
        assertTrue(mockSession.typeSubmit);
        assertTrue(result.contains("已在 #input 中输入文本"));
        assertTrue(result.contains("并提交"));
        assertTrue(result.contains("mock clean dom"));
    }

    @Test
    void typeStillSucceedsWhenDomSummaryFails() {
        mockSession.failCleanDom = true;

        String result = provider.type(Map.of("selector", "#input", "text", "hello"));

        assertTrue(mockSession.typeCalled);
        assertEquals("#input", mockSession.typeSelector);
        assertTrue(result.contains("已在 #input 中输入文本"));
        assertTrue(result.contains("DOM 摘要获取失败"));
        assertFalse(result.contains("输入失败"));
    }

    @Test
    void typeForwardsDomSummaryMaxLength() {
        String result = provider.type(Map.of(
                "selector", "#input",
                "text", "hello",
                "dom_summary_max_length", "1200"
        ));

        assertTrue(mockSession.typeCalled);
        assertTrue(result.contains("mock clean dom"));
        assertEquals(1200, mockSession.cleanDomMaxLength);
    }

    @Test
    void domSummaryMaxLengthIsClamped() {
        provider.click(Map.of("selector", "#btn", "dom_summary_max_length", "999999"));

        assertEquals(20000, mockSession.cleanDomMaxLength);
    }

    @Test
    void testBrowserEvaluate() {
        String result = provider.evaluate(Map.of("script", "return 1 + 1;"));
        assertEquals("return 1 + 1;", mockSession.evaluateScript);
        assertTrue(result.contains("mock result"));
    }
}
