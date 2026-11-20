package ca.yukon.aem.core.directory;

import java.io.IOException;

/**
 * Calls the Government of Yukon directory API (https://api.gov.yk.ca/directory).
 *
 * <p>The API sits behind Azure API Management and needs a subscription key in the
 * {@code Ocp-Apim-Subscription-Key} header. It has no paging or search: each endpoint returns its
 * whole list ({@code employees} is about 4.4 MB).</p>
 */
public interface DirectoryApiClient {

    /** Known endpoints, relative to the base URL. */
    String DEPARTMENTS = "departments";
    String DIVISIONS = "divisions";
    String EMPLOYEES = "employees";

    /** @return true when both the base URL and the API key are set */
    boolean isConfigured();

    /** @return the configured base URL, for diagnostics */
    String getBaseUrl();

    /**
     * Sends a GET request to {@code <baseUrl>/<endpoint>}.
     *
     * @param endpoint path relative to the base URL, for example {@link #DEPARTMENTS}
     * @return the response, whatever its status code
     * @throws IOException          when the request cannot be sent or the response cannot be read
     * @throws IllegalStateException when the client is not configured
     */
    DirectoryApiResponse get(String endpoint) throws IOException;
}
