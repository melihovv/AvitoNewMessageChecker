/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Alexander Melihov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package melihovv.AvitoNewMessageChecker;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Класс для проверки новых сообщений в личном кабинете avito.ru.
 */
public class AvitoNewMessageChecker {

    public static void main(String[] args) {
        final Logger log = LogManager.getLogger(
                AvitoNewMessageChecker.class.getName()
        );

        if (args.length != 1) {
            log.fatal("Не указан путь к config.yaml");
        }

        YamlReader reader = null;
        try {
            reader = new YamlReader(new FileReader(args[0]));
        } catch (FileNotFoundException e) {
            log.fatal("Не могу прочитать config.yaml");
            log.fatal(e.getMessage());
            System.exit(1);
        }

        Map config = null;
        try {
            Object temp = reader.read();
            config = (Map) temp;
        } catch (YamlException e) {
            log.fatal("Ошибки в синтаксисе config.yaml");
            log.fatal(e.getMessage());
            System.exit(1);
        }

        final List<String> keys = Arrays.asList(
                "avito_login",
                "avito_pass",
                "smtp_host",
                "smtp_port",
                "smtp_login",
                "smtp_pass",
                "email_from",
                "email_to"
        );

        for (final String key : keys) {
            if (!config.containsKey(key)) {
                String values = String.join(", ", keys);
                log.fatal("Какие-то из значений не заданы: " + values);
                System.exit(1);
            }
        }

        try {
            final WebDriver driver = new FirefoxDriver();
            final String baseUrl = "https://www.avito.ru/";
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            driver.get(baseUrl + "profile/login");
            driver.findElement(By.name("login")).clear();
            driver.findElement(By.name("login")).sendKeys(
                    config.get("avito_login").toString()
            );
            driver.findElement(By.name("password")).clear();
            driver.findElement(By.name("password")).sendKeys(
                    config.get("avito_pass").toString()
            );
            driver.findElement(By.xpath("//button[@type='submit']")).click();
            driver.findElement(
                    By.cssSelector(
                            "#sidebar-nav-messenger > " +
                                    "a.link.js-sidebar-menu-link"
                    )
            ).click();

            final boolean newMessagesPresent = isElementPresent(
                    driver,
                    By.className("is-design-new")
            );
            driver.get(baseUrl + "profile/exit");
            driver.quit();

            if (newMessagesPresent) {
                log.info("Есть непрочитанные сообщения");
                sendNotification(
                        config.get("smtp_host").toString(),
                        config.get("smtp_port").toString(),
                        config.get("smtp_login").toString(),
                        config.get("smtp_pass").toString(),
                        config.get("email_from").toString(),
                        config.get("email_to").toString(),
                        "New message on avito.ru",
                        "You have unread messages on avito.ru"
                );
            } else {
                log.info("Непрочитанных сообщений нет");
            }
        } catch (Exception e) {
            log.error("Что-то пошло не так");
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
            System.exit(1);
        }
    }

    /**
     * Отправляет email.
     *
     * @param host     Хост smtp сервера.
     * @param port     Порт smtp сервера.
     * @param login    Логин на smtp сервер.
     * @param pass     Пароль на smtp сервер.
     * @param sendFrom От кого отправлять.
     * @param sendTo   Кому отправлять.
     * @param subject  Тема сообщения.
     * @param message  Сообщение.
     * @throws MessagingException
     */
    private static void sendNotification(final String host,
                                         final String port,
                                         final String login,
                                         final String pass,
                                         final String sendFrom,
                                         final String sendTo,
                                         final String subject,
                                         final String message)
            throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.socketFactory.port", port);
        props.put(
                "mail.smtp.socketFactory.class",
                "javax.net.ssl.SSLSocketFactory"
        );
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", port);

        Session session = Session.getDefaultInstance(props,
                new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                login,
                                pass
                        );
                    }
                });

        Message m = new MimeMessage(session);
        m.setFrom(new InternetAddress(sendFrom));
        m.setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(sendTo)
        );
        m.setSubject(subject);
        m.setText(message);

        Transport.send(m);
    }

    /**
     * Возвращает истину, если элемент существует на странице, иначе - ложь.
     *
     * @param driver Веб движок.
     * @param by     Селектор элемента.
     * @return Истину, если элемент существует на странице, иначе - ложь.
     */
    private static boolean isElementPresent(WebDriver driver, By by) {
        try {
            driver.findElement(by);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }
}
