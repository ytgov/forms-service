package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.config.DirectoryServiceConfig;
import ca.yukon.aem.core.directory.DirectoryApiClient;
import ca.yukon.aem.core.directory.DirectoryApiResponse;
import ca.yukon.aem.core.directory.DirectoryService;
import ca.yukon.aem.core.directory.DirectoryUnavailableException;
import ca.yukon.aem.core.directory.EmployeeDetail;
import ca.yukon.aem.core.directory.EmployeeSearchResult;
import ca.yukon.aem.core.directory.NameList;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps an in-memory copy of the directory on each AEM instance. It loads when the service starts
 * and then on the configured interval. A failed refresh keeps the previous copy; nothing is written
 * to the repository.
 *
 * <p>Logs never contain directory data or the search keyword.</p>
 */
@Component(service = DirectoryService.class, immediate = true)
@Designate(ocd = DirectoryServiceConfig.class)
public class DirectoryServiceImpl implements DirectoryService {

    static final String JOB_NAME = "yukon-forms-directory-refresh";

    /** When the directory is empty, requests retry the download at most this often. */
    static final long RETRY_WHEN_EMPTY_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryServiceImpl.class);

    @Reference
    private DirectoryApiClient apiClient;

    @Reference
    private Scheduler scheduler;

    private volatile DirectorySnapshot snapshot;
    private volatile int minLength;
    /** The type-ahead (typeahead.js, limit 100) shows at most this many results. */
    static final int MAX_RESULTS_CAP = 100;

    private volatile int maxResults;
    /** Each download is several MB per instance, so not more often than every 5 minutes; at least daily. */
    static final int MIN_REFRESH_MINUTES = 5;
    static final int MAX_REFRESH_MINUTES = 1440;
    private volatile int refreshMinutes;
    /** When the last download started; package-private for tests. */
    volatile long lastAttempt;
    /** True while a download started by a request is queued or running. */
    private final AtomicBoolean retrying = new AtomicBoolean();

    int maxResults() {
        return maxResults;
    }

    int refreshMinutes() {
        return refreshMinutes;
    }

    @Activate
    @Modified
    protected void activate(DirectoryServiceConfig config) {
        minLength = Math.max(1, config.search_min_length());
        maxResults = Math.min(MAX_RESULTS_CAP, Math.max(1, config.search_max_results()));
        refreshMinutes = Math.min(MAX_REFRESH_MINUTES, Math.max(MIN_REFRESH_MINUTES, config.refresh_interval_minutes()));
        long periodSeconds = TimeUnit.MINUTES.toSeconds(refreshMinutes);
        scheduler.unschedule(JOB_NAME);
        // Each instance keeps its own copy, so the job runs on every instance, not only the leader.
        ScheduleOptions options = scheduler.NOW(-1, periodSeconds)
                .name(JOB_NAME)
                .canRunConcurrently(false);
        scheduler.schedule((Runnable) this::refresh, options);
    }

    @Deactivate
    protected void deactivate() {
        scheduler.unschedule(JOB_NAME);
    }

    @Override
    public EmployeeSearchResult searchEmployees(String keyword) throws DirectoryUnavailableException {
        return current().search(keyword, minLength, maxResults);
    }

    @Override
    public EmployeeDetail getEmployee(String email, String title, String department)
            throws DirectoryUnavailableException {
        return current().employee(email, title, department);
    }

    @Override
    public NameList getDepartments() throws DirectoryUnavailableException {
        return current().departments();
    }

    @Override
    public NameList getDivisions(String department) throws DirectoryUnavailableException {
        return current().divisions(department);
    }

    @Override
    public NameList getBranches(String department, String division) throws DirectoryUnavailableException {
        return current().branches(department, division);
    }

    @Override
    public NameList getUnits(String department, String division, String branch)
            throws DirectoryUnavailableException {
        return current().units(department, division, branch);
    }

    /**
     * Downloads the directory and replaces the cached copy. Keeps the old copy on any failure.
     *
     * @return true when the copy was replaced
     */
    synchronized boolean refresh() {
        lastAttempt = System.currentTimeMillis();
        if (!apiClient.isConfigured()) {
            LOG.warn("Directory refresh skipped: the directory API key is not configured");
            return false;
        }
        try {
            String divisions = download(DirectoryApiClient.DIVISIONS);
            String employees = download(DirectoryApiClient.EMPLOYEES);
            DirectorySnapshot loaded = DirectorySnapshot.parse(employees, divisions, System.currentTimeMillis());
            snapshot = loaded;
            LOG.info("Directory refreshed: {} employee positions, {} organization units",
                    loaded.employeeCount(), loaded.orgUnitCount());
            return true;
        } catch (IOException | RuntimeException e) {
            // The exception message never holds directory data; the response body is not logged.
            LOG.warn("Directory refresh failed, keeping the previous copy: {}", e.toString());
            return false;
        }
    }

    private String download(String endpoint) throws IOException {
        DirectoryApiResponse response = apiClient.get(endpoint);
        if (!response.isSuccess()) {
            throw new IOException("GET " + endpoint + " returned HTTP " + response.getStatusCode());
        }
        return response.getBody();
    }

    /**
     * Returns the cached copy. Without one, the request fails at once (HTTP 503, so the form's error
     * handler runs within the board's 10 s) and, at most once a minute, a download starts in the
     * background for the next requests. Requests never wait for a download.
     */
    private DirectorySnapshot current() throws DirectoryUnavailableException {
        DirectorySnapshot current = snapshot;
        if (current != null) {
            return current;
        }
        retryInBackground();
        throw new DirectoryUnavailableException("The directory has not been loaded");
    }

    private void retryInBackground() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt < RETRY_WHEN_EMPTY_MILLIS || !retrying.compareAndSet(false, true)) {
            return;
        }
        lastAttempt = now;
        boolean scheduled = false;
        try {
            ScheduleOptions options = scheduler.NOW()
                    .name(JOB_NAME + "-retry")
                    .canRunConcurrently(false);
            scheduled = scheduler.schedule((Runnable) () -> {
                try {
                    refresh();
                } finally {
                    retrying.set(false);
                }
            }, options);
        } finally {
            if (!scheduled) {
                retrying.set(false);
            }
        }
    }
}
