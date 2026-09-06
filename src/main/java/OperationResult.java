public final class OperationResult {
    public enum Status { SUCCESS, FAILED, PREVIEW, CANCELLED }

    public final Status status;
    public final String message;
    public final int exitCode;

    private OperationResult(Status status, String message, int exitCode) {
        this.status = status;
        this.message = message == null ? "" : message;
        this.exitCode = exitCode;
    }

    public static OperationResult success(String message) {
        return new OperationResult(Status.SUCCESS, message, 0);
    }

    public static OperationResult failed(String message, int exitCode) {
        return new OperationResult(Status.FAILED, message, exitCode == 0 ? 1 : exitCode);
    }

    public static OperationResult preview(String message) {
        return new OperationResult(Status.PREVIEW, message, 0);
    }

    public static OperationResult cancelled(String message) {
        return new OperationResult(Status.CANCELLED, message, 130);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PREVIEW;
    }
}
