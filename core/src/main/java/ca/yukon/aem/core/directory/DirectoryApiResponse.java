package ca.yukon.aem.core.directory;

/**
 * A raw response from the directory API.
 */
public final class DirectoryApiResponse {

    private final int statusCode;
    private final String body;
    private final long elapsedMillis;

    public DirectoryApiResponse(int statusCode, String body, long elapsedMillis) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.elapsedMillis = elapsedMillis;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
