package relay;

/** A client-facing validation, authentication, or access failure. */
public final class Problem extends RuntimeException {
    public final int status;

    public Problem(int status, String message) {
        super(message);
        this.status = status;
    }
}
