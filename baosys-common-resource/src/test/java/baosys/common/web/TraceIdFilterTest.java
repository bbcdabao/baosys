package baosys.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sharesIncomingTraceWithResponseAndRequestProcessingThenClearsIt() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIds.HEADER_NAME, "request-trace");
        var response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, (req, res) -> {
            assertThat(TraceIds.current()).isEqualTo("request-trace");
            assertThat(response.getHeader(TraceIds.HEADER_NAME)).isEqualTo("request-trace");
        });

        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void generatesTraceForMissingBlankAndOversizedHeaders() throws Exception {
        for (String candidate : new String[] {null, " ", "x".repeat(129)}) {
            var request = new MockHttpServletRequest();
            if (candidate != null) {
                request.addHeader(TraceIds.HEADER_NAME, candidate);
            }
            var response = new MockHttpServletResponse();
            new TraceIdFilter().doFilter(request, response, (req, res) -> {
                assertThat(TraceIds.current()).matches("[0-9a-f]{32}");
                assertThat(response.getHeader(TraceIds.HEADER_NAME)).isEqualTo(TraceIds.current());
            });
            assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
        }
    }

    @Test
    void acceptsMaximumLengthAndClearsMdcWhenRequestFails() {
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIds.HEADER_NAME, "x".repeat(128));
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, response, (req, res) -> {
            assertThat(TraceIds.current()).isEqualTo("x".repeat(128));
            throw new ServletException("failed");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void createsFallbackWithoutLeakingMdcOutsideRequest() {
        assertThat(TraceIds.current()).matches("[0-9a-f]{32}");
        assertThat(TraceIds.create()).matches("[0-9a-f]{32}");
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    void asyncDispatchReusesRequestIdAndRestoresOuterMdc() throws Exception {
        var filter = new TraceIdFilter();
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIds.HEADER_NAME, "invalid trace\r\nvalue");
        var response = new MockHttpServletResponse();
        MDC.put(TraceIds.MDC_KEY, "outer");
        filter.doFilter(request, response, (req, res) ->
            assertThat(TraceIds.current()).matches("[0-9a-f]{32}"));
        String trace = response.getHeader(TraceIds.HEADER_NAME);
        request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        filter.doFilter(request, response, (req, res) -> assertThat(TraceIds.current()).isEqualTo(trace));
        assertThat(MDC.get(TraceIds.MDC_KEY)).isEqualTo("outer");
        assertThat(filter.getOrder()).isLessThan(-100);
    }
}
