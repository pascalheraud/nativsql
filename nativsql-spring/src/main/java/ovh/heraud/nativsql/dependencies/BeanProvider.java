package ovh.heraud.nativsql.dependencies;

@Named
public class BeanProvider implements IBeanProvider {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public <T> T getBean(Class<T> type) {
        // Implementation for getting a bean of the specified type
        return applicationContext.getBean(type);
    }
}
