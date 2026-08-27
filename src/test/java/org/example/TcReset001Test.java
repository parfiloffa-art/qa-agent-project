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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TcReset001Test {

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
    private Locator monthsTermUnit;
    private Locator yearsTermUnit;
    private Locator annuityPaymentType;
    private Locator differentiatedPaymentType;
    private Locator differentiatedPaymentOption;
    private Locator resetButton;
    private Locator results;
    private Locator scheduleToggle;
    private Locator schedule;
    private Locator amountError;
    private Locator rateError;
    private Locator termError;

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
        monthsTermUnit = page.locator("input[name='term-unit'][value='months']");
        yearsTermUnit = page.locator("input[name='term-unit'][value='years']");
        annuityPaymentType = page.locator("input[name='payment-type'][value='annuity']");
        differentiatedPaymentType = page.locator("input[name='payment-type'][value='differentiated']");
        differentiatedPaymentOption = page.locator(
                "label:has(input[name='payment-type'][value='differentiated'])"
        );
        resetButton = page.locator("#reset-button");
        results = page.locator("#results");
        scheduleToggle = page.locator("#schedule-toggle");
        schedule = page.locator("#schedule");
        amountError = page.locator("#amount-error");
        rateError = page.locator("#rate-error");
        termError = page.locator("#term-error");
    }

    private void prepareExpandedDifferentiatedCalculation() {
        amountInput.fill("100000");
        rateInput.fill("12");
        termMonthsInput.fill("12");
        differentiatedPaymentOption.click();
        scheduleToggle.click();

        assertThat(differentiatedPaymentType).isChecked();
        assertThat(results).isVisible();
        assertThat(schedule).isVisible();
        assertThat(resetButton).isVisible();
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

    // region Тест TC-RESET-001

    @Test
    @DisplayName("TC-RESET-001 — восстановление начального состояния кнопкой «Сбросить»")
    void resetButtonRestoresInitialState() {
        prepareExpandedDifferentiatedCalculation();

        resetButton.click();

        assertThat(amountInput).hasValue("");
        assertThat(rateInput).hasValue("");
        assertThat(termMonthsInput).hasValue("");
        assertThat(monthsTermUnit).isChecked();
        assertThat(yearsTermUnit).not().isChecked();
        assertThat(annuityPaymentType).isChecked();
        assertThat(differentiatedPaymentType).not().isChecked();

        assertThat(amountInput).hasAttribute("aria-invalid", "false");
        assertThat(rateInput).hasAttribute("aria-invalid", "false");
        assertThat(termMonthsInput).hasAttribute("aria-invalid", "false");
        assertThat(amountError).isHidden();
        assertThat(rateError).isHidden();
        assertThat(termError).isHidden();
        assertThat(results).isHidden();
        assertThat(schedule).isHidden();
        assertThat(resetButton).isHidden();
    }

    // endregion
}
