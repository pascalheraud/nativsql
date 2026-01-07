package io.github.pascalheraud.nativsql.mapper;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * A ResultSet wrapper that prefixes all column names with a given prefix.
 * This allows nested mappers to work with their own "namespace" of columns.
 * 
 * Example: PrefixedResultSet with prefix "address." will translate
 * getObject("street") to delegate.getObject("address.street")
 */
public class PrefixedResultSet implements ResultSet {
    
    private final ResultSet delegate;
    private final String prefix;
    
    public PrefixedResultSet(ResultSet delegate, String prefix) {
        this.delegate = delegate;
        this.prefix = prefix;
    }
    
    private String prefixedColumn(String columnLabel) {
        return prefix + columnLabel;
    }
    
    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return delegate.getObject(prefixedColumn(columnLabel));
    }
    
    @Override
    public String getString(String columnLabel) throws SQLException {
        return delegate.getString(prefixedColumn(columnLabel));
    }
    
    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return delegate.getBoolean(prefixedColumn(columnLabel));
    }
    
    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return delegate.getByte(prefixedColumn(columnLabel));
    }
    
    @Override
    public short getShort(String columnLabel) throws SQLException {
        return delegate.getShort(prefixedColumn(columnLabel));
    }
    
    @Override
    public int getInt(String columnLabel) throws SQLException {
        return delegate.getInt(prefixedColumn(columnLabel));
    }
    
    @Override
    public long getLong(String columnLabel) throws SQLException {
        return delegate.getLong(prefixedColumn(columnLabel));
    }
    
    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return delegate.getFloat(prefixedColumn(columnLabel));
    }
    
    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return delegate.getDouble(prefixedColumn(columnLabel));
    }
    
    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return delegate.getBigDecimal(prefixedColumn(columnLabel));
    }
    
    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return delegate.getBytes(prefixedColumn(columnLabel));
    }
    
    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return delegate.getDate(prefixedColumn(columnLabel));
    }
    
    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return delegate.getTime(prefixedColumn(columnLabel));
    }
    
    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return delegate.getTimestamp(prefixedColumn(columnLabel));
    }
    
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        return delegate.findColumn(prefixedColumn(columnLabel));
    }
    
    // Delegate all other methods to the underlying ResultSet
    
    @Override
    public boolean next() throws SQLException {
        return delegate.next();
    }
    
    @Override
    public void close() throws SQLException {
        delegate.close();
    }
    
    @Override
    public boolean wasNull() throws SQLException {
        return delegate.wasNull();
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return delegate.getMetaData();
    }
    
    @Override
    public Object getObject(int columnIndex) throws SQLException {
        return delegate.getObject(columnIndex);
    }
    
    @Override
    public String getString(int columnIndex) throws SQLException {
        return delegate.getString(columnIndex);
    }
    
    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        return delegate.getBoolean(columnIndex);
    }
    
    @Override
    public byte getByte(int columnIndex) throws SQLException {
        return delegate.getByte(columnIndex);
    }
    
    @Override
    public short getShort(int columnIndex) throws SQLException {
        return delegate.getShort(columnIndex);
    }
    
    @Override
    public int getInt(int columnIndex) throws SQLException {
        return delegate.getInt(columnIndex);
    }
    
    @Override
    public long getLong(int columnIndex) throws SQLException {
        return delegate.getLong(columnIndex);
    }
    
    @Override
    public float getFloat(int columnIndex) throws SQLException {
        return delegate.getFloat(columnIndex);
    }
    
    @Override
    public double getDouble(int columnIndex) throws SQLException {
        return delegate.getDouble(columnIndex);
    }
    
    // Note: Deprecated methods omitted for brevity
    // Additional methods like getArray, getBlob, etc. should be implemented similarly
    
    // Stub implementations for remaining required methods
    @Override public Statement getStatement() throws SQLException { return delegate.getStatement(); }
    @Override public boolean isBeforeFirst() throws SQLException { return delegate.isBeforeFirst(); }
    @Override public boolean isAfterLast() throws SQLException { return delegate.isAfterLast(); }
    @Override public boolean isFirst() throws SQLException { return delegate.isFirst(); }
    @Override public boolean isLast() throws SQLException { return delegate.isLast(); }
    @Override public void beforeFirst() throws SQLException { delegate.beforeFirst(); }
    @Override public void afterLast() throws SQLException { delegate.afterLast(); }
    @Override public boolean first() throws SQLException { return delegate.first(); }
    @Override public boolean last() throws SQLException { return delegate.last(); }
    @Override public int getRow() throws SQLException { return delegate.getRow(); }
    @Override public boolean absolute(int row) throws SQLException { return delegate.absolute(row); }
    @Override public boolean relative(int rows) throws SQLException { return delegate.relative(rows); }
    @Override public boolean previous() throws SQLException { return delegate.previous(); }
    @Override public void setFetchDirection(int direction) throws SQLException { delegate.setFetchDirection(direction); }
    @Override public int getFetchDirection() throws SQLException { return delegate.getFetchDirection(); }
    @Override public void setFetchSize(int rows) throws SQLException { delegate.setFetchSize(rows); }
    @Override public int getFetchSize() throws SQLException { return delegate.getFetchSize(); }
    @Override public int getType() throws SQLException { return delegate.getType(); }
    @Override public int getConcurrency() throws SQLException { return delegate.getConcurrency(); }
    @Override public SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
    @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
    @Override public String getCursorName() throws SQLException { return delegate.getCursorName(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    
    // Additional stubs - in production, all methods should properly delegate
    @SuppressWarnings("deprecation")
    @Override public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException { return delegate.getBigDecimal(prefixedColumn(columnLabel), scale); }
    @SuppressWarnings("deprecation")
    @Override public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException { return delegate.getBigDecimal(columnIndex, scale); }
    @Override public BigDecimal getBigDecimal(int columnIndex) throws SQLException { return delegate.getBigDecimal(columnIndex); }
    @Override public byte[] getBytes(int columnIndex) throws SQLException { return delegate.getBytes(columnIndex); }
    @Override public Date getDate(int columnIndex) throws SQLException { return delegate.getDate(columnIndex); }
    @Override public Time getTime(int columnIndex) throws SQLException { return delegate.getTime(columnIndex); }
    @Override public Timestamp getTimestamp(int columnIndex) throws SQLException { return delegate.getTimestamp(columnIndex); }
    @Override public InputStream getAsciiStream(String columnLabel) throws SQLException { return delegate.getAsciiStream(prefixedColumn(columnLabel)); }
    @SuppressWarnings("deprecation")
    @Override public InputStream getUnicodeStream(String columnLabel) throws SQLException { return delegate.getUnicodeStream(prefixedColumn(columnLabel)); }
    @Override public InputStream getBinaryStream(String columnLabel) throws SQLException { return delegate.getBinaryStream(prefixedColumn(columnLabel)); }
    @Override public InputStream getAsciiStream(int columnIndex) throws SQLException { return delegate.getAsciiStream(columnIndex); }
    @SuppressWarnings("deprecation")
    @Override public InputStream getUnicodeStream(int columnIndex) throws SQLException { return delegate.getUnicodeStream(columnIndex); }
    @Override public InputStream getBinaryStream(int columnIndex) throws SQLException { return delegate.getBinaryStream(columnIndex); }
    @Override public Reader getCharacterStream(String columnLabel) throws SQLException { return delegate.getCharacterStream(prefixedColumn(columnLabel)); }
    @Override public Reader getCharacterStream(int columnIndex) throws SQLException { return delegate.getCharacterStream(columnIndex); }
    @Override public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException { return delegate.getObject(prefixedColumn(columnLabel), map); }
    @Override public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException { return delegate.getObject(columnIndex, map); }
    @Override public Ref getRef(String columnLabel) throws SQLException { return delegate.getRef(prefixedColumn(columnLabel)); }
    @Override public Ref getRef(int columnIndex) throws SQLException { return delegate.getRef(columnIndex); }
    @Override public Blob getBlob(String columnLabel) throws SQLException { return delegate.getBlob(prefixedColumn(columnLabel)); }
    @Override public Blob getBlob(int columnIndex) throws SQLException { return delegate.getBlob(columnIndex); }
    @Override public Clob getClob(String columnLabel) throws SQLException { return delegate.getClob(prefixedColumn(columnLabel)); }
    @Override public Clob getClob(int columnIndex) throws SQLException { return delegate.getClob(columnIndex); }
    @Override public Array getArray(String columnLabel) throws SQLException { return delegate.getArray(prefixedColumn(columnLabel)); }
    @Override public Array getArray(int columnIndex) throws SQLException { return delegate.getArray(columnIndex); }
    @Override public Date getDate(String columnLabel, Calendar cal) throws SQLException { return delegate.getDate(prefixedColumn(columnLabel), cal); }
    @Override public Date getDate(int columnIndex, Calendar cal) throws SQLException { return delegate.getDate(columnIndex, cal); }
    @Override public Time getTime(String columnLabel, Calendar cal) throws SQLException { return delegate.getTime(prefixedColumn(columnLabel), cal); }
    @Override public Time getTime(int columnIndex, Calendar cal) throws SQLException { return delegate.getTime(columnIndex, cal); }
    @Override public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException { return delegate.getTimestamp(prefixedColumn(columnLabel), cal); }
    @Override public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException { return delegate.getTimestamp(columnIndex, cal); }
    @Override public URL getURL(String columnLabel) throws SQLException { return delegate.getURL(prefixedColumn(columnLabel)); }
    @Override public URL getURL(int columnIndex) throws SQLException { return delegate.getURL(columnIndex); }
    @Override public Reader getNCharacterStream(String columnLabel) throws SQLException { return delegate.getNCharacterStream(prefixedColumn(columnLabel)); }
    @Override public Reader getNCharacterStream(int columnIndex) throws SQLException { return delegate.getNCharacterStream(columnIndex); }
    @Override public NClob getNClob(String columnLabel) throws SQLException { return delegate.getNClob(prefixedColumn(columnLabel)); }
    @Override public NClob getNClob(int columnIndex) throws SQLException { return delegate.getNClob(columnIndex); }
    @Override public SQLXML getSQLXML(String columnLabel) throws SQLException { return delegate.getSQLXML(prefixedColumn(columnLabel)); }
    @Override public SQLXML getSQLXML(int columnIndex) throws SQLException { return delegate.getSQLXML(columnIndex); }
    @Override public String getNString(String columnLabel) throws SQLException { return delegate.getNString(prefixedColumn(columnLabel)); }
    @Override public String getNString(int columnIndex) throws SQLException { return delegate.getNString(columnIndex); }
    @Override public <T> T getObject(String columnLabel, Class<T> type) throws SQLException { return delegate.getObject(prefixedColumn(columnLabel), type); }
    @Override public <T> T getObject(int columnIndex, Class<T> type) throws SQLException { return delegate.getObject(columnIndex, type); }
    
    // Update methods - all throw UnsupportedOperationException as this is read-only
    @Override public boolean rowUpdated() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public boolean rowInserted() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public boolean rowDeleted() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNull(int columnIndex) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBoolean(int columnIndex, boolean x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateByte(int columnIndex, byte x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateShort(int columnIndex, short x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateInt(int columnIndex, int x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateLong(int columnIndex, long x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateFloat(int columnIndex, float x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateDouble(int columnIndex, double x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateString(int columnIndex, String x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBytes(int columnIndex, byte[] x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateDate(int columnIndex, Date x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateTime(int columnIndex, Time x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateObject(int columnIndex, Object x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNull(String columnLabel) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBoolean(String columnLabel, boolean x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateByte(String columnLabel, byte x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateShort(String columnLabel, short x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateInt(String columnLabel, int x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateLong(String columnLabel, long x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateFloat(String columnLabel, float x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateDouble(String columnLabel, double x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateString(String columnLabel, String x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBytes(String columnLabel, byte[] x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateDate(String columnLabel, Date x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateTime(String columnLabel, Time x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateObject(String columnLabel, Object x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void insertRow() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateRow() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void deleteRow() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void refreshRow() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void cancelRowUpdates() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void moveToInsertRow() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void moveToCurrentRow() throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateRef(int columnIndex, Ref x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateRef(String columnLabel, Ref x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(int columnIndex, Blob x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(String columnLabel, Blob x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateClob(int columnIndex, Clob x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateClob(String columnLabel, Clob x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateArray(int columnIndex, Array x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateArray(String columnLabel, Array x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public RowId getRowId(int columnIndex) throws SQLException { return delegate.getRowId(columnIndex); }
    @Override public RowId getRowId(String columnLabel) throws SQLException { return delegate.getRowId(prefixedColumn(columnLabel)); }
    @Override public void updateRowId(int columnIndex, RowId x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateRowId(String columnLabel, RowId x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public int getHoldability() throws SQLException { return delegate.getHoldability(); }
    @Override public boolean isClosed() throws SQLException { return delegate.isClosed(); }
    @Override public void updateNString(int columnIndex, String nString) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNString(String columnLabel, String nString) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(int columnIndex, NClob nClob) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(String columnLabel, NClob nClob) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateClob(int columnIndex, Reader reader, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateClob(String columnLabel, Reader reader, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateClob(int columnIndex, Reader reader) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateClob(String columnLabel, Reader reader) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(int columnIndex, Reader reader) throws SQLException { throw new UnsupportedOperationException(); }
    @Override public void updateNClob(String columnLabel, Reader reader) throws SQLException { throw new UnsupportedOperationException(); }
}