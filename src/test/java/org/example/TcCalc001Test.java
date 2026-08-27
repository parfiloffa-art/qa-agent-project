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
class TcCalc001Test {

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
    private Locator annuityPaymentType;
    private Locator results;
    private Locator selectedSchemeTitle;
    private Locator summaryAmount;
    private Locator summaryRate;
    private Locator summaryTerm;
    private Locator monthlyPayment;
    private Locator annuityTotalPayment;
    private Locator annuityOverpayment;
    private Locator comparisonAnnuityCharacter;
    private Locator comparisonDifferentiatedCharacter;
    private Locator comparisonAnnuityTotal;
    private Locator comparisonDifferentiatedTotal;
    private Locator comparisonAnnuityOverpayment;
    private Locator comparisonDifferentiatedOverpayment;
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
        annuityPaymentType = page.locator("input[name='payment-type'][value='annuity']");
        results = page.locator("#results");
        selectedSchemeTitle = page.locator("#selected-scheme-title");
        summaryAmount = page.locator("#summary-amount");
        summaryRate = page.locator("#summary-rate");
        summaryTerm = page.locator("#summary-term");
        monthlyPayment = metricValue("Ежемесячный платёж");
        annuityTotalPayment = metricValue("Общая сумма выплат");
        annuityOverpayment = metricValue("Переплата");
        comparisonAnnuityCharacter = page.locator("#comparison-annuity-character");
        comparisonDifferentiatedCharacter = page.locator("#comparison-diff-character");
        comparisonAnnuityTotal = page.locator("#comparison-annuity-total");
        comparisonDifferentiatedTotal = page.locator("#comparison-diff-total");
        comparisonAnnuityOverpayment = page.locator("#comparison-annuity-overpayment");
        comparisonDifferentiatedOverpayment = page.locator("#comparison-diff-overpayment");
        differenceText = page.locator("#difference-text");
    }

    private Locator metricValue(String label) {
        return page.locator("#result-metrics .metric")
                .filter(new Locator.FilterOptions().setHasText(label))
                .locator("dd");
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

    // region Тест TC-CALC-001

    @Test
    @DisplayName("TC-CALC-001 — контрольный аннуитетный расчёт и сравнение схем")
    void controlAnnuityCalculationAndSchemeComparison() {
        assertThat(annuityPaymentType).isChecked();

        fillLoanParameters("100000", "12", "12");

        assertThat(results).isVisible();
        assertThat(selectedSchemeTitle).hasText("Аннуитетные платежи");
        assertThat(summaryAmount).hasText("100 000,00 ₽");
        assertThat(summaryRate).hasText("12%");
        assertThat(summaryTerm).hasText("12 месяцев");
        assertThat(monthlyPayment).hasText("8 884,88 ₽");
        assertThat(annuityTotalPayment).hasText("106 618,56 ₽");
        assertThat(annuityOverpayment).hasText("6 618,56 ₽");

        assertThat(comparisonAnnuityCharacter).containsText("8 884,88 ₽");
        assertThat(comparisonDifferentiatedCharacter).containsText("9 333,33 ₽");
        assertThat(comparisonDifferentiatedCharacter).containsText("8 416,70 ₽");
        assertThat(comparisonAnnuityTotal).hasText("106 618,56 ₽");
        assertThat(comparisonDifferentiatedTotal).hasText("106 500,00 ₽");
        assertThat(comparisonAnnuityOverpayment).hasText("6 618,56 ₽");
        assertThat(comparisonDifferentiatedOverpayment).hasText("6 500,00 ₽");
        assertThat(differenceText).containsText("118,56 ₽");
        assertThat(differenceText).containsText("Расчётная переплата меньше у дифференцированной схемы.");
        assertThat(differenceText).containsText("Это сравнение, а не финансовая рекомендация.");
    }

    // endregion
}
