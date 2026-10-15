package com.example.travelagency.web;

import com.example.travelagency.web.annotation.CurrentUserId;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserIdArgumentResolverTest {
    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @Test
    void supportsParameter_annotatedString_returnsTrue() throws Exception {
        assertTrue(resolver.supportsParameter(parameter(0)));
    }

    @Test
    void supportsParameter_unannotatedString_returnsFalse() throws Exception {
        assertFalse(resolver.supportsParameter(parameter(1)));
    }

    @Test
    void supportsParameter_annotatedWrongType_returnsFalse() throws Exception {
        assertFalse(resolver.supportsParameter(parameter(2)));
    }

    @Test
    void resolveArgument_authenticatedRequest_returnsPrincipalNameAsString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "traveler-123");

        Object result = resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);

        assertInstanceOf(String.class, result);
        assertEquals("traveler-123", result);
    }

    @Test
    void resolveArgument_missingPrincipal_rejectsRequest() throws Exception {
        MethodParameter parameter = parameter(0);
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> resolver.resolveArgument(parameter, null, request, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Authentication is required", exception.getReason());
    }

    private static MethodParameter parameter(int index) throws NoSuchMethodException {
        return new MethodParameter(ExampleController.class.getDeclaredMethod(
                "example", String.class, String.class, Long.class), index);
    }

    private static class ExampleController {
        void example(@CurrentUserId String userId, String plain, @CurrentUserId Long wrongType) {
        }
    }
}
