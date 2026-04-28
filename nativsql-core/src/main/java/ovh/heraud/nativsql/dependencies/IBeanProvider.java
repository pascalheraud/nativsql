package ovh.heraud.nativsql.dependencies;

public interface IBeanProvider {

    <T> T getBean(Class<T> type);
}
