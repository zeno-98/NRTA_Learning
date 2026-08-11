package tools;

public class IdCounter {
    private int currentId = 0;

    public int createId() {
        return currentId++;
    }

    public String createIdAsString() {
        return String.valueOf(createId());
    }

    public int getMaxId() {
        return currentId;
    }

    public void reset() {
        this.currentId = 0;
    }
}
