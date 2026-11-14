package com.anything_but_dotnet.copilot.controllers;

import com.anything_but_dotnet.copilot.config.SecurityConfig;
import com.anything_but_dotnet.copilot.dto.Citation;
import com.anything_but_dotnet.copilot.dto.UserQuestionRequest;
import com.anything_but_dotnet.copilot.dto.UserQuestionResponse;
import com.anything_but_dotnet.copilot.exception.GatewayExceptionHandler;
import com.anything_but_dotnet.copilot.exception.RagGenerationException;
import com.anything_but_dotnet.copilot.model.ErrorCategory;
import com.anything_but_dotnet.copilot.model.QuestionStatus;
import com.anything_but_dotnet.copilot.security.JwtService;
import com.anything_but_dotnet.copilot.service.RagGenerationOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenerationController.class)
@Import({SecurityConfig.class, GatewayExceptionHandler.class})
class GenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagGenerationOrchestrator ragGenerationOrchestrator;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void shouldReturn200WhenQuestionProcessedSuccessfully() throws Exception {
        UserQuestionResponse mockResponse = new UserQuestionResponse(
                "req-123",
                QuestionStatus.ANSWERED,
                "To get internet compensation, submit the reimbursement form on SharePoint.",
                List.of(new Citation(
                        "IT Compensation Guidelines",
                        "https://company.sharepoint.com/docs/it-comp",
                        "doc-456",
                        "123e4567-e89b-12d3-a456-426614174000",
                        0.95
                ))
        );

        when(ragGenerationOrchestrator.generateUserQuestions(any(UserQuestionRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/chat/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query_id": "req-123",
                                  "channel": "teams",
                                  "user_email": "oleh.pona@company.com",
                                  "query": "How to get internet compensation?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query_id").value("req-123"))
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.reply_text").value("To get internet compensation, submit the reimbursement form on SharePoint."))
                .andExpect(jsonPath("$.citations[0].title").value("IT Compensation Guidelines"))
                .andExpect(jsonPath("$.citations[0].document_id").value("doc-456"))
                .andExpect(jsonPath("$.citations[0].chunk_id").value("123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(jsonPath("$.citations[0].score").value(0.95));
    }

    @Test
    void shouldReturn400WithQueryIdWhenValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/chat/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query_id": "req-123",
                                  "channel": "teams",
                                  "user_email": "oleh.pona@company.com",
                                  "query": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.query_id").value("req-123"))
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.user_message").value("Your question is empty or exceeds the maximum allowed length."));
    }

    @Test
    void shouldReturn400WithNullQueryIdWhenJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/chat/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ malformed json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.query_id").isEmpty())
                .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"));
    }

    @Test
    void shouldReturn503WhenDownstreamRagUnavailable() throws Exception {
        when(ragGenerationOrchestrator.generateUserQuestions(any(UserQuestionRequest.class)))
                .thenThrow(new RagGenerationException("Vector DB down", ErrorCategory.VECTOR_DB_UNAVAILABLE));

        mockMvc.perform(post("/api/v1/chat/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query_id": "req-123",
                                  "channel": "slack",
                                  "user_email": "oleh.pona@company.com",
                                  "query": "How to get internet compensation?"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.query_id").value("req-123"))
                .andExpect(jsonPath("$.error_code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.user_message").value("The knowledge assistant is temporarily unavailable. Please try again in a few minutes."));
    }

    @Test
    void shouldReturn500WhenInternalErrorOccurs() throws Exception {
        when(ragGenerationOrchestrator.generateUserQuestions(any(UserQuestionRequest.class)))
                .thenThrow(new RagGenerationException("Internal failure", ErrorCategory.INTERNAL_ERROR));

        mockMvc.perform(post("/api/v1/chat/question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query_id": "req-123",
                                  "channel": "slack",
                                  "user_email": "oleh.pona@company.com",
                                  "query": "How to get internet compensation?"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.query_id").value("req-123"))
                .andExpect(jsonPath("$.error_code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.user_message").value("An unexpected error occurred while processing your request."));
    }
}
