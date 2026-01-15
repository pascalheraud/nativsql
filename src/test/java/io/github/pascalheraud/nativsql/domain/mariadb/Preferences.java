package io.github.pascalheraud.nativsql.domain.mariadb;

/**
 * User preferences, stored as JSONB in PostgreSQL.
 */
public class Preferences {
    private String language;
    private String theme;
    private boolean notifications;
    
    public Preferences() {
    }
    
    public Preferences(String language, String theme, boolean notifications) {
        this.language = language;
        this.theme = theme;
        this.notifications = notifications;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getTheme() {
        return theme;
    }
    
    public void setTheme(String theme) {
        this.theme = theme;
    }
    
    public boolean isNotifications() {
        return notifications;
    }
    
    public void setNotifications(boolean notifications) {
        this.notifications = notifications;
    }
    
    @Override
    public String toString() {
        return "Preferences{" +
                "language='" + language + '\'' +
                ", theme='" + theme + '\'' +
                ", notifications=" + notifications +
                '}';
    }
}