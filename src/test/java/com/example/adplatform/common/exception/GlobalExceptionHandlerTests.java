package com.example.adplatform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ExceptionTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldReturnUnifiedErrorResponseForBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnUnifiedErrorResponseForDependencyException() throws Exception {
        mockMvc.perform(get("/test/dependency-exception"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE.getCode()))
                .andExpect(jsonPath("$.message").value("Elasticsearch 暂时不可用"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnUnifiedErrorResponseForSystemException() throws Exception {
        mockMvc.perform(get("/test/system-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SYSTEM_ERROR.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    private static class ExceptionTestController {

        @GetMapping("/test/business-exception")
        void businessException() {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/test/dependency-exception")
        void dependencyException() {
            throw new DependencyException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "Elasticsearch 暂时不可用");
        }

        @GetMapping("/test/system-exception")
        void systemException() {
            throw new IllegalStateException("test exception");
        }
    }
}
