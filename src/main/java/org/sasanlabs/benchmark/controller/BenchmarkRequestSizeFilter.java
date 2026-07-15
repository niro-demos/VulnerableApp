package org.sasanlabs.benchmark.controller;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects oversized benchmark bodies, including chunked requests without Content-Length. */
@Component
@Profile("unsafe")
public class BenchmarkRequestSizeFilter extends OncePerRequestFilter {
    private final long maxRequestBytes;

    public BenchmarkRequestSizeFilter(
            @Value("${benchmark.max-request-bytes:1048576}") long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().endsWith("/scanner/benchmark");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestBytes) {
            response.sendError(
                    HttpStatus.PAYLOAD_TOO_LARGE.value(), "Benchmark request is too large");
            return;
        }
        try {
            chain.doFilter(new LimitedRequest(request, maxRequestBytes), response);
        } catch (RequestTooLargeException tooLarge) {
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(
                        HttpStatus.PAYLOAD_TOO_LARGE.value(), "Benchmark request is too large");
            }
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;

        private LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long count;

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    if (value >= 0 && ++count > limit) throw new RequestTooLargeException();
                    return value;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }

    private static final class RequestTooLargeException extends IOException {}
}
