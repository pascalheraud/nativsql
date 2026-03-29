package ovh.heraud.nativsql.domain.data;

public interface IData<T> {
    T getData();
    void setData(T data);
}
