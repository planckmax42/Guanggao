package com.example.adplatform.common.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTests {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ExceptionTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @CsvSource({"1451,500,99999", "1452,500,99999", "1048,500,99999"})
    void shouldTreatUntranslatedIntegrityErrorsAsSystemErrors(int sqlCode, int httpStatus, int resultCode) throws Exception {
        mockMvc.perform(get("/test/integrity/{code}", sqlCode))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(resultCode))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private_table"))));
    }

    @ParameterizedTest
    @CsvSource({"RESOURCE_IN_USE,409,50008,素材存在关联数据，不能删除", "RESOURCE_NOT_FOUND,404,50002,广告位不存在"})
    void shouldPreserveTranslatedBusinessError(ErrorCode errorCode, int httpStatus, int resultCode,
                                               String message) throws Exception {
        mockMvc.perform(get("/test/translated/{code}", errorCode).param("message", message))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(resultCode))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void shouldKeepDuplicateKeyHandling() throws Exception {
        mockMvc.perform(get("/test/duplicate-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50004));
    }

    @Test
    void shouldNotMisclassifyIntegrityErrorsWithoutSqlCause() throws Exception {
        mockMvc.perform(get("/test/integrity-without-cause"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(99999));
    }

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

    @Test
    void shouldReturnMysqlConnectionFailedForDatabaseTimeout() throws Exception {
        mockMvc.perform(get("/test/database-timeout"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.MYSQL_CONNECTION_FAILED.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.MYSQL_CONNECTION_FAILED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    private static class ExceptionTestController {

        @GetMapping("/test/translated/{code}")
        void translated(@PathVariable("code") ErrorCode code, @RequestParam("message") String message) {
            throw new BusinessException(code, message,
                    new DataIntegrityViolationException("database details"));
        }

        @GetMapping("/test/integrity/{code}")
        void integrity(@PathVariable("code") int code) {
            throw new DataIntegrityViolationException("write failed",
                    new RuntimeException(new SQLException("private_table constraint failed", "23000", code)));
        }

        @GetMapping("/test/duplicate-key")
        void duplicateKey() {
            throw new DuplicateKeyException("duplicate");
        }

        @GetMapping("/test/integrity-without-cause")
        void integrityWithoutCause() {
            throw new DataIntegrityViolationException("unexpected integrity failure");
        }

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

        @GetMapping("/test/database-timeout")
        void databaseTimeout() {
            throw new QueryTimeoutException("数据库查询超时");
        }

        @GetMapping("/test/system-exception")
        void systemException() {
            throw new IllegalStateException("test exception");
        }
    }
}
