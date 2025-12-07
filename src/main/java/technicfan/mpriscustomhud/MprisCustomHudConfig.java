package technicfan.mpriscustomhud;

public class MprisCustomHudConfig {
    private String filter;
    private String preferred;

    public MprisCustomHudConfig() {
        filter = "";
        preferred = "";
    }

    public String getFilter() {
        return filter;
    }

    public String getPreferred() {
        return preferred;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public void setPreferred(String preferred) {
        this.preferred = preferred;
    }
}
