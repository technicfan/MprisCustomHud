package technicfan.mpriscustomhud;

public class Config {
    public final String preferred;
    public final boolean onlyPreferred;

    public Config() {
        preferred = "";
        onlyPreferred = false;
    }

    private Config(String preferred, boolean onlyPreferred) {
        this.preferred = preferred;
        this.onlyPreferred = onlyPreferred;
    }

    public Config setPreferred(String preferred) {
        return new Config(preferred, onlyPreferred);
    }

    public Config setOnlyPreferred(boolean onlyPreferred) {
        return new Config(preferred, onlyPreferred);
    }
}
