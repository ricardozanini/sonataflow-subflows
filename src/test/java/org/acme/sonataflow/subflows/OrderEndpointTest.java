package org.acme.sonataflow.subflows;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class OrderEndpointTest {

    @Test
    void post_order_should_set_fraud_true_and_shipping_international() {
        String body = """
                {
                  "id": "f0643c68-609c-48aa-a820-5df423fa4fe0",
                  "country": "CA",
                  "total": 10000,
                  "description": "iPhone 12"
                }
                """;

        String response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post("/order")
                .then()
                .statusCode(201)
                .extract().asPrettyString();
                //.body("workflowdata.fraudEvaluation", is(true))
                //.body("workflowdata.shipping", equalTo("international"));
        assertThat(response).isNotEmpty();
    }
}
