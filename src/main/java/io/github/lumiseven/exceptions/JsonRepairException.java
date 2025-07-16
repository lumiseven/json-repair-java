package io.github.lumiseven.exceptions;

public class JsonRepairException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private int position;

    public int getPosition() {
        return position;
    }

    public JsonRepairException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

}
