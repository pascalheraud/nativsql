package ovh.heraud.nativsql.util;

/**
 * Test entity for ReflectionUtils tests.
 * Uses TestEntityColumns for type-safe column references.
 */
public class TestEntity {
    public Long id;
    public String email;
    public boolean active;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
