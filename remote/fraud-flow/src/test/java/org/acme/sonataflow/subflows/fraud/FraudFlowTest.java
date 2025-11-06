package org.acme.sonataflow.subflows.fraud;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class FraudFlowTest {

    @Test
    void fraud_total_over_1000_should_flag_true() {
        given()
                .contentType("application/json")
                .body("{\"total\": 1500}")
                .when()
                .post("/fraudhandling")
                .then()
                .statusCode(201)
                .body("workflowdata.fraudEvaluation", is(true))
                .body("workflowdata.total", equalTo(1500));
    }

    @Test
    void fraud_total_below_or_equal_1000_should_echo_total() {
        given()
                .contentType("application/json")
                .body("{\"total\": 800}")
                .when()
                .post("/fraudhandling")
                .then()
                .statusCode(201)
                .body("workflowdata.total", equalTo(800));
    }
}

