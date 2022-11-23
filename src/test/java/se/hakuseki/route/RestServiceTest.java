package se.hakuseki.route;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class RestServiceTest {
    @Test
    public void testFIRDSEndpoint() {
        given()
                .when().get("/firds")
                .then()
                .log().headers()
                .statusCode(200)
                ;
    }

}