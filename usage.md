# NativSQL Usage Examples

## Basic CRUD Operations

### Insert

```java
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public void createUser() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        user.setStatus(UserStatus.ACTIVE);
        
        // Address as JSON
        Address address = new Address();
        address.setStreet("123 Main Street");
        address.setCity("Paris");
        address.setPostalCode("75001");
        address.setCountry("France");
        user.setAddress(address);
        
        // Preferences as JSON
        Preferences prefs = new Preferences();
        prefs.setLanguage("fr");
        prefs.setTheme("dark");
        prefs.setNotifications(true);
        user.setPreferences(prefs);
        
        // Insert all non-null fields
        userRepository.insert(user);
        
        // Or insert specific fields only
        userRepository.insert(user, "firstName", "lastName", "email");
    }
}
```

### Update

```java
public void updateUser(Long userId) {
    User user = userRepository.findById(userId);
    
    if (user != null) {
        user.setStatus(UserStatus.SUSPENDED);
        
        // Update specific fields
        userRepository.update(user, "id", "status");
        
        // Or update all non-null fields
        userRepository.update(user, "id");
    }
}
```

### Delete

```java
public void deleteUser(Long userId) {
    userRepository.delete("id", userId);
}
```

## Custom Queries

### Simple Query

```java
@Repository
public class UserRepository extends GenericRepository<User> {
    
    public List<User> findActiveUsers() {
        String sql = """
            SELECT id, first_name, last_name, email, status, address, preferences
            FROM users 
            WHERE status = :status
            ORDER BY created_at DESC
            """;
        
        return jdbcTemplate.query(sql,
            Map.of("status", "ACTIVE"),
            rowMapperFactory.getRowMapper(User.class));
    }
}
```

### Query with JSON Operators

```java
public List<User> findUsersByCity(String city) {
    String sql = """
        SELECT id, first_name, last_name, email, status, address, preferences
        FROM users 
        WHERE address->>'city' = :city
        """;
    
    return jdbcTemplate.query(sql,
        Map.of("city", city),
        rowMapperFactory.getRowMapper(User.class));
}

public List<User> findUsersWithDarkTheme() {
    String sql = """
        SELECT id, first_name, last_name, email, status, address, preferences
        FROM users 
        WHERE preferences->>'theme' = 'dark'
        """;
    
    return jdbcTemplate.query(sql, rowMapperFactory.getRowMapper(User.class));
}
```

### Pagination

```java
public List<User> findUsers(int page, int size) {
    String sql = """
        SELECT id, first_name, last_name, email, status, address, preferences
        FROM users 
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """;
    
    return jdbcTemplate.query(sql,
        Map.of("limit", size, "offset", page * size),
        rowMapperFactory.getRowMapper(User.class));
}
```

## Nested Object Mapping

### Define Domain Classes

```java
public class UserWithAddress {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private AddressEntity address;  // Nested object
    
    // Getters and setters
}

public class AddressEntity {
    private Long id;
    private Long userId;
    private String street;
    private String city;
    private String postalCode;
    private String country;
    
    // Getters and setters
}
```

### Query with JOIN

```java
public List<UserWithAddress> findUsersWithAddresses() {
    String sql = """
        SELECT 
            u.id,
            u.first_name,
            u.last_name,
            u.email,
            a.id AS "address.id",
            a.user_id AS "address.userId",
            a.street AS "address.street",
            a.city AS "address.city",
            a.postal_code AS "address.postalCode",
            a.country AS "address.country"
        FROM users u
        LEFT JOIN addresses a ON u.id = a.user_id
        ORDER BY u.id
        """;
    
    return jdbcTemplate.query(sql,
        rowMapperFactory.getRowMapper(UserWithAddress.class));
}
```

**Key Points:**
- Use dot notation in column aliases: `AS "address.street"`
- The mapper automatically creates nested objects
- Quotes are required for case-sensitive column names with dots

## Custom Type Mappers

### Value Objects

```java
// Define value object
public class Email {
    private final String value;
    
    public Email(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    private static boolean isValid(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}

// Register in configuration
@Configuration
public class NativSqlConfig {
    
    @Bean
    public TypeMapperFactory typeMapperFactory(ObjectMapper objectMapper) {
        TypeMapperFactory factory = new TypeMapperFactory(objectMapper);
        
        // Register value object mapper: String -> Email
        factory.registerCompositeMapper(Email.class, String.class, Email::new);
        
        return factory;
    }
}

// Use in entity
public class User {
    private Long id;
    private Email email;  // Stored as VARCHAR, mapped to Email
    
    // Getters and setters
}
```

### Geographic Types (PostGIS)

```java
import org.postgis.Point;
import org.postgis.PGgeometry;

@Configuration
public class NativSqlConfig {
    
    @Bean
    public TypeMapperFactory typeMapperFactory(ObjectMapper objectMapper) {
        TypeMapperFactory factory = new TypeMapperFactory(objectMapper);
        
        // Register PostGIS Point mapper
        factory.register(Point.class, (rs, col) -> {
            try {
                Object obj = rs.getObject(col);
                if (obj == null) return null;
                
                if (obj instanceof PGgeometry) {
                    return (Point) ((PGgeometry) obj).getGeometry();
                }
                
                return null;
            } catch (SQLException e) {
                return null;
            }
        });
        
        return factory;
    }
}

// Use in entity
public class User {
    private Long id;
    private Point location;  // PostGIS GEOMETRY(Point)
    
    // Getters and setters
}

// Query
String sql = """
    SELECT id, first_name, location
    FROM users
    WHERE ST_DWithin(location, ST_GeomFromText(:point, 4326), :distance)
    """;
```

## Advanced Scenarios

### Batch Insert

```java
public void batchInsertUsers(List<User> users) {
    for (User user : users) {
        userRepository.insert(user);
    }
}

// Or use Spring JDBC batch operations
public void batchInsertUsersOptimized(List<User> users) {
    String sql = """
        INSERT INTO users (first_name, last_name, email, status)
        VALUES (:firstName, :lastName, :email, :status)
        """;
    
    List<Map<String, Object>> batchValues = users.stream()
        .map(user -> Map.of(
            "firstName", user.getFirstName(),
            "lastName", user.getLastName(),
            "email", user.getEmail(),
            "status", user.getStatus().name()
        ))
        .toList();
    
    jdbcTemplate.batchUpdate(sql, batchValues.toArray(new Map[0]));
}
```

### Returning Generated ID

```java
public Long insertAndReturnId(User user) {
    String sql = """
        INSERT INTO users (first_name, last_name, email, status, address)
        VALUES (:firstName, :lastName, :email, :status, :address)
        RETURNING id
        """;
    
    Map<String, Object> params = Map.of(
        "firstName", user.getFirstName(),
        "lastName", user.getLastName(),
        "email", user.getEmail(),
        "status", user.getStatus().name(),
        "address", typeMapperFactory.toJsonb(user.getAddress())
    );
    
    return jdbcTemplate.queryForObject(sql, params, Long.class);
}
```

### Complex Aggregations

```java
public class UserStats {
    private UserStatus status;
    private Long count;
    private String mostCommonCity;
    
    // Getters and setters
}

public List<UserStats> getUserStatsByStatus() {
    String sql = """
        SELECT 
            status,
            COUNT(*) as count,
            MODE() WITHIN GROUP (ORDER BY address->>'city') as most_common_city
        FROM users
        GROUP BY status
        ORDER BY count DESC
        """;
    
    return jdbcTemplate.query(sql, rowMapperFactory.getRowMapper(UserStats.class));
}
```

### Transactions

```java
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public void transferAccount(Long fromUserId, Long toUserId) {
        User fromUser = userRepository.findById(fromUserId);
        User toUser = userRepository.findById(toUserId);
        
        // Update both users
        fromUser.setStatus(UserStatus.INACTIVE);
        toUser.setStatus(UserStatus.ACTIVE);
        
        userRepository.update(fromUser, "id", "status");
        userRepository.update(toUser, "id", "status");
        
        // If any operation fails, transaction rolls back
    }
}
```

## Testing

### Integration Test Example

```java
@JdbcTest
@Testcontainers
@Import({NativSqlConfig.class, RowMapperFactory.class, UserRepository.class})
class UserRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:15-3.3")
        .withInitScript("test-schema.sql");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testInsertAndFind() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        
        userRepository.insert(user);
        
        User found = userRepository.findByEmail("test@example.com");
        
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Test");
    }
}
```

## Performance Tips

1. **Use specific column lists** instead of `SELECT *`
2. **Cache RowMappers** - they're automatically cached per class
3. **Use PreparedStatements** - NamedParameterJdbcTemplate handles this
4. **Index JSON fields** for frequent queries:
   ```sql
   CREATE INDEX idx_users_city ON users ((address->>'city'));
   ```
5. **Batch operations** for bulk inserts/updates
6. **Connection pooling** - HikariCP is configured by default in Spring Boot