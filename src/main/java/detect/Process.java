package detect;

import detect.object.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

import detect.ver2.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.print.Doc;

public class Process {

    public static Pair<String, List<Action>> parseJson2(JSONObject object) {
        String url = "";
        List<Action> list = new ArrayList<>();
        JSONParser parser = new JSONParser();



        JSONObject jsonObject =  object;

        url = (String) jsonObject.get("url");

        JSONArray actions = (JSONArray) jsonObject.get("actions");
        for (Object o : actions) {
            JSONObject action = (JSONObject) o;
            String type = (String) action.get("type");
            if (type.equals("input")) {
                String value = (String) action.get("value");
                String locator = (String) action.get("locator");
                Action act = new InputAction(value, locator);
                list.add(act);
            }
            if (type.equals("click")) {
                String locator = (String) action.get("locator");
                Action act = new ClickAction(locator);
                list.add(act);
            }
            if (type.equals("select")) {
                String question = (String) action.get("question");
                String choice = (String) action.get("choice");
                Action act = new SelectAction(question, choice);
                list.add(act);
            }
            if (type.equals("checkbox")) {
                String choice = (String) action.get("describedLocator");
                Action act = new ClickCheckboxAction(choice);
                list.add(act);
            }
            if (type.equals("hover")) {
                String locator = (String) action.get("describedLocator");
                Action act = new HoverAction(locator);
                list.add(act);
            }
            if (type.equals("verifyUrl")) {
                String expectedUrl = (String) action.get("url");
                Action act = new AssertURL(expectedUrl);
                list.add(act);
            }
        }

        return new Pair<>(url, list);
    }
    public static Pair<String, List<Action>> parseJson(String pathToJson) {
        String url = "";
        List<Action> list = new ArrayList<>();
        JSONParser parser = new JSONParser();

        try {
            Object obj = parser.parse(new FileReader(pathToJson));

            JSONObject jsonObject =  (JSONObject) obj;

            url = (String) jsonObject.get("url");

            JSONArray actions = (JSONArray) jsonObject.get("actions");
            for (Object o : actions) {
                JSONObject action = (JSONObject) o;
                String type = (String) action.get("type");
                if (type.equals("input")) {
                    String value = (String) action.get("value");
                    String locator = (String) action.get("locator");
                    Action act = new InputAction(value, locator);
                    list.add(act);
                }
                if (type.equals("click")) {
                    String locator = (String) action.get("locator");
                    Action act = new ClickAction(locator);
                    list.add(act);
                }
                if (type.equals("select")) {
                    String question = (String) action.get("question");
                    String choice = (String) action.get("choice");
                    Action act = new SelectAction(question, choice);
                    list.add(act);
                }
                if (type.equals("checkbox")) {
                    String choice = (String) action.get("choice");
                    Action act = new ClickCheckboxAction(choice);
                    list.add(act);
                }
                if (type.equals("hover")) {
                    String locator = (String) action.get("locator");
                    Action act = new HoverAction(locator);
                    list.add(act);
                }
                if (type.equals("assertUrl")) {
                    String expectedUrl = (String) action.get("expectedUrl");
                    Action act = new AssertURL(expectedUrl);
                    list.add(act);
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return new Pair<>(url, list);
    }

    public static List<Action> detectLocators(List<Action> list, String url) {
        String htmlContent = getHtmlContent(url);
        Element previousElement = null;
        Document document = getDomTree(htmlContent);
        Elements inputElements = HandleInput.getInputElements(document);
        Elements selectElements = HandleSelect.getSelectElements(document);
        Elements clickableElements = HandleClick.getClickableElements(document);
        boolean isAfterHoverAction = false;
        List<Action> visited = new ArrayList<>();
        Map<String, List<Action>> map = new HashMap<>(); //saves actions that needed to  be currently detected
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof AssertURL) {
                if (i != list.size() - 1) {
                    WebDriver driver = new ChromeDriver();
                    driver.get(url);
                    Action.runActions(visited, driver);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    String pageSource = driver.getPageSource();
                    document = getDomTree(pageSource);
                    inputElements = HandleInput.getInputElements(document);
                    selectElements = HandleSelect.getSelectElements(document);
                    clickableElements = HandleClick.getClickableElements(document);
                    driver.quit();
                }
                visited.add(list.get(i));
                isAfterHoverAction = false;
                previousElement = null;
            } else {
                if (list.get(i) instanceof InputAction) {
                    String text_locator = list.get(i).getText_locator();
                    List<String> input = Arrays.asList(text_locator);
                    Map<String, List<Element>> res = InputElement.detectInputElement(input, inputElements, isAfterHoverAction);

                    List<Element> elements = res.get(text_locator);
                    List<Element> elementList = new ArrayList<>();
                    WebDriver driver = new ChromeDriver();
                    driver.get(url);
                    Action.runActions(visited, driver);
                    for (Element e : elements) {
                        String xpath = Process.getAbsoluteXpath(e, "");
                        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                        if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                            elementList.add(e);
                        }
                    }
                    driver.quit();
                    if (elementList.size() == 1) {
                        Element e = elementList.get(0);
//                        String locator = Process.getXpath(e);
                        String locator = Process.getAbsoluteXpath(e, "");
                        System.out.println(locator);
                        list.get(i).setDom_locator(locator);
                        previousElement = e;
                    } else {
                        if (previousElement != null) {
                            Element e = HandleElement.findNearestElementWithSpecifiedElement(previousElement, elementList);
                            String locator = Process.getAbsoluteXpath(e, "");
                            System.out.println(locator);
//                        String locator = Process.getXpath(e);
                            list.get(i).setDom_locator(locator);
                            previousElement = e;
                        } else {
                            Element e = HandleElement.findNearestElementWithSpecifiedElement(document.body(), elementList);
                            String locator = Process.getAbsoluteXpath(e, "");
                            System.out.println(locator);
//                        String locator = Process.getXpath(e);
                            list.get(i).setDom_locator(locator);
                            previousElement = e;
                        }

                    }
                    isAfterHoverAction = false;
                }
                if (list.get(i) instanceof ClickAction) {
                    String text_locator = list.get(i).getText_locator();
                    List<String> input = Arrays.asList(text_locator);
                    Map<String, List<Element>> res = Click.detectClickElement(input, clickableElements, isAfterHoverAction);
                    isAfterHoverAction = false;
                    List<Element> elements = res.get(text_locator);
                    List<Element> elementList = new ArrayList<>();
                    WebDriver driver = new ChromeDriver();
                    driver.get(url);
                    Action.runActions(visited, driver);
                    for (Element e : elements) {
                        String xpath = Process.getAbsoluteXpath(e, "");
                        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                        if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                            elementList.add(e);
                        }
                    }
                    driver.quit();
                    if (elementList.size() == 1) {
                        Element e = elementList.get(0);
//                        String locator = Process.getXpath(e);
                        String locator = Process.getAbsoluteXpath(e, "");
                        System.out.println(locator);
                        list.get(i).setDom_locator(locator);
                        previousElement = e;
                    } else {
                        if (previousElement != null) {
                            Element e = HandleElement.findNearestElementWithSpecifiedElement(previousElement, elementList);
                            String locator = Process.getAbsoluteXpath(e, "");
                            System.out.println(locator);
//                        String locator = Process.getXpath(e);
                            list.get(i).setDom_locator(locator);
                            previousElement = e;
                        } else {
                            Element e = HandleElement.findNearestElementWithSpecifiedElement(document.body(), elementList);
                            String locator = Process.getAbsoluteXpath(e, "");
                            System.out.println(locator);
//                        String locator = Process.getXpath(e);
                            list.get(i).setDom_locator(locator);
                            previousElement = e;
                        }
                    }
                }
                if (list.get(i) instanceof HoverAction) {
                    String text_locator = list.get(i).getText_locator();
                    List<String> input = Arrays.asList(text_locator);
                    Map<String, List<Element>> res = Hover.detectHoverElement(input, clickableElements, isAfterHoverAction);
                    isAfterHoverAction = true;
                    List<Element> elements = res.get(text_locator);
                    List<Element> elementList = new ArrayList<>();
                    WebDriver driver = new ChromeDriver();
                    driver.get(url);
                    Action.runActions(visited, driver);
                    for (Element e : elements) {
                        String xpath = Process.getAbsoluteXpath(e, "");
                        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                        if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                            elementList.add(e);
                        }
                    }
                    driver.quit();
                    if (elementList.size() == 1) {
                        Element e = elementList.get(0);
//                        String locator = Process.getXpath(e);
                        String locator = Process.getAbsoluteXpath(e, "");
                        System.out.println(locator);
                        list.get(i).setDom_locator(locator);
                        previousElement = e;
                    } else {
                        if (previousElement != null) {
                            Element e = HandleElement.findNearestElementWithSpecifiedElement(previousElement, elementList);
                            String locator = Process.getAbsoluteXpath(e, "");
                            System.out.println(locator);
//                        String locator = Process.getXpath(e);
                            list.get(i).setDom_locator(locator);
                            previousElement = e;
                        } else {
                            Element e = HandleElement.findNearestElementWithSpecifiedElement(document.body(), elementList);
                            String locator = Process.getAbsoluteXpath(e, "");
                            System.out.println(locator);
//                        String locator = Process.getXpath(e);
                            list.get(i).setDom_locator(locator);
                            previousElement = e;
                        }
                    }
                }
                if (list.get(i) instanceof ClickCheckboxAction) {
                    ClickCheckboxAction checkboxAction = (ClickCheckboxAction) list.get(i);
                    String choice = checkboxAction.getChoice();
                    Pair<String, String> pair = new Pair("", choice);
                    Element checkbox = Checkbox.detectCheckboxElement(pair, document);
                    isAfterHoverAction = false;
                    String locator = Process.getAbsoluteXpath(checkbox, "");
                    System.out.println(locator);
                    list.get(i).setDom_locator(locator);
//                    list.get(i).setDom_locator(Process.getXpath(checkbox));
                    previousElement = checkbox;
                }
                if (list.get(i) instanceof SelectAction) {
                    SelectAction selectAction = (SelectAction) list.get(i);
                    String question = selectAction.getQuestion();
                    String choice = selectAction.getChoice();
                    Pair<String, String> pair = new Pair<>(question, choice);
                    List<Pair<String, String>> list_pair = new ArrayList<>();
                    list_pair.add(pair);
                    Map<Pair<String, String>, Element> res = Select.detectSelectElement(list_pair, selectElements);
                    Element select = res.get(pair);
                    previousElement = select;
                    isAfterHoverAction = false;
                    String locator = Process.getAbsoluteXpath(select, "");
                    System.out.println(locator);
                    list.get(i).setDom_locator(locator);
//                    list.get(i).setDom_locator(Process.getXpath(select));
                }
                visited.add(list.get(i));
            }
        }
        return list;
    }
    public static String getHtmlContent(String linkHtml) {
        WebDriver driver = new ChromeDriver();
        driver.get(linkHtml);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String htmlContent = driver.getPageSource();
        driver.quit();
        return htmlContent;
    }

    public static Document getDomTree(String htmlContent) {
        Document domTree = Jsoup.parse(htmlContent);
        return domTree;
    }

    public static String getXpath(Element e) {
        int attributes_size = e.attributesSize();
        String xpath = "";
        xpath += "//" + e.tagName() + "[";
        boolean havingPreviousAttribute = false;
        if (attributes_size > 0) {
            Attributes attr = e.attributes();
            if (e.hasAttr("id") && !e.attr("id").isEmpty()) {
                xpath += "@id=" + "'" + e.attr("id") + "']";
                return xpath;
            }
            if (attributes_size == 1 && e.hasAttr("class") && !e.attr("class").isEmpty()) {
                xpath += "@class=" + "'" + e.attr("class");
                havingPreviousAttribute = true;
            } else {
                for (Attribute temp : attr) {
                    if (temp.getKey().equals("pattern") || temp.getKey().equals("class")) {
                        continue;
                    } else {
                        if (havingPreviousAttribute) {
                            xpath += " and " + "@" + temp.getKey() + "=" + "'" + temp.getValue() + "'";
                        } else {
                            xpath += "@" + temp.getKey() + "=" + "'" + temp.getValue() + "'";
                            havingPreviousAttribute = true;
                        }

                    }
                }
            }
        }

        String textOfElement = e.ownText();
        if (havingPreviousAttribute && !textOfElement.matches("\\s*")) {
            xpath += " and " + "normalize-space()=" + "'" + textOfElement + "'";
        } else {
            if (!textOfElement.matches("\\s*")) {
                xpath += "normalize-space()=" + "'" + textOfElement + "'";
            }
        }
        xpath += "]";
        return xpath;
    }

//    private String generateXPATH(WebElement childElement, String current) {
//        String childTag = childElement.getTagName();
//        if(childTag.equals("html")) {
//            return "/html[1]"+current;
//        }
//        WebElement parentElement = childElement.findElement(By.xpath(".."));
//        List<WebElement> childrenElements = parentElement.findElements(By.xpath("*"));
//        int count = 0;
//        for(int i=0;i<childrenElements.size(); i++) {
//            WebElement childrenElement = childrenElements.get(i);
//            String childrenElementTag = childrenElement.getTagName();
//            if(childTag.equals(childrenElementTag)) {
//                count++;
//            }
//            if(childElement.equals(childrenElement)) {
//                return generateXPATH(parentElement, "/" + childTag + "[" + count + "]"+current);
//            }
//        }
//        return null;
//    }

    public static String getAbsoluteXpath(Element e, String current) {
        String tag = e.tagName();
        if(tag.equals("html")) {
            return "/html"+current;
        }
        Element parentElement = e.parent();
        Elements childrenElements = parentElement.children();
        int count = 0;
        int idx = 0;
        for (int i = 0; i < childrenElements.size(); i++) {
            Element childrenElement = childrenElements.get(i);
            String childrenElementTag = childrenElement.tagName();
            if(tag.equals(childrenElementTag)) {
                count++;
            }
            if(e.equals(childrenElement)) {
                idx = count;
            }
            if (i == childrenElements.size() - 1) {
                if (count == 1) {
                    return getAbsoluteXpath(parentElement, "/" + tag + current);
                }
                return getAbsoluteXpath(parentElement, "/" + tag + "[" + idx + "]"+current);
            }
        }
        return null;
    }

    public static Element detectElementV2(String pageSource, Action currentAction, boolean isAfterHoverAction, Element previousElement, WebDriver driver) {

            Document document = getDomTree(pageSource);
            Element result = null;
            if (currentAction instanceof InputAction || currentAction instanceof ClickAction || currentAction instanceof HoverAction) {
                String text_locator = currentAction.getText_locator();
                List<String> input = Arrays.asList(text_locator);
                if (currentAction instanceof InputAction) {
                    Elements inputElements = HandleInput.getInputElements(document);
                    Map<String, List<Element>> map = InputElement.detectInputElement(input, inputElements, isAfterHoverAction);
                    List<Element> potentialElements = map.get(text_locator);
                    List<Element> isDisplayPotentialElements = new ArrayList<>();
                    for (Element e : potentialElements) {
                        String xpath = Process.getAbsoluteXpath(e, "");
                        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                        if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                            isDisplayPotentialElements.add(e);
                        }
                    }
                    if (isDisplayPotentialElements.size() == 1) {
                        result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                    } else if (isDisplayPotentialElements.size() > 0) {
                        if (previousElement != null) {
                            result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                        } else {
                            result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                        }
                    }
                }

                if (currentAction instanceof ClickAction) {
                    Elements clickableElements = HandleClick.getClickableElements(document);
                    Map<String, List<Element>> map = InputElement.detectInputElement(input, clickableElements, isAfterHoverAction);
                    List<Element> potentialElements = map.get(text_locator);
                    List<Element> isDisplayPotentialElements = new ArrayList<>();
                    for (Element e : potentialElements) {
                        String xpath = Process.getAbsoluteXpath(e, "");
                        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                        if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                            isDisplayPotentialElements.add(e);
                        }
                    }
                    if (isDisplayPotentialElements.size() == 1) {
                        result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                    } else if (isDisplayPotentialElements.size() > 0) {
                        if (previousElement != null) {
                            result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                        } else {
                            result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                        }
                    }
                }

                if (currentAction instanceof HoverAction) {
                    Elements clickableElements = HandleClick.getClickableElements(document);
                    Map<String, List<Element>> map = InputElement.detectInputElement(input, clickableElements, isAfterHoverAction);
                    List<Element> potentialElements = map.get(text_locator);
                    List<Element> isDisplayPotentialElements = new ArrayList<>();
                    for (Element e : potentialElements) {
                        String xpath = Process.getAbsoluteXpath(e, "");
                        Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                        if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                            isDisplayPotentialElements.add(e);
                        }
                    }
                    if (isDisplayPotentialElements.size() == 1) {
                        result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                    } else if (isDisplayPotentialElements.size() > 0) {
                        if (previousElement != null) {
                            result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                        } else {
                            result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                        }
                    }
                }

            } else {
                if (currentAction instanceof ClickCheckboxAction) {
                    ClickCheckboxAction checkboxAction = (ClickCheckboxAction) currentAction;
                    String choice = checkboxAction.getChoice();
                    String question = checkboxAction.getQuestion();
                    Pair<String, String> pairQuestionAndChoice = new Pair(question, choice);
                    result = Checkbox.detectCheckboxElement(pairQuestionAndChoice, document);
                }

                if (currentAction instanceof SelectAction) {
                    Elements selectElements = HandleSelect.getSelectElements(document);
                    SelectAction selectAction = (SelectAction) currentAction;
                    String question = selectAction.getQuestion();
                    String choice = selectAction.getChoice();
                    Pair<String, String> pairQuestionAndChoice = new Pair<>(question, choice);
                    List<Pair<String, String>> listPairQuestionAndChoice = new ArrayList<>();
                    listPairQuestionAndChoice.add(pairQuestionAndChoice);
                    Map<Pair<String, String>, Element> map = Select.detectSelectElement(listPairQuestionAndChoice, selectElements);
                    result = map.get(pairQuestionAndChoice);
                }
            }
            return result;

    }

    public static Element detectElement(String pageSource, String description, String actionType, boolean isAfterHoverAction, Element previousElement, List<Action> visitedActions, String url) {

        Document document = getDomTree(pageSource);
        Element result = null;
        List<String> input = Arrays.asList(description);
        Map<String, List<Element>> map = new HashMap<>();
        List<Element> potentialElements = new ArrayList<>();
        List<Element> isDisplayPotentialElements = new ArrayList<>();
        WebDriver driver = new ChromeDriver();
        switch (actionType) {
            case "Input":
                Elements inputElements = HandleInput.getInputElements(document);
                map = InputElement.detectInputElement(input, inputElements, isAfterHoverAction);
                potentialElements = map.get(description);
                driver.get(url);
                Action.runActions(visitedActions, driver);
                for (Element e : potentialElements) {
                    String xpath = Process.getAbsoluteXpath(e, "");
                    Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        isDisplayPotentialElements.add(e);
                    }
                }
                driver.quit();
                if (isDisplayPotentialElements.size() == 1) {
                    result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                } else {
                    if (previousElement != null) {
                        result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    } else {
                        result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    }
                }

                break;
            case "Click":
                Elements clickableElements = HandleClick.getClickableElements(document);
                map = Click.detectClickElement(input, clickableElements, isAfterHoverAction);
                potentialElements = map.get(description);
                driver.get(url);
                Action.runActions(visitedActions, driver);
                for (Element e : potentialElements) {
                    String xpath = Process.getAbsoluteXpath(e, "");
                    Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        isDisplayPotentialElements.add(e);
                    }
                }
                driver.quit();
                if (isDisplayPotentialElements.size() == 1) {
                    result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                } else {
                    if (previousElement != null) {
                        result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    } else {
                        result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    }
                }

                break;
            case "Hover":
                break;
            case "Select":
                break;
            case "Checkbox":
                break;
            default:


        }

        return result;

    }

    public static Element detectElement(String pageSource, Action action, boolean isAfterHoverAction, Element previousElement, List<Action> visitedActions, String url) {
        Document document = getDomTree(pageSource);
        Element result = null;
        if (action instanceof InputAction || action instanceof ClickAction || action instanceof HoverAction) {
            String text_locator = action.getText_locator();
            List<String> input = Arrays.asList(text_locator);
            if (action instanceof InputAction) {
                Elements inputElements = HandleInput.getInputElements(document);
                Map<String, List<Element>> map = InputElement.detectInputElement(input, inputElements, isAfterHoverAction);
                List<Element> potentialElements = map.get(text_locator);
                List<Element> isDisplayPotentialElements = new ArrayList<>();
                WebDriver driver = new ChromeDriver();
                driver.get(url);
                Action.runActions(visitedActions, driver);
                for (Element e : potentialElements) {
                    String xpath = Process.getAbsoluteXpath(e, "");
                    Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        isDisplayPotentialElements.add(e);
                    }
                }
                driver.quit();
                if (isDisplayPotentialElements.size() == 1) {
                    result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                } else if (isDisplayPotentialElements.size() > 0) {
                    if (previousElement != null) {
                        result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    } else {
                        result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    }
                }
            }

            if (action instanceof ClickAction) {
                Elements clickableElements = HandleClick.getClickableElements(document);
                Map<String, List<Element>> map = InputElement.detectInputElement(input, clickableElements, isAfterHoverAction);
                List<Element> potentialElements = map.get(text_locator);
                List<Element> isDisplayPotentialElements = new ArrayList<>();
                WebDriver driver = new ChromeDriver();
                driver.get(url);
                Action.runActions(visitedActions, driver);
                for (Element e : potentialElements) {
                    String xpath = Process.getAbsoluteXpath(e, "");
                    Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        isDisplayPotentialElements.add(e);
                    }
                }
                driver.quit();
                if (isDisplayPotentialElements.size() == 1) {
                    result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                } else if (isDisplayPotentialElements.size() > 0) {
                    if (previousElement != null) {
                        result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    } else {
                        result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    }
                }
            }

            if (action instanceof HoverAction) {
                Elements clickableElements = HandleClick.getClickableElements(document);
                Map<String, List<Element>> map = InputElement.detectInputElement(input, clickableElements, isAfterHoverAction);
                List<Element> potentialElements = map.get(text_locator);
                List<Element> isDisplayPotentialElements = new ArrayList<>();
                WebDriver driver = new ChromeDriver();
                driver.get(url);
                Action.runActions(visitedActions, driver);
                for (Element e : potentialElements) {
                    String xpath = Process.getAbsoluteXpath(e, "");
                    Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        isDisplayPotentialElements.add(e);
                    }
                }
                driver.quit();
                if (isDisplayPotentialElements.size() == 1) {
                    result = isDisplayPotentialElements.get(0);
//                        String locator = Process.getXpath(e);
//                    locator = Process.getAbsoluteXpath(e, "");
                } else if (isDisplayPotentialElements.size() > 0) {
                    if (previousElement != null) {
                        result = HandleElement.findNearestElementWithSpecifiedElement(previousElement, isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    } else {
                        result = HandleElement.findNearestElementWithSpecifiedElement(document.body(), isDisplayPotentialElements);
//                        locator = Process.getAbsoluteXpath(e, "");
                    }
                }
            }

        } else {
            if (action instanceof ClickCheckboxAction) {
                ClickCheckboxAction checkboxAction = (ClickCheckboxAction) action;
                String choice = checkboxAction.getChoice();
                String question = checkboxAction.getQuestion();
                Pair<String, String> pairQuestionAndChoice = new Pair(question, choice);
                result = Checkbox.detectCheckboxElement(pairQuestionAndChoice, document);
            }

            if (action instanceof SelectAction) {
                Elements selectElements = HandleSelect.getSelectElements(document);
                SelectAction selectAction = (SelectAction) action;
                String question = selectAction.getQuestion();
                String choice = selectAction.getChoice();
                Pair<String, String> pairQuestionAndChoice = new Pair<>(question, choice);
                List<Pair<String, String>> listPairQuestionAndChoice = new ArrayList<>();
                listPairQuestionAndChoice.add(pairQuestionAndChoice);
                Map<Pair<String, String>, Element> map = Select.detectSelectElement(listPairQuestionAndChoice, selectElements);
                result = map.get(pairQuestionAndChoice);
            }
        }
        return result;
    }
    public static void main(String[] args) {
        Pair<String, List<Action>> res = parseJson("src/main/resources/testcase/sample_saucedemo.json");
        String url = res.getFirst();
        List<Action> actions = res.getSecond();
        List<Action> result = detectLocators(actions, url);
        for (Action action : result) {
            System.out.println(action.getDom_locator());
        }
    }
}
