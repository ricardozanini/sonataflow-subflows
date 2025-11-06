package org.acme.sonataflow.subflows.shipping;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ShippingFlowTest {

    @Test
    void shipping_country_not_us_should_be_domestic_false() {
        given()
                .contentType("application/json")
                .body("{\"country\": \"CA\"}")
                .when()
                .post("/shippinghandling")
                .then()
                .statusCode(201)
                .body("workflowdata.shipping", is("international"));
    }

    @Test
    void shipping_country_us_should_be_domestic_true() {
        given()
                .contentType("application/json")
                .body("{\"country\": \"US\"}")
                .when()
                .post("/shippinghandling")
                .then()
                .statusCode(201)
                .body("workflowdata.shipping", is("domestic"));
    }
}

