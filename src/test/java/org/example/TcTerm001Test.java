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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TcTerm001Test {

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
    private Locator termYearsInput;
    private Locator termRemainderInput;
    private Locator monthsTermUnit;
    private Locator yearsTermUnit;
    private Locator monthsTermUnitControl;
    private Locator yearsTermUnitControl;
    private Locator termMonthsGroup;
    private Locator termYearsGroup;
    private Locator termError;
    private Locator results;
    private Locator summaryTerm;

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
    void openCalculator() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setChannel("chrome")
                .setHeadless(true));
        if (!isApplicationAvailable()) {
            startApplication();
        }
        page = browser.newPage();
        page.navigate(BASE_URL);
        initializeLocators();
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
        termYearsInput = page.locator("#term-years");
        termRemainderInput = page.locator("#term-remainder");
        monthsTermUnit = page.locator("input[name='term-unit'][value='months']");
        yearsTermUnit = page.locator("input[name='term-unit'][value='years']");
        monthsTermUnitControl = page.locator(
                "#term-fieldset label:has(input[name='term-unit'][value='months'])"
        );
        yearsTermUnitControl = page.locator(
                "#term-fieldset label:has(input[name='term-unit'][value='years'])"
        );
        termMonthsGroup = page.locator("#term-months-group");
        termYearsGroup = page.locator("#term-years-group");
        termError = page.locator("#term-error");
        results = page.locator("#results");
        summaryTerm = page.locator("#summary-term");
    }

    private void fillLoanParameters(String amount, String rate, String months) {
        amountInput.fill(amount);
        rateInput.fill(rate);
        termMonthsInput.fill(months);
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

    // region Тест TC-TERM-001

    @Test
    @DisplayName("TC-TERM-001 — сохранение точного срока при переключении месяцев и лет")
    void exactTermIsPreservedWhenSwitchingBetweenMonthsAndYears() {
        fillLoanParameters("100000", "12", "18");

        assertThat(results).isVisible();
        assertThat(summaryTerm).hasText("18 месяцев");
        assertThat(termError).isHidden();
        String initialResultText = results.innerText();

        yearsTermUnitControl.click();

        assertThat(yearsTermUnit).isChecked();
        assertThat(termMonthsGroup).isHidden();
        assertThat(termYearsGroup).isVisible();
        assertThat(termYearsInput).hasValue("1");
        assertThat(termRemainderInput).hasValue("6");
        assertThat(termError).isHidden();
        assertThat(results).isVisible();
        assertThat(summaryTerm).hasText("18 месяцев");
        assertEquals(initialResultText, results.innerText());

        monthsTermUnitControl.click();

        assertThat(monthsTermUnit).isChecked();
        assertThat(termYearsGroup).isHidden();
        assertThat(termMonthsGroup).isVisible();
        assertThat(termMonthsInput).hasValue("18");
        assertThat(termError).isHidden();
        assertThat(results).isVisible();
        assertThat(summaryTerm).hasText("18 месяцев");
        assertEquals(initialResultText, results.innerText());
    }

    // endregion
}
