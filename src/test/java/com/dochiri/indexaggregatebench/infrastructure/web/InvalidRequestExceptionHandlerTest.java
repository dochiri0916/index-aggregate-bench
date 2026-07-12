package com.dochiri.indexaggregatebench.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidRequestExceptionHandlerTest {

    @Test
    @DisplayName("잘못된 요청 예외는 사용자 메시지를 노출하지 않고 400 응답으로 변환한다")
    void invalidRequestShouldBeConvertedToBadRequest() {
        // given
        InvalidRequestExceptionHandler handler = new InvalidRequestExceptionHandler();

        // when
        var response = handler.handleInvalidRequest(new IllegalArgumentException("internal detail"));

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "invalid_request");
        assertThat(response.getBody()).containsEntry("message", "요청 값이 올바르지 않습니다.");
        assertThat(response.getBody()).doesNotContainValue("internal detail");
    }
}
