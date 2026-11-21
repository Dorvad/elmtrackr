package com.elmtrackr.app.monitoring

import io.sentry.SentryEvent
import io.sentry.exception.ExceptionMechanismException
import io.sentry.exception.SentryHttpClientException
import io.sentry.protocol.Mechanism
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which events [CrashReporting]'s `beforeSend` drops as failed HTTP responses.
 *
 * The OkHttp case is built the way `SentryOkHttpUtils.captureClientError` builds it in
 * sentry-okhttp 8.50.1: the client exception wrapped in an [ExceptionMechanismException]
 * whose mechanism is the interceptor. A production event of exactly this shape — a 504
 * from `/rest/v1/shifts` — is what this filter exists for.
 */
class CrashReportingFailedHttpTest {

    @Test
    fun `a 5xx captured by the OkHttp interceptor is dropped`() {
        val mechanism = Mechanism().apply { type = "SentryOkHttpInterceptor" }
        val event = SentryEvent(
            ExceptionMechanismException(
                mechanism,
                SentryHttpClientException("HTTP Client Error with status code: 504"),
                Thread.currentThread(),
                true,
            ),
        )

        assertTrue(CrashReporting.isFailedHttpResponse(event))
    }

    @Test
    fun `an unwrapped client exception is dropped too`() {
        val event = SentryEvent(SentryHttpClientException("HTTP Client Error with status code: 503"))

        assertTrue(CrashReporting.isFailedHttpResponse(event))
    }

    @Test
    fun `a crash in app code is still sent`() {
        val event = SentryEvent(IllegalStateException("boom"))

        assertFalse(CrashReporting.isFailedHttpResponse(event))
    }

    @Test
    fun `a handled sync failure reported through report is still sent`() {
        // CrashReporting.report() is how background work surfaces its failures; a sync
        // error that happens to mention a status code is not the interceptor's event.
        val event = SentryEvent(RuntimeException("Sync failed: 504 Gateway Timeout"))

        assertFalse(CrashReporting.isFailedHttpResponse(event))
    }

    @Test
    fun `an event without a throwable is still sent`() {
        assertFalse(CrashReporting.isFailedHttpResponse(SentryEvent()))
    }
}
