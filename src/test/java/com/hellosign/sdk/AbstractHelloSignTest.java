package com.hellosign.sdk;

/**
 * The MIT License (MIT)
 * 
 * Copyright (C) 2015 hellosign.com
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hellosign.sdk.http.Authentication;

/**
 * Contains common code used by all tests.
 * 
 * @author "Chris Paul (chris@hellosign.com)"
 */
public abstract class AbstractHelloSignTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractHelloSignTest.class);

    protected static String validApiKey;
    protected static String validUserEmail;
    protected static String validUserPass;
    protected static String validUserEmail2;
    protected static String teamName;
    protected static String invalidUserEmail;
    protected static String invalidUserPass;
    protected static String templateId;
    protected static String templateTitle;
    protected static String clientId;
    protected static String callbackUrl;

    protected static boolean online = false;

    protected static Authentication auth = new Authentication();

    // Static block to populate test properties and set up the HTTPS client, if necessary.
    // These can be used by all implementing test classes.
    static {
        Properties properties = new Properties();
        try {
            properties.load(AbstractHelloSignTest.class.getResourceAsStream("/test.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (properties != null) {
            validApiKey = properties.getProperty("valid.apiKey");
            validUserEmail = properties.getProperty("valid.email");
            validUserPass = properties.getProperty("valid.pass");
            validUserEmail2 = properties.getProperty("valid.email.2");
            teamName = properties.getProperty("team.name");
            invalidUserEmail = properties.getProperty("invalid.email");
            invalidUserPass = properties.getProperty("invalid.pass");
            templateTitle = properties.getProperty("template.title");
            templateId = properties.getProperty("template.id");
            clientId = properties.getProperty("client.id");
            callbackUrl = properties.getProperty("callback.url");
    
            auth.setApiKey(validApiKey);
            try {
                auth.setWebsiteCredentials(validUserEmail, validUserPass);
            } catch (HelloSignException e) {
                e.printStackTrace();
            }
        }

        // Force this to be run against the local test environment
        System.setProperty("hellosign.base.url", "https://www.dev-hellosign.com/apiapp_dev.php");
        System.setProperty("hellosign.oauth.base.url", "https://www.dev-hellosign.com/webapp_dev.php/oauth/token");
        System.setProperty("hellosign.disable.ssl", "true");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");

        online = false;
        if (!validApiKey.isEmpty()) {
            try {
                HelloSignClient client = new HelloSignClient(validApiKey);
                online = client.isOnline();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static boolean isHelloSignAvailable() {
        return online;
    }

    public static void printFields(Map<String, Serializable> fields) {
        for (String key : fields.keySet()) {
            logger.debug("\t" + key + "=" + fields.get(key));
        }
    }

    /**
     * Utility method to compare POST fields from a text file (loaded from getExpectedFields()) and
     * the fields map generated by the HelloSign resource class. This ensures that we're getting the
     * correct fields to submit to the server. If the actual value of a field is arbitrary (e.g.,
     * an ID field), then the expected field should have an asterisk (*) as its expected value.
     * @param expectedFields Map<String, Serializable>
     * @param actualFields Map<String, Serializable>
     * @return true if all fields are equal, false otherwise
     */
    public static boolean areFieldsEqual(Map<String, Serializable> expectedFields, Map<String, Serializable> actualFields) {
        for (String key : expectedFields.keySet()) {
            if (!actualFields.containsKey(key)) {
                logger.error("Key '" + key + "' not found in actual fields.");
                return false;
            }
            String expectedValue = (String) expectedFields.get(key);
            Serializable actualValue = actualFields.get(key);
            if ("*".equals(expectedValue)) {
                continue;
            }
            if (actualValue instanceof File) {
                continue; // Not sure we can test this unless we compare file names?
            }
            if (!actualValue.toString().equals(expectedValue.toString())) {
                logger.error("Actual value '" + actualValue.toString() + "' does not equal expected value '" + expectedValue.toString() + "'");
                return false;
            }
        }
        return true;
    }

    /**
     * Searches for the provided file in the directory:
     *     /src/test/resources/[TEST_CLASS_NAME]
     * 
     * @param name String file name
     * @return File
     */
    protected File getTestFile(String name) {
        String url = System.getProperty("file.separator") + this.getClass().getSimpleName() + 
            System.getProperty("file.separator") + name;
        return new File(this.getClass().getResource(url).getFile());
    }

    /**
     * Searches for the provided file in the directory and returns its contents as a String.
     *     /src/test/resources/[TEST_CLASS_NAME]
     * 
     * @param name String file name
     * @return String
     */
    protected String getTestFileAsString(String name) throws FileNotFoundException {
        Scanner s = new Scanner(getTestFile(name));
        s.useDelimiter("\\Z");
        String result = (s.hasNext() ? s.next() : "");
        s.close();
        return result;
    }

    /**
     * Searches for a test case file located at:
     *    /src/test/resources/[TEST_CLASS_NAME]/expectedFields.txt
     *
     * It then parses this into a Map of KVPs to use in analyzing the fields being
     * returned by the HelloSign resource.
     * 
     * @return Map<String, Serializable> (technically always Map<String, String>, 
     * but let's be consistent)
     */
    public Map<String, Serializable> getExpectedFields() {
        Map<String, Serializable> fields = new HashMap<String, Serializable>();
        BufferedReader br = null;
        try {
            File expectedFields = getTestFile("expectedFields.txt");
            br = new BufferedReader(new FileReader(expectedFields));
            String line = null;
            while ((line = br.readLine()) != null) {
                String[] field = line.split("=");
                if (field.length == 2) {
                    fields.put(field[0], field[1]);
                }
            }
        } catch (Exception ex) {
            try {
                if (br != null) br.close();
            } catch (Exception e) {}
        }
        return fields;
    }

    /**
     * Searches for a test case file located at:
     *     /src/test/resources/[TEST_CLASS_NAME]/expectedResponse.txt
     * 
     * It then parses this into a JSONObject for use in analyzing the response
     * returned by an API call.
     * 
     * @return JSONObject
     */
    public JSONObject getExpectedJSONResponse() {
        JSONObject json = null;
        try {
            String jsonStr = getTestFileAsString("expectedResponse.txt");
            json = new JSONObject(jsonStr);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return json;
    }

    /**
     * Utility method to compare 2 JSON objects for similar structure and content.
     * @param json1 JSONObject
     * @param json2 JSONObject
     * @return true if the structure and content of the JSON objects is equal, false otherwise
     */
    public static boolean areJSONObjectsEqual(JSONObject json1, JSONObject json2) {
        return areJSONObjectsEqual(json1, json2, false) && areJSONObjectsEqual(json1, json2, false);
    }

    /**
     * Utility method to compare 2 JSON objects for similar structure and content. 
     * Ignores the content and focuses solely on the structure.
     * @param json1 JSONObject
     * @param json2 JSONObject
     * @param ignoreData boolean
     * @return true if the structure of the JSONObjects is equal, false otherwise
     */
    public static boolean areJSONObjectsEqualIgnoreData(JSONObject json1, JSONObject json2) {
        return areJSONObjectsEqual(json1, json2, true) && areJSONObjectsEqual(json1, json2, true);
    }

    private static boolean areJSONObjectsEqual(JSONObject json1, JSONObject json2, boolean ignoreData) {
        boolean equal = true;
        try {
            Iterator<?> keys = json1.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                if (json2.has(key)) {
                    if (ignoreData) {
                        equal &= true;
                    } else {
                        equal &= compareObjects(json1.get(key), json2.get(key));
                    }
                }
                if (!equal) {
                    return false;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    private static boolean compareObjects(Object value1, Object value2) throws Exception {
        if (!value1.getClass().equals(value2.getClass())) {
            return false;
        }
        if (value1 instanceof JSONObject) {
            return areJSONObjectsEqual((JSONObject) value1, (JSONObject) value2, false);
        } else if (value1 instanceof JSONArray) {
            if (((JSONArray) value1).length() != ((JSONArray) value2).length()) {
                return false;
            }
            // Assumes array order matters
            boolean arraysEqual = true;
            for (int i = 0; i < ((JSONArray) value1).length(); i++) {
                Object v1 = ((JSONArray) value1).get(i);
                Object v2 = ((JSONArray) value2).get(i);
                arraysEqual &= compareObjects(v1, v2);
            }
            return arraysEqual;
        } else if (value1 instanceof String) {
            return value1.equals(value2);    
        }
        // else assume they are primitives
        return value1 == value2;
    }
}
