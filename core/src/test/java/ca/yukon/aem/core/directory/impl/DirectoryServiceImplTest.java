package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.directory.DirectoryApiClient;
import ca.yukon.aem.core.directory.DirectoryApiResponse;
import ca.yukon.aem.core.directory.DirectoryService;
import ca.yukon.aem.core.directory.DirectoryUnavailableException;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class DirectoryServiceImplTest {

    private final DirectoryApiClient apiClient = mock(DirectoryApiClient.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private DirectoryServiceImpl service;

    @BeforeEach
    void setUp(AemContext context) {
        // The scheduler is mocked so the refresh only runs when a test calls it.
        when(scheduler.NOW(anyInt(), anyLong())).thenReturn(mock(ScheduleOptions.class, RETURNS_SELF));
        when(scheduler.NOW()).thenReturn(mock(ScheduleOptions.class, RETURNS_SELF));
        context.registerService(Scheduler.class, scheduler, "service.ranking", Integer.MAX_VALUE);
        context.registerService(DirectoryApiClient.class, apiClient);
        when(apiClient.isConfigured()).thenReturn(true);
        service = (DirectoryServiceImpl) context.registerInjectActivateService(new DirectoryServiceImpl(),
                "search.max.results", 2);
    }

    @Test
    void servesTheDownloadedDirectory() throws Exception {
        apiReturnsTheFixture();

        assertTrue(service.refresh());

        assertEquals(5, service.searchEmployees("smith").getTotal());
        assertEquals(2, service.searchEmployees("smith").getCount(), "configured maximum");
        assertEquals("Grace", service.getEmployee("ada.lovelace@yukon.ca", "", "").getManagerFirstName());
        assertEquals(3, service.getDepartments().getCount());
    }

    @Test
    void theMaximumComesFromAnEnvironmentVariableAndIsCapped(AemContext context) throws Exception {
        apiReturnsTheFixture();
        // Cloud Manager variables arrive as strings.
        DirectoryServiceImpl fromEnv = context.registerInjectActivateService(new DirectoryServiceImpl(),
                "search.max.results", "3");
        fromEnv.refresh();
        assertEquals(3, fromEnv.searchEmployees("smith").getCount());

        DirectoryServiceImpl tooMany = context.registerInjectActivateService(new DirectoryServiceImpl(),
                "search.max.results", 500);
        assertEquals(DirectoryServiceImpl.MAX_RESULTS_CAP, tooMany.maxResults());

        DirectoryServiceImpl zero = context.registerInjectActivateService(new DirectoryServiceImpl(),
                "search.max.results", 0);
        assertEquals(1, zero.maxResults());
    }

    @Test
    void theRefreshIntervalComesFromAnEnvironmentVariableAndIsBounded(AemContext context) {
        assertEquals(60, service.refreshMinutes(), "default");
        assertEquals(15, context.registerInjectActivateService(new DirectoryServiceImpl(),
                "refresh.interval.minutes", "15").refreshMinutes());
        assertEquals(DirectoryServiceImpl.MIN_REFRESH_MINUTES, context.registerInjectActivateService(
                new DirectoryServiceImpl(), "refresh.interval.minutes", 1).refreshMinutes());
        assertEquals(DirectoryServiceImpl.MAX_REFRESH_MINUTES, context.registerInjectActivateService(
                new DirectoryServiceImpl(), "refresh.interval.minutes", 100000).refreshMinutes());
    }

    @Test
    void keepsThePreviousCopyWhenARefreshFails() throws Exception {
        apiReturnsTheFixture();
        service.refresh();
        when(apiClient.get(DirectoryApiClient.EMPLOYEES)).thenReturn(new DirectoryApiResponse(500, "oops", 10));

        assertFalse(service.refresh());

        assertEquals(5, service.searchEmployees("smith").getTotal());
    }

    @Test
    void isUnavailableUntilTheFirstDownloadSucceeds() throws Exception {
        when(apiClient.get(anyString())).thenThrow(new SocketTimeoutException("connect timed out"));

        assertFalse(service.refresh());

        assertThrows(DirectoryUnavailableException.class, () -> service.searchEmployees("smith"));
        assertThrows(DirectoryUnavailableException.class, () -> service.getDepartments());
    }

    @Test
    void retriesTheDownloadOnRequestAtMostOncePerMinute() throws Exception {
        when(apiClient.get(anyString())).thenThrow(new SocketTimeoutException("connect timed out"));
        service.refresh();

        assertThrows(DirectoryUnavailableException.class, () -> service.searchEmployees("smith"));
        assertThrows(DirectoryUnavailableException.class, () -> service.searchEmployees("smith"));

        // Only the explicit refresh above called the API: the requests came within a minute of it.
        verify(apiClient, times(1)).get(DirectoryApiClient.DIVISIONS);
    }

    @Test
    void withoutACopyRequestsFailAtOnceAndTheDownloadRunsInTheBackground() throws Exception {
        when(apiClient.get(anyString())).thenThrow(new SocketTimeoutException("connect timed out"));
        service.refresh();
        // The first download was more than a minute ago, and the API is back.
        service.lastAttempt = 0;
        reset(apiClient);
        when(apiClient.isConfigured()).thenReturn(true);
        apiReturnsTheFixture();
        when(scheduler.schedule(any(Runnable.class), any(ScheduleOptions.class))).thenReturn(true);
        clearInvocations(scheduler); // forget the refresh job scheduled on activation

        assertThrows(DirectoryUnavailableException.class, () -> service.searchEmployees("smith"));
        // A second request doesn't queue another download.
        assertThrows(DirectoryUnavailableException.class, () -> service.getDepartments());

        // The requests didn't download anything themselves.
        verify(apiClient, never()).get(anyString());
        ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(1)).schedule(job.capture(), any(ScheduleOptions.class));

        job.getValue().run();

        assertEquals(5, service.searchEmployees("smith").getTotal());
    }

    @Test
    void doesNotCallTheApiWithoutAKey() throws Exception {
        when(apiClient.isConfigured()).thenReturn(false);

        assertFalse(service.refresh());

        verify(apiClient, never()).get(any());
    }

    @Test
    void isRegisteredAsTheDirectoryService(AemContext context) {
        assertTrue(context.getService(DirectoryService.class) instanceof DirectoryServiceImpl);
    }

    private void apiReturnsTheFixture() throws IOException {
        when(apiClient.get(DirectoryApiClient.EMPLOYEES)).thenReturn(
                new DirectoryApiResponse(200, DirectorySnapshotTest.resource("employees.json"), 10));
        when(apiClient.get(DirectoryApiClient.DIVISIONS)).thenReturn(
                new DirectoryApiResponse(200, DirectorySnapshotTest.resource("divisions.json"), 10));
    }
}
