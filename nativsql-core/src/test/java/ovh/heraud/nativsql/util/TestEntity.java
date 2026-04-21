package ovh.heraud.nativsql.util;

/**
 * Test entity for ReflectionUtils tests.
 * Uses TestEntityColumns for type-safe column references.
 */
public class TestEntity {
    public Long id;
    public String email;
    public boolean active;
    public String firstName;
    public String lastName;
    public String status;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getStatus() {
        return status;
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

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
