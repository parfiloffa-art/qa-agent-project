package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TcVal001Test {

    // region Конфигурация и локаторы

    private static final String BASE_URL = "http://127.0.0.1:8765";
    private static final Duration SERVER_START_TIMEOUT = Duration.ofSeconds(10);

    private Process applicationServer;
    private Playwright playwright;
    private Browser browser;
    private Page page;

    private Locator amountInput;
    private Locator rateInput;
    private Locator termMonthsInput;
    private Locator amountError;
    private Locator results;
    private Locator selectedSchemeTitle;
    private Locator differenceText;

    // endregion

    // region Подготовка и завершение

    @BeforeAll
    void startApplication() throws Exception {
        if (isApplicationAvailable()) {
            return;
        }

        applicationServer = new ProcessBuilder(
                "python3", "-m", "http.server", "8765", "--bind", "127.0.0.1"
        )
                .directory(Path.of(System.getProperty("user.dir")).toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        long deadline = System.nanoTime() + SERVER_START_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isApplicationAvailable()) {
                return;
            }
            if (!applicationServer.isAlive()) {
                throw new IllegalStateException("Локальный сервер завершился до открытия приложения.");
            }
            Thread.sleep(100);
        }

        throw new IllegalStateException("Приложение не открылось за " + SERVER_START_TIMEOUT.toSeconds() + " секунд.");
    }

    @BeforeEach
    void openCalculator() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setChannel("chrome")
                .setHeadless(true));
        page = browser.newPage();
        page.navigate(BASE_URL);
        initializeLocators();
        fillValidPreconditions();
    }

    @AfterEach
    void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @AfterAll
    void stopApplication() {
        if (applicationServer != null) {
            applicationServer.destroy();
        }
    }

    // endregion

    // region Методы

    private void initializeLocators() {
        amountInput = page.locator("#amount");
        rateInput = page.locator("#rate");
        termMonthsInput = page.locator("#term-months");
        amountError = page.locator("#amount-error");
        results = page.locator("#results");
        selectedSchemeTitle = page.locator("#selected-scheme-title");
        differenceText = page.locator("#difference-text");
    }

    private void fillValidPreconditions() {
        rateInput.fill("12");
        termMonthsInput.fill("12");
    }

    private void enterAmount(String amount) {
        amountInput.fill(amount);
        amountInput.blur();
    }

    private void assertValidAmount(String amount, Pattern normalizedValue) {
        enterAmount(amount);

        assertThat(amountInput).hasValue(normalizedValue);
        assertThat(amountInput).hasAttribute("aria-invalid", "false");
        assertThat(amountError).isHidden();
        assertThat(results).isVisible();
        assertThat(selectedSchemeTitle).isVisible();
        assertThat(differenceText).isVisible();
    }

    private void assertInvalidAmount(String amount, String expectedError) {
        enterAmount(amount);

        assertThat(amountInput).hasAttribute("aria-invalid", "true");
        assertThat(amountError).isVisible();
        assertThat(amountError).hasText(expectedError);
        assertThat(results).isHidden();
        assertThat(selectedSchemeTitle).isHidden();
        assertThat(differenceText).isHidden();
    }

    private boolean isApplicationAvailable() {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(BASE_URL).toURL().openConnection();
            connection.setConnectTimeout(500);
            connection.setReadTimeout(500);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode >= 200 && responseCode < 400;
        } catch (IOException ignored) {
            return false;
        }
    }

    // endregion

    // region Тест TC-VAL-001

    @Test
    @DisplayName("TC-VAL-001 — границы суммы кредита")
    void loanAmountBoundariesAreValidated() {
        assertValidAmount("10000", Pattern.compile("10[ \\u00A0]000"));
        assertValidAmount("30000000", Pattern.compile("30[ \\u00A0]000[ \\u00A0]000"));
        assertInvalidAmount("9999", "Сумма должна быть от 10 000 до 30 000 000 ₽.");
        assertInvalidAmount("30000001", "Слишком смело. Максимум — 30 000 000 ₽.");
    }

    // endregion
}
