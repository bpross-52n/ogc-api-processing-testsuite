package org.n52.geoprocessing.oapi.test;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.core.StringContains.containsString;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport.Level;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import io.restassured.specification.RequestSpecification;

public class TestOGCAPIProcessing {

    private static final String OPENAPI_SPEC_URL =
            "https://app.swaggerhub.com/apiproxy/schema/file/apis/geoprocessing/WPS-all-in-one/1.0-draft?format=json";

    private static final int PORT = 9999;

    private static final OpenApiInteractionValidator validator = OpenApiInteractionValidator
            .createFor(OPENAPI_SPEC_URL)
            .withLevelResolver(
                LevelResolver.create().withLevel("validation.schema.additionalProperties", Level.IGNORE).build())
            .build();
    private static final OpenApiValidationFilter validationFilter = new OpenApiValidationFilter(validator);
    
//    private final OpenApiValidationFilter validationFilter = new OpenApiValidationFilter(OPENAPI_SPEC_URL);

    private RequestSpecification requestSpecificationValid;

    private RequestSpecification requestSpecificationInValid;

//    private String processID = "org.n52.wps.echoprocess.EchoProcess";
    private String processID = "org.n52.javaps.test.EchoProcess";

    private String baseURIValid = "http://localhost:8080/javaps/rest/";
//    private String processID2 = "org.n52.javaps.test.EchoProcess3";
//    private String processID3 = "org.n52.javaps.test.EchoProcess4";
    
    @Before
    public void setup() {
        RequestSpecBuilder builder = new RequestSpecBuilder();

//        builder.setBaseUri("http://geoprocessing.demo.52north.org/javaps/rest/");
        builder.setBaseUri(baseURIValid );
//        builder.setPort(8080);

        requestSpecificationValid = builder.build();
        builder = new RequestSpecBuilder();

        builder.setBaseUri("http://geoprocessing.demo.52north.org/wps-proxy/");
        builder.setPort(8080);

        requestSpecificationInValid = builder.build();
        builder = new RequestSpecBuilder();
    }

    @Test
    public void testRootValid() {

        ArrayList<?> links = given(requestSpecificationValid).filter(validationFilter).when().get("/").then().assertThat().statusCode(200).extract().path("links");
        
        assertTrue(links != null);
        
        for (Object link : links) {
            if(link instanceof Map<?, ?>) {
                Object href = ((Map<?,?>)link).get("href");
                
                get((String) href).then().assertThat().statusCode(200);
                
//                assertTrue(.statusCode() == 200);
//                .then().assertThat().statusCode(200);
//                System.out.println(href);
            }
        }
    }

    @Test(
            expected = OpenApiValidationFilter.OpenApiValidationException.class)
    public void testRootInValid() {

        given(requestSpecificationInValid).filter(validationFilter).when().get("/");
    }

    @Test
    public void testProcessesValid() {

        given(requestSpecificationValid).filter(validationFilter).when().get("processes/").then().assertThat()
                .statusCode(200);
    }

    @Test(
            expected = OpenApiValidationFilter.OpenApiValidationException.class)
    public void testProcessesInValid() {

        given(requestSpecificationInValid).filter(validationFilter).when().get("processes/");
    }

    @Test
    public void testProcessDescriptionValid() {

        given(requestSpecificationValid).filter(validationFilter).when().get("processes/" + processID).then().assertThat()
                .statusCode(200);
    }
    
    //test sync
    //test async (with delay to check the status)
    //test reference output
    
    @Test
    public void testExecute() throws IOException {
        
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("execute.json");
        
        StringWriter writer = new StringWriter();
        String encoding = StandardCharsets.UTF_8.name();
        IOUtils.copy(inputStream, writer, encoding);
                
        Response response = given(requestSpecificationValid).header("Content-Type", "application/json").body(writer.toString()).when().post("processes/" + processID + "/jobs");
        response.then().assertThat()
        .statusCode(201).header("Location", containsString("http://localhost:8080/javaps/rest/processes/" + processID + "/jobs/"));
        String location = response.then().extract().header("Location");
        String jobId = location.replace("http://localhost:8080/javaps/rest/processes/" + processID + "/jobs/", "");
        int i = 0;
        while(i < 3) {
            ResponseBody<?> responseBody = given(requestSpecificationValid).filter(validationFilter).get("processes/" + processID + "/jobs/" + jobId).body();
            Object o = responseBody.path("status");
            if(o instanceof String) {
                String status = (String)o;
                System.out.println(status);
                assertTrue(!status.equals("failed"));
                if(status.equals("successful")) {
                    Object links = responseBody.path("links");
                    if(links instanceof List<?>) {
                        String result = getLink((List<?>) links, "result");
                        System.out.println(result);
                        given(requestSpecificationValid).header("Content-Type", "application/json").filter(validationFilter).get(result.replace(baseURIValid, "")).then().statusCode(200);
                    }
                    break;
                }
            }
            i++;
        }
        
    }
    
    @Test
    public void testExecuteSync() throws IOException {

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("execute.json");

        StringWriter writer = new StringWriter();
        String encoding = StandardCharsets.UTF_8.name();
        IOUtils.copy(inputStream, writer, encoding);

        Response response = given(requestSpecificationValid).header("Content-Type", "application/json")
                .body(writer.toString()).when().post("processes/" + processID + "/jobs?sync-execute=true");
        response.then().assertThat().statusCode(200);
        ResponseBody<?> responseBody = response.body();
        Object o = responseBody.path("outputs");
        assertTrue(o != null);
//        if (o instanceof String) {
//            String status = (String) o;
//            System.out.println(status);
//            assertTrue(!status.equals("failed"));
//            if (status.equals("successful")) {
//                Object links = responseBody.path("links");
//                if (links instanceof List<?>) {
//                    String result = getLink((List<?>) links, "result");
//                    System.out.println(result);
//                    given(requestSpecificationValid).header("Content-Type", "application/json").filter(validationFilter);
//                }
//            }
//        }

    }
    
    @Test
    public void testJobListValid() {

        given(requestSpecificationValid).filter(validationFilter).when().get("processes/" + processID + "/jobs/").then().assertThat()
                .statusCode(200);
    }
    
    private String getLink(List<?> links, String relName) {
        
        for (Object link : links) {
            if(link instanceof Map<?, ?>) {
                Object rel = ((Map<?,?>)link).get("rel");
                Object href = ((Map<?,?>)link).get("href");
                if(rel.equals(relName)) {
                    return (String) href;
                }
                
            }
        }
        return "";
    }
    
    @Test
    public void testProcessExecutionValid() {
        
        given(requestSpecificationValid).filter(validationFilter).when().get("processes/").then().assertThat()
        .statusCode(200);
    }

}
