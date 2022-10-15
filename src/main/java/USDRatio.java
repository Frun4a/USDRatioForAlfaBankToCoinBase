import com.google.gson.JsonParser;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public class USDRatio {

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriverMac");

        int AMOUNT = 2000;
        int COINBASE_FEE = 30;
        double TRANSFER_FEE = 0.005;

        var alfaExchangeRate = getAlfaExchangeRate();
        var binanceExchangeRate = getAverageBinanceExchangeRate();

        var amountToTransferInRub = AMOUNT * alfaExchangeRate * (1 - TRANSFER_FEE);
        var receiveAmountInUSD = (amountToTransferInRub / binanceExchangeRate) - COINBASE_FEE;

        var efficiencyRatio = receiveAmountInUSD / AMOUNT;
        var loseRatio = String.format("%.2f%%", 100 - (efficiencyRatio * 100));

        System.out.println("-".repeat(50));
        System.out.println("Alfa-Bank USD/RUB Exchange Rate is: " + alfaExchangeRate + " ₽");
        System.out.println("Binance P2P Average USDT Price is: " + String.format("%.2f", binanceExchangeRate) + " ₽");
        System.out.println("You will lose " + loseRatio + " of money on your transfer");
        System.out.println("You will get $" + String.format("%.2f", AMOUNT * efficiencyRatio) + " from your $" + AMOUNT);
        System.out.println("-".repeat(50));
    }

    private static double getAlfaExchangeRate() throws InterruptedException, URISyntaxException, IOException {
        String API_URL = "https://alfabank.ru/api/v1/scrooge/currencies/alfa-rates";

        var requestURL = new URIBuilder(API_URL)
                .addParameter("date.lt", OffsetDateTime.now().minusSeconds(10).toString())
                .addParameter("currencyCode.eq", "USD")
                .addParameter("clientType.eq", "standardCC")
                .addParameter("lastActualForDate.eq", "true")
                .addParameter("rateType.in", "makeCash")
                .build();

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(requestURL)
                .header("accept", "application/json")
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var jsonObject = new JsonParser().parse(response.body()).getAsJsonObject();
        return jsonObject
                .get("data").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("rateByClientType").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("ratesByType").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("lastActualRate").getAsJsonObject()
                .get("buy").getAsJsonObject()
                .get("originalValue").getAsDouble();
    }

    private static double getAverageBinanceExchangeRate() {
        By closeBannerButtonLocator = By.xpath("//*[local-name()='svg']/*[local-name()='path'" +
                " and @d='M10.586 12L4.293 5.707 5 5l.707-.707L12 10.586l6.293-6.293L19 5l.707.707L13.414 12l6.293 " +
                "6.293-1.414 1.414L12 13.414l-6.293 6.293L5 19l-.707-.707L10.586 12z']/..");
        By adOkButtonLocator = By.xpath("//button[text()='Ok']");

        By fiatSelectorLocator = By.xpath("//div[@id='C2Cfiatfilter_searhbox_fiat']");
        By fiatInputLocator = By.xpath("//div[@id='C2Cfiatfilter_searhbox_fiat']" +
                "//div[@class='bn-sdd-innerInputContainer css-16vu25q']//input");
        By rubButtonLocator = By.xpath("//div[@id='C2Cfiatfilter_searhbox_fiat']//div[contains(@class,'bn-sdd-option')]" +
                "//div[text()='RUB']");

        By paymentMethodSelectorLocator = By.xpath("//div[@id='C2Cpaymentfilter_searchbox_payment']");
        By tinkoffPaymentOptionLocator = By.xpath("//ul//li[@id='Тинькофф']");
        By rateValuesLocator = By.xpath("//button[.='Купить USDT']/../../../div[2]/div/div/div[1]");

        ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);
        WebDriver driver = new ChromeDriver(options);
        var wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.get("https://p2p.binance.com/ru/trade/TinkoffNew/USDT?fiat=RUB");
        wait.until(ExpectedConditions.presenceOfElementLocated(closeBannerButtonLocator)).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(adOkButtonLocator)).click();

        driver.findElement(fiatSelectorLocator).click();
        driver.findElement(fiatInputLocator).sendKeys("RUB");
        driver.findElement(rubButtonLocator).click();

        driver.findElement(paymentMethodSelectorLocator).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(tinkoffPaymentOptionLocator)).click();

        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(rateValuesLocator, 8));
        List<WebElement> exchangeRates = driver.findElements(rateValuesLocator);

        var exchangeRatesAsDoubles = exchangeRates.stream().map(el -> Double.valueOf(el.getText())).toList();

        driver.quit();

        return exchangeRatesAsDoubles.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
    }

}